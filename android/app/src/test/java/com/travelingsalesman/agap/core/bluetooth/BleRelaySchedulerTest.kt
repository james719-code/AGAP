package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeRelayDao(private val terminalToken: String? = null) : RelayDao {
    override suspend fun insertSeenPacket(seen: SeenMeshPacketEntity) = Unit

    override suspend fun getSeenPacket(token: String, packetType: String): SeenMeshPacketEntity? = null

    override suspend fun isTokenTerminal(token: String): Boolean = token == terminalToken

    override suspend fun enqueueRelay(packet: RelayQueueEntity): Long = 1L

    override suspend fun getQueuedRelay(
        token: String,
        packetType: String,
        now: Long
    ): RelayQueueEntity? = null

    override suspend fun getNextRelayPackets(now: Long): List<RelayQueueEntity> = emptyList()

    override fun observeQueuedRelayCount(): Flow<Int> = flowOf(0)

    override fun observeUndeliveredMessageCount(now: Long): Flow<Int> = flowOf(0)

    override fun observeQueuedRelays(limit: Int): Flow<List<RelayQueueEntity>> = flowOf(emptyList())

    override suspend fun deleteDuplicateQueuedRelays(): Int = 0

    override suspend fun makeRelayEligibleNow(queueId: Long, now: Long) = Unit

    override suspend fun markAdvertised(queueId: Long, now: Long, nextAdvertiseMs: Long) = Unit

    override suspend fun removeQueueEntry(queueId: Long) = Unit

    override suspend fun removeTokenFromQueue(token: String) = Unit

    override suspend fun deleteExpiredRelayRows(now: Long) = Unit

    override suspend fun deleteExpiredSeenRows(now: Long) = Unit

    override suspend fun markTokenTerminal(
        token: String,
        status: String,
        timeMs: Long,
        tombstoneExpiryMs: Long
    ) = Unit
}

class BleRelaySchedulerTest {

    @Test
    fun calculatePriority_returnsExpectedOrdering() {
        val scheduler = BleRelayScheduler(FakeRelayDao())

        val localSos = scheduler.calculatePriority(BleMeshPacketType.SOS, isOriginLocal = true)
        val ack = scheduler.calculatePriority(BleMeshPacketType.ACK, isOriginLocal = false)
        val criticalStatus = scheduler.calculatePriority(
            BleMeshPacketType.STATUS,
            isOriginLocal = false,
            statusCode = 6
        )
        val relayedSos = scheduler.calculatePriority(BleMeshPacketType.SOS, isOriginLocal = false)
        val localReport = scheduler.calculatePriority(
            BleMeshPacketType.INCIDENT_REPORT,
            isOriginLocal = true
        )

        assertEquals(100, localSos)
        assertEquals(95, ack)
        assertEquals(90, criticalStatus)
        assertEquals(80, relayedSos)
        assertEquals(70, localReport)
    }

    @Test
    fun enqueueForRelay_suppressesRelayIfTokenIsTerminal() = runBlocking {
        val fakeDao = FakeRelayDao(terminalToken = "abcdef123456")
        val scheduler = BleRelayScheduler(fakeDao)

        val packet = BleMeshPacket(
            protocolVersion = 2,
            packetType = BleMeshPacketType.SOS,
            emergencyType = "MEDICAL",
            transportToken = byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x12.toByte(),
                0x34.toByte(),
                0x56.toByte()
            ),
            hopCount = 1
        )

        val result = scheduler.enqueueForRelay(packet, byteArrayOf(1, 2, 3), isOriginLocal = false)
        assertEquals(-1L, result)
    }

    @Test
    fun calculateNextAttemptDelay_isAlwaysZeroForGattRelay() {
        val scheduler = BleRelayScheduler(FakeRelayDao())
        val now = System.currentTimeMillis()

        assertEquals(0L, scheduler.calculateNextAttemptDelay(now - 1_000L, 1))
        assertEquals(0L, scheduler.calculateNextAttemptDelay(now - 40 * 60 * 1_000L, 99))
    }
}
