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

    fun calculatePriority(
        packetType: BleMeshPacketType,
        isOriginLocal: Boolean,
        statusCode: Int = 0
    ): Int {
        return when (packetType) {
            BleMeshPacketType.SOS -> if (isOriginLocal) 100 else 80
            BleMeshPacketType.ACK -> 95
            BleMeshPacketType.STATUS -> if (statusCode == 3 || statusCode == 4) 90 else 85
            BleMeshPacketType.INCIDENT_REPORT -> if (isOriginLocal) 70 else 60
            BleMeshPacketType.BEACON -> 10
        }
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
        val ttlMs = if (packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS) 3600_000L else 7200_000L
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
