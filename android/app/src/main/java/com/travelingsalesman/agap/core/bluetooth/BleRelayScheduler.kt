package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRelayScheduler @Inject constructor(
    private val relayDao: RelayDao
) {
    private val rng = SecureRandom()

    companion object {
        const val MAX_HOP = 15
        const val SOS_REPORT_TTL_MS = 7200_000L // 2 hours
        const val ACK_STATUS_TTL_MS = 3600_000L  // 1 hour

        const val PRIORITY_LOCAL_SOS = 100
        const val PRIORITY_ACK = 95
        const val PRIORITY_CRITICAL_STATUS = 90
        const val PRIORITY_RELAYED_SOS = 80
        const val PRIORITY_LOCAL_REPORT = 70
        const val PRIORITY_RELAYED_REPORT = 60
        const val PRIORITY_BEACON = 10
    }

    fun calculatePriority(
        packetType: BleMeshPacketType,
        isOriginLocal: Boolean,
        statusCode: Int = 0
    ): Int {
        return when (packetType) {
            BleMeshPacketType.SOS -> if (isOriginLocal) PRIORITY_LOCAL_SOS else PRIORITY_RELAYED_SOS
            BleMeshPacketType.ACK -> PRIORITY_ACK
            BleMeshPacketType.STATUS -> if (statusCode == 3 || statusCode == 4) PRIORITY_CRITICAL_STATUS else 85
            BleMeshPacketType.INCIDENT_REPORT -> if (isOriginLocal) PRIORITY_LOCAL_REPORT else PRIORITY_RELAYED_REPORT
            BleMeshPacketType.BEACON -> PRIORITY_BEACON
        }
    }

    fun calculateNextAdvertiseDelay(createdAtMs: Long, advertiseCount: Int): Long {
        val ageMs = System.currentTimeMillis() - createdAtMs
        val baseIntervalMs = when {
            ageMs < 10 * 60 * 1000L -> 2000L
            ageMs < 30 * 60 * 1000L -> 4000L
            ageMs < 60 * 60 * 1000L -> 8000L
            else -> 16000L
        }
        val jitter = rng.nextInt(1000).toLong()
        return baseIntervalMs + jitter
    }

    suspend fun markAdvertised(entity: RelayQueueEntity) {
        val delayMs = calculateNextAdvertiseDelay(entity.createdAtMs, entity.advertiseCount + 1)
        val now = System.currentTimeMillis()
        relayDao.markAdvertised(entity.transportToken, entity.packetType, now, now + delayMs)
    }

    suspend fun enqueueForRelay(
        packet: BleMeshPacket,
        payloadBytes: ByteArray,
        isOriginLocal: Boolean
    ): Long {
        val isTerminal = relayDao.isTokenTerminal(packet.transportTokenHex)
        if (isTerminal && packet.packetType != BleMeshPacketType.STATUS) {
            return -1L
        }

        val priority = calculatePriority(packet.packetType, isOriginLocal, packet.statusCode)
        val ttlMs = if (packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS) ACK_STATUS_TTL_MS else SOS_REPORT_TTL_MS
        val now = System.currentTimeMillis()

        val entity = RelayQueueEntity(
            transportToken = packet.transportTokenHex,
            packetType = packet.packetType.name,
            payloadBytesHex = payloadBytes.joinToString("") { "%02x".format(it) },
            priority = priority,
            isOriginLocal = isOriginLocal,
            hopCount = packet.hopCount,
            createdAtMs = now,
            expiryMs = now + ttlMs,
            lastAdvertiseMs = 0,
            nextAdvertiseMs = now + rng.nextInt(500),
            advertiseCount = 0,
            relayState = "QUEUED"
        )

        return relayDao.enqueueRelay(entity)
    }

    suspend fun getNextPacketToBroadcast(): RelayQueueEntity? {
        val candidates = relayDao.getNextRelayPackets()
        return candidates.firstOrNull()
    }
}
