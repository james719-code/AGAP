package com.travelingsalesman.agap.core.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class DownloadedRoadGraph(
    val nodes: Collection<RoutingNode>,
    val edges: Collection<RoutingEdge>
)

/**
 * Downloads OSM road topology only when AGAP has internet transport available.
 * The result is immediately persisted by OfflineRouteEngine and reused offline.
 *
 * A dedicated client is intentional: the app's normal OkHttp client rewrites
 * requests to the configured AGAP backend, which must not happen for Overpass.
 */
@Singleton
class OsmRoadGraphDownloader @Inject constructor(
    private val json: Json
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    internal fun download(bounds: RoutingBounds): DownloadedRoadGraph? {
        val query = buildQuery(bounds)
        val requestBody = FormBody.Builder()
            .add("data", query)
            .build()

        for (endpoint in OVERPASS_ENDPOINTS) {
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("User-Agent", "AGAP-Android/1.0 offline-emergency-routing")
                .build()

            val parsed = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    json.decodeFromString<OverpassResponse>(body)
                }
            }.getOrNull() ?: continue

            val graph = toRoadGraph(parsed)
            if (graph.nodes.isNotEmpty() && graph.edges.isNotEmpty()) {
                return graph
            }
        }

        return null
    }

    private fun buildQuery(bounds: RoutingBounds): String =
        """
        [out:json][timeout:45];
        way["highway"](${bounds.south},${bounds.west},${bounds.north},${bounds.east});
        (._;>;);
        out body;
        """.trimIndent()

    private fun toRoadGraph(response: OverpassResponse): DownloadedRoadGraph {
        val nodes = response.elements
            .asSequence()
            .filter { it.type == "node" && it.lat != null && it.lon != null }
            .associate { element ->
                element.id to RoutingNode(
                    id = element.id,
                    latitude = element.lat!!,
                    longitude = element.lon!!
                )
            }

        val edges = ArrayList<RoutingEdge>()

        response.elements
            .asSequence()
            .filter { it.type == "way" }
            .forEach wayLoop@ { way ->
                val highway = way.tags["highway"] ?: return@wayLoop
                if (!isMotorRoutable(highway, way.tags)) return@wayLoop

                val wayNodes = way.nodes
                if (wayNodes.size < 2) return@wayLoop

                val speedKph = parseSpeedKph(way.tags["maxspeed"], highway)
                val oneWay = when {
                    way.tags["oneway"].equals("-1", ignoreCase = true) -> -1
                    way.tags["oneway"].equals("yes", ignoreCase = true) ||
                        way.tags["oneway"].equals("true", ignoreCase = true) ||
                        way.tags["oneway"] == "1" ||
                        way.tags["junction"].equals("roundabout", ignoreCase = true) -> 1
                    else -> 0
                }

                wayNodes.zipWithNext().forEach edgeLoop@ { (aId, bId) ->
                    val a = nodes[aId] ?: return@edgeLoop
                    val b = nodes[bId] ?: return@edgeLoop
                    val distance = haversineMeters(
                        a.latitude,
                        a.longitude,
                        b.latitude,
                        b.longitude
                    )
                    if (distance <= 0.0) return@edgeLoop

                    fun edge(from: Long, to: Long) = RoutingEdge(
                        fromNode = from,
                        toNode = to,
                        distanceMeters = distance,
                        speedKph = speedKph,
                        roadClass = highway,
                        wayId = way.id
                    )

                    when (oneWay) {
                        1 -> edges.add(edge(aId, bId))
                        -1 -> edges.add(edge(bId, aId))
                        else -> {
                            edges.add(edge(aId, bId))
                            edges.add(edge(bId, aId))
                        }
                    }
                }
            }

        return DownloadedRoadGraph(
            nodes = nodes.values,
            edges = edges
        )
    }

    private fun isMotorRoutable(
        highway: String,
        tags: Map<String, String>
    ): Boolean {
        if (highway.lowercase() in NON_MOTOR_HIGHWAYS) return false

        val vehicleAccess = tags["motor_vehicle"] ?: tags["vehicle"]
        if (vehicleAccess.equals("no", ignoreCase = true)) return false

        // Construction/proposed roads are not usable even if a highway=* value
        // happens to be present in malformed/imported OSM data.
        if (tags.containsKey("construction") || tags.containsKey("proposed")) return false

        return true
    }

    private fun parseSpeedKph(
        raw: String?,
        highway: String
    ): Double {
        if (!raw.isNullOrBlank()) {
            val number = SPEED_NUMBER.find(raw)?.value?.toDoubleOrNull()
            if (number != null && number > 0.0) {
                return if (raw.contains("mph", ignoreCase = true)) {
                    number * 1.609344
                } else {
                    number
                }.coerceIn(5.0, 120.0)
            }
        }

        return when (highway.lowercase()) {
            "motorway", "motorway_link" -> 80.0
            "trunk", "trunk_link" -> 70.0
            "primary", "primary_link" -> 60.0
            "secondary", "secondary_link" -> 50.0
            "tertiary", "tertiary_link" -> 40.0
            "residential", "unclassified" -> 30.0
            "living_street" -> 15.0
            "service" -> 20.0
            else -> 25.0
        }
    }

    @Serializable
    private data class OverpassResponse(
        @SerialName("elements")
        val elements: List<OverpassElement> = emptyList()
    )

    @Serializable
    private data class OverpassElement(
        @SerialName("type")
        val type: String,
        @SerialName("id")
        val id: Long,
        @SerialName("lat")
        val lat: Double? = null,
        @SerialName("lon")
        val lon: Double? = null,
        @SerialName("nodes")
        val nodes: List<Long> = emptyList(),
        @SerialName("tags")
        val tags: Map<String, String> = emptyMap()
    )

    companion object {
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        private val SPEED_NUMBER = Regex("\\d+(?:\\.\\d+)?")

        private val NON_MOTOR_HIGHWAYS = setOf(
            "footway",
            "path",
            "cycleway",
            "steps",
            "pedestrian",
            "bridleway",
            "corridor",
            "platform",
            "raceway",
            "construction",
            "proposed"
        )
    }
}
