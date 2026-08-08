package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeRelayDao(private val terminalToken: String? = null) : RelayDao {
    override suspend fun insertSeenPacket(seen: SeenMeshPacketEntity) {}
    override suspend fun getSeenPacket(token: String): SeenMeshPacketEntity? = null
    override suspend fun isTokenTerminal(token: String): Boolean = (token == terminalToken)
    override suspend fun enqueueRelay(packet: RelayQueueEntity): Long = 1L
    override suspend fun getNextRelayPackets(now: Long): List<RelayQueueEntity> = emptyList()
    override suspend fun markAdvertised(token: String, packetType: String, now: Long, nextAdvertiseMs: Long) {}
    override suspend fun removeTokenFromQueue(token: String) {}
    override suspend fun markTokenTerminal(token: String, status: String, timeMs: Long, tombstoneExpiryMs: Long) {}
}

class BleRelaySchedulerTest {

    @Test
    fun calculatePriority_returnsExpectedOrdering() {
        val scheduler = BleRelayScheduler(FakeRelayDao())

        val localSos = scheduler.calculatePriority(BleMeshPacketType.SOS, isOriginLocal = true)
        val ack = scheduler.calculatePriority(BleMeshPacketType.ACK, isOriginLocal = false)
        val criticalStatus = scheduler.calculatePriority(BleMeshPacketType.STATUS, isOriginLocal = false, statusCode = 6)
        val relayedSos = scheduler.calculatePriority(BleMeshPacketType.SOS, isOriginLocal = false)
        val localReport = scheduler.calculatePriority(BleMeshPacketType.INCIDENT_REPORT, isOriginLocal = true)

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
            transportToken = byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x12.toByte(), 0x34.toByte(), 0x56.toByte()),
            hopCount = 1
        )

        val result = scheduler.enqueueForRelay(packet, byteArrayOf(1, 2, 3), isOriginLocal = false)
        assertEquals(-1L, result)
    }

    @Test
    fun calculateNextAdvertiseDelay_increasesWithAge() {
        val scheduler = BleRelayScheduler(FakeRelayDao())
        val now = System.currentTimeMillis()

        val recentDelay = scheduler.calculateNextAdvertiseDelay(now - 1000L, 1)
        val oldDelay = scheduler.calculateNextAdvertiseDelay(now - 40 * 60 * 1000L, 5)

        assert(recentDelay >= 2000L && recentDelay <= 3000L)
        assert(oldDelay >= 8000L && oldDelay <= 9000L)
    }
}
