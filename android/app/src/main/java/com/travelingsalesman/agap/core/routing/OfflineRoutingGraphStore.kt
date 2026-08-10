package com.travelingsalesman.agap.core.routing

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent road graph stored in AGAP's private app database storage.
 *
 * The MBTiles/osmdroid cache remains responsible only for drawing the map.
 * This database stores the connected OSM road graph required by A*.
 *
 * A preprocessed database from the AGAP backend can replace this database
 * atomically. That avoids emergency-time Overpass downloads, JSON parsing and
 * tens of thousands of INSERT statements on the phone.
 */
@Singleton
class OfflineRoutingGraphStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val helper = RoutingDbHelper(context.applicationContext)

    @Volatile
    private var cachedGraph: CachedGraph? = null

    fun hasCoverage(bounds: RoutingBounds): Boolean {
        val db = helper.readableDatabase
        return db.rawQuery(
            """
            SELECT 1
            FROM routing_coverage
            WHERE south <= ?
              AND west <= ?
              AND north >= ?
              AND east >= ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                bounds.south.toString(),
                bounds.west.toString(),
                bounds.north.toString(),
                bounds.east.toString()
            )
        ).use { cursor -> cursor.moveToFirst() }
    }

    fun hasAnyCoverage(): Boolean {
        val db = helper.readableDatabase
        return db.rawQuery(
            "SELECT 1 FROM routing_coverage LIMIT 1",
            null
        ).use { cursor -> cursor.moveToFirst() }
    }

    @Synchronized
    internal fun saveGraph(
        nodes: Collection<RoutingNode>,
        edges: Collection<RoutingEdge>,
        coverage: RoutingBounds
    ) {
        if (nodes.isEmpty() || edges.isEmpty()) return

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val nodeValues = ContentValues(3)
            nodes.forEach { node ->
                nodeValues.clear()
                nodeValues.put("id", node.id)
                nodeValues.put("lat", node.latitude)
                nodeValues.put("lon", node.longitude)
                db.insertWithOnConflict(
                    "routing_nodes",
                    null,
                    nodeValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            val edgeValues = ContentValues(6)
            edges.forEach { edge ->
                edgeValues.clear()
                edgeValues.put("from_node", edge.fromNode)
                edgeValues.put("to_node", edge.toNode)
                edgeValues.put("distance_m", edge.distanceMeters)
                edgeValues.put("speed_kph", edge.speedKph)
                edgeValues.put("road_class", edge.roadClass)
                edgeValues.put("way_id", edge.wayId)
                db.insertWithOnConflict(
                    "routing_edges",
                    null,
                    edgeValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            val coverageValues = ContentValues(5).apply {
                put("south", coverage.south)
                put("west", coverage.west)
                put("north", coverage.north)
                put("east", coverage.east)
                put("updated_at", System.currentTimeMillis())
            }
            db.insert("routing_coverage", null, coverageValues)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        cachedGraph = null
    }

    /**
     * Installs a backend-preprocessed routing DB by replacing the app's routing
     * database file. The supplied file is validated first and the previous DB is
     * restored if activation fails.
     */
    @Synchronized
    fun installPrebuiltDatabase(sourceFile: File): Boolean {
        if (!validateRoutingDatabase(sourceFile)) return false

        val target = context.getDatabasePath(ROUTING_DB_NAME)
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false

        val backup = File(parent, "$ROUTING_DB_NAME.bak")
        val stage = File(parent, "$ROUTING_DB_NAME.installing")

        helper.close()
        cachedGraph = null
        deleteSqliteSidecars(target)
        stage.delete()

        return try {
            sourceFile.copyTo(stage, overwrite = true)
            if (!validateRoutingDatabase(stage)) {
                stage.delete()
                return false
            }

            backup.delete()
            if (target.exists() && !target.renameTo(backup)) {
                target.copyTo(backup, overwrite = true)
                target.delete()
            }

            if (!stage.renameTo(target)) {
                stage.copyTo(target, overwrite = true)
                stage.delete()
            }

            if (!validateRoutingDatabase(target)) {
                target.delete()
                if (backup.exists()) backup.renameTo(target)
                return false
            }

            backup.delete()
            sourceFile.delete()
            true
        } catch (_: Exception) {
            stage.delete()
            target.delete()
            if (backup.exists()) {
                runCatching { backup.renameTo(target) }
            }
            false
        }
    }

    /**
     * Loads a graph window and caches a slightly larger window in memory. Admin
     * can preview several responders around one incident without repeating the
     * SQLite joins for every tap.
     */
    @Synchronized
    internal fun loadGraph(bounds: RoutingBounds): RoutingGraph {
        cachedGraph?.let { cached ->
            if (cached.bounds.contains(bounds)) {
                return cached.graph
            }
        }

        // Prefer the full installed coverage window that contains this route.
        // A backend-prebuilt deployment package normally has one city-sized
        // coverage row, so the first preview pays the SQLite materialization
        // cost once and every later responder preview reuses the same RAM graph.
        // Small Overpass fallback corridors still cache only their own coverage.
        val loadBounds = findLargestContainingCoverage(bounds)
            ?: bounds.expanded(MEMORY_CACHE_PADDING_DEGREES)
        val graph = loadGraphFromDatabase(loadBounds)
        cachedGraph = CachedGraph(loadBounds, graph)
        return graph
    }

    private fun findLargestContainingCoverage(bounds: RoutingBounds): RoutingBounds? {
        val db = helper.readableDatabase
        return db.rawQuery(
            """
            SELECT south, west, north, east
            FROM routing_coverage
            WHERE south <= ?
              AND west <= ?
              AND north >= ?
              AND east >= ?
            ORDER BY ((north - south) * (east - west)) DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                bounds.south.toString(),
                bounds.west.toString(),
                bounds.north.toString(),
                bounds.east.toString()
            )
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            RoutingBounds(
                south = cursor.getDouble(0),
                west = cursor.getDouble(1),
                north = cursor.getDouble(2),
                east = cursor.getDouble(3)
            )
        }
    }

    private fun loadGraphFromDatabase(bounds: RoutingBounds): RoutingGraph {
        val db = helper.readableDatabase
        val nodes = LinkedHashMap<Long, RoutingNode>()

        db.rawQuery(
            """
            SELECT id, lat, lon
            FROM routing_nodes
            WHERE lat BETWEEN ? AND ?
              AND lon BETWEEN ? AND ?
            """.trimIndent(),
            arrayOf(
                bounds.south.toString(),
                bounds.north.toString(),
                bounds.west.toString(),
                bounds.east.toString()
            )
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val latIndex = cursor.getColumnIndexOrThrow("lat")
            val lonIndex = cursor.getColumnIndexOrThrow("lon")
            while (cursor.moveToNext()) {
                val node = RoutingNode(
                    id = cursor.getLong(idIndex),
                    latitude = cursor.getDouble(latIndex),
                    longitude = cursor.getDouble(lonIndex)
                )
                nodes[node.id] = node
            }
        }

        if (nodes.isEmpty()) {
            return RoutingGraph(emptyMap(), emptyMap())
        }

        val adjacency = HashMap<Long, MutableList<RoutingEdge>>()

        db.rawQuery(
            """
            SELECT e.from_node,
                   e.to_node,
                   e.distance_m,
                   e.speed_kph,
                   e.road_class,
                   e.way_id
            FROM routing_edges e
            INNER JOIN routing_nodes a ON a.id = e.from_node
            INNER JOIN routing_nodes b ON b.id = e.to_node
            WHERE a.lat BETWEEN ? AND ?
              AND a.lon BETWEEN ? AND ?
              AND b.lat BETWEEN ? AND ?
              AND b.lon BETWEEN ? AND ?
            """.trimIndent(),
            arrayOf(
                bounds.south.toString(),
                bounds.north.toString(),
                bounds.west.toString(),
                bounds.east.toString(),
                bounds.south.toString(),
                bounds.north.toString(),
                bounds.west.toString(),
                bounds.east.toString()
            )
        ).use { cursor ->
            val fromIndex = cursor.getColumnIndexOrThrow("from_node")
            val toIndex = cursor.getColumnIndexOrThrow("to_node")
            val distanceIndex = cursor.getColumnIndexOrThrow("distance_m")
            val speedIndex = cursor.getColumnIndexOrThrow("speed_kph")
            val classIndex = cursor.getColumnIndexOrThrow("road_class")
            val wayIndex = cursor.getColumnIndexOrThrow("way_id")

            while (cursor.moveToNext()) {
                val edge = RoutingEdge(
                    fromNode = cursor.getLong(fromIndex),
                    toNode = cursor.getLong(toIndex),
                    distanceMeters = cursor.getDouble(distanceIndex),
                    speedKph = cursor.getDouble(speedIndex),
                    roadClass = cursor.getString(classIndex),
                    wayId = cursor.getLong(wayIndex)
                )
                // The destination should be in the node query because both ends
                // were constrained above. Keep the guard for malformed packages.
                if (edge.fromNode in nodes && edge.toNode in nodes) {
                    adjacency.getOrPut(edge.fromNode) { mutableListOf() }.add(edge)
                }
            }
        }

        return RoutingGraph(
            nodes = nodes,
            adjacency = adjacency
        )
    }

    private fun validateRoutingDatabase(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false

        return runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val requiredTables = setOf(
                    "routing_nodes",
                    "routing_edges",
                    "routing_coverage"
                )
                val foundTables = mutableSetOf<String>()
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'",
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) foundTables += cursor.getString(0)
                }
                if (!foundTables.containsAll(requiredTables)) return@use false

                val nodeCount = db.rawQuery(
                    "SELECT COUNT(*) FROM routing_nodes",
                    null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                val edgeCount = db.rawQuery(
                    "SELECT COUNT(*) FROM routing_edges",
                    null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                val coverageCount = db.rawQuery(
                    "SELECT COUNT(*) FROM routing_coverage",
                    null
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

                nodeCount > 0L && edgeCount > 0L && coverageCount > 0L
            }
        }.getOrDefault(false)
    }

    private fun deleteSqliteSidecars(database: File) {
        File(database.absolutePath + "-wal").delete()
        File(database.absolutePath + "-shm").delete()
        File(database.absolutePath + "-journal").delete()
    }

    private data class CachedGraph(
        val bounds: RoutingBounds,
        val graph: RoutingGraph
    )

    private class RoutingDbHelper(
        context: Context
    ) : SQLiteOpenHelper(
        context,
        ROUTING_DB_NAME,
        null,
        ROUTING_DB_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE routing_nodes (
                    id INTEGER PRIMARY KEY,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE routing_edges (
                    from_node INTEGER NOT NULL,
                    to_node INTEGER NOT NULL,
                    distance_m REAL NOT NULL,
                    speed_kph REAL NOT NULL,
                    road_class TEXT NOT NULL,
                    way_id INTEGER NOT NULL,
                    PRIMARY KEY (from_node, to_node, way_id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE routing_coverage (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    south REAL NOT NULL,
                    west REAL NOT NULL,
                    north REAL NOT NULL,
                    east REAL NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_routing_nodes_lat_lon ON routing_nodes(lat, lon)")
            db.execSQL("CREATE INDEX idx_routing_edges_from ON routing_edges(from_node)")
            db.execSQL("CREATE INDEX idx_routing_edges_to ON routing_edges(to_node)")
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            db.execSQL("DROP TABLE IF EXISTS routing_coverage")
            db.execSQL("DROP TABLE IF EXISTS routing_edges")
            db.execSQL("DROP TABLE IF EXISTS routing_nodes")
            onCreate(db)
        }
    }

    companion object {
        private const val ROUTING_DB_NAME = "agap_offline_routing.db"
        private const val ROUTING_DB_VERSION = 1
        private const val MEMORY_CACHE_PADDING_DEGREES = 0.012
    }
}
