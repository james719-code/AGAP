package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentCacheTest {
    @Test
    fun unchangedServerSnapshotUsesOnlyBatchedPrimaryKeyReads() = runBlocking {
        val incoming = (1L..1_001L).map(::incident)
        val cache = CacheDao(incoming.map { it.copy(lastSyncedAt = 1L) })

        cache.dao.cacheIncidentSnapshot(incoming)

        assertEquals(listOf(400, 400, 201), cache.primaryReadSizes)
        assertTrue(cache.logicalReadSizes.isEmpty())
        assertTrue(cache.writeSizes.isEmpty())
    }

    @Test
    fun unchangedMeshSnapshotKeepsUuidAndDoesNotRewriteRows() = runBlocking {
        val incoming = (1L..1_001L).map(::incident)
        val local = incoming.map {
            it.copy(incidentId = "mesh-${it.incidentId}", lastSyncedAt = 1L)
        }
        val cache = CacheDao(local)

        cache.dao.cacheIncidentSnapshot(incoming)

        assertEquals(listOf(400, 400, 201), cache.primaryReadSizes)
        assertEquals(listOf(400, 400, 201), cache.logicalReadSizes)
        assertTrue(cache.writeSizes.isEmpty())
        assertEquals(local, cache.rows.values.toList())
    }

    @Test
    fun meshPlaceholderIsReconciledOnceAndSubsequentPollIsIgnored() = runBlocking {
        val remote = incident(417L)
        val placeholder = remote.copy(
            incidentId = "local-uuid",
            serverIncidentId = null,
            status = "QUEUED",
            deliveryState = "PENDING"
        )
        val cache = CacheDao(listOf(placeholder))

        cache.dao.cacheIncidentSnapshot(listOf(remote))
        cache.dao.cacheIncidentSnapshot(listOf(remote.copy(lastSyncedAt = 2_000L)))

        assertEquals(listOf(1), cache.writeSizes)
        assertEquals(listOf("local-uuid"), cache.rows.keys.toList())
        assertEquals(remote.copy(incidentId = "local-uuid"), cache.rows["local-uuid"])
    }

    @Test
    fun changedStatusAndDispatchAreWrittenWithoutChangingMeshIdentity() = runBlocking {
        val remote = incident(417L)
        val cache = CacheDao(listOf(remote.copy(incidentId = "local-uuid")))
        val dispatched = remote.copy(status = "DISPATCHED", dispatchedUnitId = 42L)

        cache.dao.cacheIncidentSnapshot(listOf(dispatched))

        assertEquals(listOf(1), cache.writeSizes)
        assertEquals(dispatched.copy(incidentId = "local-uuid"), cache.rows["local-uuid"])
    }

    @Test
    fun duplicateLegacyLogicalIdsReconcileTheFirstReturnedMeshAlias() = runBlocking {
        val remote = incident(417L)
        val first = remote.copy(
            incidentId = "legacy-first",
            serverIncidentId = null,
            status = "QUEUED"
        )
        val second = first.copy(incidentId = "legacy-second")
        val cache = CacheDao(listOf(first, second))

        cache.dao.cacheIncidentSnapshot(listOf(remote))

        assertEquals(listOf(1), cache.writeSizes)
        assertEquals(listOf("legacy-first", "legacy-second"), cache.rows.keys.toList())
        assertEquals(remote.copy(incidentId = "legacy-first"), cache.rows["legacy-first"])
        assertEquals(second, cache.rows["legacy-second"])
    }

    @Test
    fun newIncidentsUseBoundedReadsAndWrites() = runBlocking {
        val incoming = (1L..1_001L).map(::incident)
        val cache = CacheDao(emptyList())

        cache.dao.cacheIncidentSnapshot(incoming)

        assertEquals(listOf(400, 400, 201), cache.primaryReadSizes)
        assertEquals(listOf(400, 400, 201), cache.logicalReadSizes)
        assertEquals(listOf(200, 200, 200, 200, 200, 1), cache.writeSizes)
        assertEquals(incoming, cache.rows.values.toList())
    }

    private fun incident(id: Long) = IncidentEntity(
        incidentId = id.toString(),
        serverIncidentId = id,
        targetId = "UNASSIGNED",
        emergencyType = "MEDICAL",
        latitude = 13.14,
        longitude = 123.74,
        timestamp = 1_000L,
        status = "RECEIVED",
        packetHash = "packet-$id",
        lastSyncedAt = 1_000L
    )

    /** Only the snapshot's database operations are allowed; per-row reads fail the test. */
    private class CacheDao(initial: List<IncidentEntity>) {
        val rows = initial.associateByTo(linkedMapOf()) { it.incidentId }
        val primaryReadSizes = mutableListOf<Int>()
        val logicalReadSizes = mutableListOf<Int>()
        val writeSizes = mutableListOf<Int>()

        @Suppress("UNCHECKED_CAST")
        val dao = Proxy.newProxyInstance(
            IncidentDao::class.java.classLoader,
            arrayOf(IncidentDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getIncidentsByIds" -> {
                    val ids = args!![0] as List<String>
                    primaryReadSizes.add(ids.size)
                    ids.mapNotNull(rows::get)
                }
                "getIncidentsByLogicalIds" -> {
                    val ids = args!![0] as List<String>
                    logicalReadSizes.add(ids.size)
                    val requested = ids.toSet()
                    rows.values.filter { it.logicalId in requested }
                }
                "insertIncidents" -> {
                    val incidents = args!![0] as List<IncidentEntity>
                    writeSizes.add(incidents.size)
                    incidents.forEach { rows[it.incidentId] = it }
                    Unit
                }
                else -> error("Unexpected database operation: ${method.name}")
            }
        } as IncidentDao
    }
}
