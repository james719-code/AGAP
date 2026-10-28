package com.travelingsalesman.agap.core.routing

import com.travelingsalesman.agap.core.map.MapPackageManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * AGAP road routing policy:
 *
 * 1. Prefer a backend-prepared SQLite road graph already installed on the device.
 * 2. If internet is allowed, synchronize that graph package once; use Overpass only
 *    as a small route-corridor fallback when the backend package is unavailable.
 * 3. Keep the graph in memory and run A* locally using physical road length as cost.
 * 4. If no routable graph exists, return a clearly marked direct fallback. The UI
 *    must never label that fallback as a shortest road route.
 */
@Singleton
class OfflineRouteEngine @Inject constructor(
    private val store: OfflineRoutingGraphStore,
    private val downloader: OsmRoadGraphDownloader,
    private val mapPackageManager: MapPackageManager,
    private val routingPackageManager: RoutingGraphPackageManager
) {
    private val graphDownloadMutex = Mutex()

    /**
     * Warm the entire active deployment-map area while internet is available.
     * This makes an offline map useful for routing before the first emergency: once
     * this succeeds, responders can run A* inside the cached deployment area even
     * after switching to BLE-only transport.
     */
    suspend fun prefetchActiveMapRoads(allowNetwork: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val bounds = parseActiveMapBounds() ?: return@withContext false
            if (store.hasCoverage(bounds)) {
                // Warm the graph window in RAM before command needs it. This makes
                // the first responder preview avoid even the SQLite join/load cost.
                store.loadGraph(bounds)
                return@withContext true
            }

            val networkAllowed = allowNetwork && !ServerConfigManager.isBleOnlyActive && !com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST
            if (!networkAllowed) return@withContext false

            // Preferred path: download one backend-preprocessed SQLite graph.
            // Do not warm a whole city through Overpass on the phone.
            routingPackageManager.syncIfNeeded(allowNetwork = true)
            if (store.hasCoverage(bounds)) {
                store.loadGraph(bounds)
                true
            } else {
                false
            }
        }

    suspend fun route(
        start: RouteCoordinate,
        end: RouteCoordinate,
        allowNetwork: Boolean
    ): OfflineRouteResult = withContext(Dispatchers.IO) {
        if (!start.isUsable() || !end.isUsable()) {
            return@withContext directFallback(
                start,
                end,
                "Invalid responder or incident coordinates"
            )
        }

        val directDistance = haversineMeters(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude
        )

        if (directDistance < 8.0) {
            return@withContext OfflineRouteResult(
                points = listOf(start, end),
                distanceMeters = directDistance,
                durationSeconds = 0L,
                source = RouteSource.OFFLINE_GRAPH,
                isRoadRoute = true
            )
        }

        // Wider padding is used for longer routes so A* has room to use bridges,
        // arterials and detours instead of being trapped in a narrow rectangle.
        val routeSpanDegrees = directDistance / 111_000.0
        val padding = max(MIN_ROUTE_PADDING_DEGREES, routeSpanDegrees * 0.30)
            .coerceAtMost(MAX_ROUTE_PADDING_DEGREES)
        val requestedBounds = RoutingBounds.around(start, end, padding)

        val networkAllowed = allowNetwork && !ServerConfigManager.isBleOnlyActive && !com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST

        // Coverage is judged by the actual endpoints rather than the padded A*
        // search rectangle. A responder near the edge of a downloaded package
        // should not trigger a network fetch merely because padding extends a
        // little outside the package boundary.
        val endpointBounds = RoutingBounds.around(start, end, ENDPOINT_COVERAGE_PADDING_DEGREES)
        val hadEndpointCoverage = store.hasCoverage(endpointBounds)

        if (!hadEndpointCoverage && networkAllowed) {
            // Fast preferred path: install the backend-built SQLite graph.
            routingPackageManager.syncIfNeeded(allowNetwork = true)
        }

        var hasEndpointCoverage = store.hasCoverage(endpointBounds)
        if (!hasEndpointCoverage && networkAllowed) {
            // Compatibility/emergency fallback only. If the backend package was
            // not prepared yet, fetch just this route corridor from Overpass.
            ensureCoverage(requestedBounds)
            hasEndpointCoverage = store.hasCoverage(endpointBounds)
        }

        val graph = store.loadGraph(requestedBounds)
        val localResult = calculateAStar(
            graph = graph,
            start = start,
            end = end,
            source = when {
                hasEndpointCoverage && hadEndpointCoverage -> RouteSource.OFFLINE_GRAPH
                hasEndpointCoverage -> RouteSource.DOWNLOADED_GRAPH
                else -> RouteSource.OFFLINE_PARTIAL_GRAPH
            }
        )

        if (localResult != null) {
            return@withContext localResult.copy(
                message = when (localResult.source) {
                    RouteSource.DOWNLOADED_GRAPH ->
                        "Road graph cached for offline reuse"
                    RouteSource.OFFLINE_PARTIAL_GRAPH ->
                        "Using available cached roads; full routing coverage is not cached"
                    else -> null
                }
            )
        }

        // First retry with a larger window from data already on the device.
        // This handles bridges/arterials that leave the initial corridor without
        // doing another network request.
        val expanded = requestedBounds.expanded(EXPANDED_RETRY_PADDING_DEGREES)
        val expandedLocalGraph = store.loadGraph(expanded)
        calculateAStar(
            graph = expandedLocalGraph,
            start = start,
            end = end,
            source = if (hasEndpointCoverage) RouteSource.OFFLINE_GRAPH else RouteSource.OFFLINE_PARTIAL_GRAPH
        )?.let { return@withContext it }

        // Only the legacy fallback may fetch more OSM topology. Normally the
        // prebuilt backend package makes this branch unreachable.
        if (networkAllowed && !store.hasCoverage(expanded)) {
            if (ensureCoverage(expanded)) {
                val expandedGraph = store.loadGraph(expanded)
                calculateAStar(
                    graph = expandedGraph,
                    start = start,
                    end = end,
                    source = RouteSource.DOWNLOADED_GRAPH
                )?.let { return@withContext it.copy(message = "Road graph cached for offline reuse") }
            }
        }

        return@withContext directFallback(
            start,
            end,
            if (networkAllowed) {
                "No connected drivable road route was found"
            } else {
                "Offline road data for this area is not cached yet"
            }
        )
    }


    private suspend fun ensureCoverage(bounds: RoutingBounds): Boolean {
        if (store.hasCoverage(bounds)) return true

        return graphDownloadMutex.withLock {
            if (store.hasCoverage(bounds)) return@withLock true

            val downloaded = downloader.download(bounds) ?: return@withLock false
            store.saveGraph(
                nodes = downloaded.nodes,
                edges = downloaded.edges,
                coverage = bounds
            )
            store.hasCoverage(bounds)
        }
    }

    private fun parseActiveMapBounds(): RoutingBounds? {
        val values = mapPackageManager
            .getActiveMapMetadata()
            .boundingBoxStr
            .split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }

        if (values.size != 4) return null
        return runCatching {
            RoutingBounds(
                south = values[0],
                west = values[1],
                north = values[2],
                east = values[3]
            )
        }.getOrNull()
    }

    private fun calculateAStar(
        graph: RoutingGraph,
        start: RouteCoordinate,
        end: RouteCoordinate,
        source: RouteSource
    ): OfflineRouteResult? {
        if (graph.nodes.isEmpty() || graph.adjacency.isEmpty()) return null

        val startNode = graph.spatialIndex.nearest(start, MAX_SNAP_DISTANCE_METERS) ?: return null
        val endNode = graph.spatialIndex.nearest(end, MAX_SNAP_DISTANCE_METERS) ?: return null

        val startSnapDistance = haversineMeters(
            start.latitude,
            start.longitude,
            startNode.latitude,
            startNode.longitude
        )
        val endSnapDistance = haversineMeters(
            end.latitude,
            end.longitude,
            endNode.latitude,
            endNode.longitude
        )

        if (
            startSnapDistance > MAX_SNAP_DISTANCE_METERS ||
            endSnapDistance > MAX_SNAP_DISTANCE_METERS
        ) {
            return null
        }

        data class QueueEntry(
            val nodeId: Long,
            val fScore: Double
        )

        val queue = PriorityQueue<QueueEntry>(compareBy { it.fScore })
        val gScore = HashMap<Long, Double>()
        val cameFrom = HashMap<Long, Long>()
        val cameVia = HashMap<Long, RoutingEdge>()
        val closed = HashSet<Long>()

        gScore[startNode.id] = 0.0
        queue.add(
            QueueEntry(
                startNode.id,
                haversineMeters(
                    startNode.latitude,
                    startNode.longitude,
                    endNode.latitude,
                    endNode.longitude
                )
            )
        )

        var expandedNodes = 0
        var found = false

        while (queue.isNotEmpty() && expandedNodes < MAX_ASTAR_EXPANSIONS) {
            val current = queue.poll()
            if (!closed.add(current.nodeId)) continue
            expandedNodes++

            if (current.nodeId == endNode.id) {
                found = true
                break
            }

            val currentCost = gScore[current.nodeId] ?: continue
            graph.adjacency[current.nodeId].orEmpty().forEach { edge ->
                if (edge.toNode in closed) return@forEach
                val nextNode = graph.nodes[edge.toNode] ?: return@forEach
                val tentative = currentCost + edge.distanceMeters

                if (tentative < (gScore[edge.toNode] ?: Double.POSITIVE_INFINITY)) {
                    cameFrom[edge.toNode] = current.nodeId
                    cameVia[edge.toNode] = edge
                    gScore[edge.toNode] = tentative

                    val heuristic = haversineMeters(
                        nextNode.latitude,
                        nextNode.longitude,
                        endNode.latitude,
                        endNode.longitude
                    )
                    queue.add(QueueEntry(edge.toNode, tentative + heuristic))
                }
            }
        }

        if (!found) return null

        val reversedNodeIds = ArrayList<Long>()
        var cursor = endNode.id
        reversedNodeIds.add(cursor)
        while (cursor != startNode.id) {
            cursor = cameFrom[cursor] ?: return null
            reversedNodeIds.add(cursor)
        }
        reversedNodeIds.reverse()

        val routePoints = ArrayList<RouteCoordinate>(reversedNodeIds.size + 2)
        routePoints.add(start)
        reversedNodeIds.forEach { nodeId ->
            graph.nodes[nodeId]?.let { node ->
                routePoints.add(RouteCoordinate(node.latitude, node.longitude))
            }
        }
        routePoints.add(end)

        var roadDurationSeconds = 0.0
        reversedNodeIds.drop(1).forEach { nodeId ->
            roadDurationSeconds += cameVia[nodeId]?.durationSeconds ?: 0.0
        }

        // Start/end snapping is only a small connector to the nearest mapped road.
        // Use a conservative urban responder speed for that connector when estimating ETA.
        val snapDurationSeconds =
            (startSnapDistance + endSnapDistance) / DEFAULT_SNAP_SPEED_MPS

        return OfflineRouteResult(
            points = routePoints.deduplicateConsecutive(),
            distanceMeters =
                (gScore[endNode.id] ?: return null) + startSnapDistance + endSnapDistance,
            durationSeconds = (roadDurationSeconds + snapDurationSeconds).toLong().coerceAtLeast(1L),
            source = source,
            isRoadRoute = true
        )
    }


    private fun directFallback(
        start: RouteCoordinate,
        end: RouteCoordinate,
        message: String
    ): OfflineRouteResult {
        val distance = haversineMeters(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude
        )
        return OfflineRouteResult(
            points = listOf(start, end),
            distanceMeters = distance,
            durationSeconds = (distance / DIRECT_FALLBACK_SPEED_MPS).toLong().coerceAtLeast(1L),
            source = RouteSource.DIRECT_FALLBACK,
            isRoadRoute = false,
            message = message
        )
    }

    private fun RouteCoordinate.isUsable(): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    private fun List<RouteCoordinate>.deduplicateConsecutive(): List<RouteCoordinate> {
        if (size < 2) return this
        val result = ArrayList<RouteCoordinate>(size)
        forEach { point ->
            val previous = result.lastOrNull()
            if (
                previous == null ||
                previous.latitude != point.latitude ||
                previous.longitude != point.longitude
            ) {
                result.add(point)
            }
        }
        return result
    }

    companion object {
        private const val ENDPOINT_COVERAGE_PADDING_DEGREES = 0.001
        private const val MIN_ROUTE_PADDING_DEGREES = 0.015
        private const val MAX_ROUTE_PADDING_DEGREES = 0.060
        private const val EXPANDED_RETRY_PADDING_DEGREES = 0.025
        private const val MAX_SNAP_DISTANCE_METERS = 2_500.0
        private const val MAX_ASTAR_EXPANSIONS = 250_000
        private const val DEFAULT_SNAP_SPEED_MPS = 8.33 // ~30 km/h
        private const val DIRECT_FALLBACK_SPEED_MPS = 11.11 // ~40 km/h, display fallback only
    }
}
