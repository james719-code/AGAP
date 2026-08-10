package com.travelingsalesman.agap.core.routing

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small routing-only coordinate model. Keeping this outside osmdroid means the
 * routing engine can be unit tested without a MapView.
 */
data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double
)

enum class RouteSource {
    OFFLINE_GRAPH,
    DOWNLOADED_GRAPH,
    OFFLINE_PARTIAL_GRAPH,
    DIRECT_FALLBACK
}

data class OfflineRouteResult(
    val points: List<RouteCoordinate>,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val source: RouteSource,
    val isRoadRoute: Boolean,
    val message: String? = null
) {
    val distanceKm: Double
        get() = distanceMeters / 1000.0
}

data class RoutingBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    init {
        require(south <= north) { "south must be <= north" }
        require(west <= east) { "west must be <= east" }
    }

    fun expanded(paddingDegrees: Double): RoutingBounds = RoutingBounds(
        south = (south - paddingDegrees).coerceAtLeast(-90.0),
        west = (west - paddingDegrees).coerceAtLeast(-180.0),
        north = (north + paddingDegrees).coerceAtMost(90.0),
        east = (east + paddingDegrees).coerceAtMost(180.0)
    )

    fun contains(other: RoutingBounds): Boolean =
        south <= other.south &&
            west <= other.west &&
            north >= other.north &&
            east >= other.east

    companion object {
        fun around(
            start: RouteCoordinate,
            end: RouteCoordinate,
            paddingDegrees: Double
        ): RoutingBounds = RoutingBounds(
            south = min(start.latitude, end.latitude),
            west = min(start.longitude, end.longitude),
            north = max(start.latitude, end.latitude),
            east = max(start.longitude, end.longitude)
        ).expanded(paddingDegrees)
    }
}

internal data class RoutingNode(
    val id: Long,
    val latitude: Double,
    val longitude: Double
)

internal data class RoutingEdge(
    val fromNode: Long,
    val toNode: Long,
    val distanceMeters: Double,
    val speedKph: Double,
    val roadClass: String,
    val wayId: Long
) {
    val durationSeconds: Double
        get() {
            val metersPerSecond = (speedKph.coerceAtLeast(5.0) * 1000.0) / 3600.0
            return distanceMeters / metersPerSecond
        }
}

/**
 * Small fixed-grid index used only for snapping GPS/incident coordinates to the
 * closest routing node. The previous implementation scanned every routing node
 * twice for each preview. On a city graph this removes tens of thousands of
 * Haversine calculations from every route request.
 */
internal class RoutingSpatialIndex(
    nodes: Collection<RoutingNode>
) {
    private val cells = HashMap<Long, MutableList<RoutingNode>>()

    init {
        nodes.forEach { node ->
            cells.getOrPut(cellKey(node.latitude, node.longitude)) { mutableListOf() }
                .add(node)
        }
    }

    fun nearest(
        point: RouteCoordinate,
        maxDistanceMeters: Double
    ): RoutingNode? {
        if (cells.isEmpty()) return null

        val baseLat = cellCoordinate(point.latitude)
        val baseLon = cellCoordinate(point.longitude)

        val latDegrees = maxDistanceMeters / METERS_PER_LAT_DEGREE
        val lonMetersPerDegree =
            (METERS_PER_LAT_DEGREE * cos(Math.toRadians(point.latitude)))
                .coerceAtLeast(MIN_LON_METERS_PER_DEGREE)
        val lonDegrees = maxDistanceMeters / lonMetersPerDegree

        val latRadius = ceil(latDegrees / CELL_SIZE_DEGREES).toInt() + 1
        val lonRadius = ceil(lonDegrees / CELL_SIZE_DEGREES).toInt() + 1

        var best: RoutingNode? = null
        var bestDistance = maxDistanceMeters

        for (latOffset in -latRadius..latRadius) {
            for (lonOffset in -lonRadius..lonRadius) {
                val bucket = cells[cellKey(baseLat + latOffset, baseLon + lonOffset)] ?: continue
                bucket.forEach { node ->
                    // Cheap rectangular rejection before the Haversine calculation.
                    if (kotlin.math.abs(node.latitude - point.latitude) > latDegrees) return@forEach
                    if (kotlin.math.abs(node.longitude - point.longitude) > lonDegrees) return@forEach

                    val distance = haversineMeters(
                        point.latitude,
                        point.longitude,
                        node.latitude,
                        node.longitude
                    )
                    if (distance <= bestDistance) {
                        bestDistance = distance
                        best = node
                    }
                }
            }
        }

        return best
    }

    private fun cellCoordinate(value: Double): Int =
        floor(value / CELL_SIZE_DEGREES).toInt()

    private fun cellKey(latitude: Double, longitude: Double): Long =
        cellKey(cellCoordinate(latitude), cellCoordinate(longitude))

    private fun cellKey(latCell: Int, lonCell: Int): Long =
        (latCell.toLong() shl 32) xor (lonCell.toLong() and 0xffffffffL)

    companion object {
        private const val CELL_SIZE_DEGREES = 0.003
        private const val METERS_PER_LAT_DEGREE = 111_000.0
        private const val MIN_LON_METERS_PER_DEGREE = 20_000.0
    }
}

internal class RoutingGraph(
    val nodes: Map<Long, RoutingNode>,
    val adjacency: Map<Long, List<RoutingEdge>>,
    val spatialIndex: RoutingSpatialIndex = RoutingSpatialIndex(nodes.values)
)

internal fun haversineMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusM = 6_371_000.0
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLat = Math.toRadians(lat2 - lat1)
    val deltaLon = Math.toRadians(lon2 - lon1)

    val a = sin(deltaLat / 2.0).pow(2) +
        cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2)

    return 2.0 * earthRadiusM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
