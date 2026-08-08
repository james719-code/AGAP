package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRelayScheduler @Inject constructor(
    private val relayDao: RelayDao
) {
    private val rng = SecureRandom()

    companion object {
        const val MAX_HOP = 15
        const val SOS_REPORT_TTL_MS = 7_200_000L // 2 hours
        const val ACK_STATUS_TTL_MS = 3_600_000L // 1 hour

        const val PRIORITY_LOCAL_SOS = 100
        const val PRIORITY_ACK = 95
        const val PRIORITY_CRITICAL_STATUS = 90
        const val PRIORITY_RELAYED_SOS = 80
        const val PRIORITY_LOCAL_REPORT = 70
        const val PRIORITY_RELAYED_REPORT = 60
    }

    fun calculatePriority(
        packetType: BleMeshPacketType,
        isOriginLocal: Boolean,
        statusCode: Int = 0
    ): Int {
        return when (packetType) {
            BleMeshPacketType.SOS -> if (isOriginLocal) PRIORITY_LOCAL_SOS else PRIORITY_RELAYED_SOS
            BleMeshPacketType.ACK -> PRIORITY_ACK
            BleMeshPacketType.STATUS -> if (statusCode == 6 || statusCode == 7) PRIORITY_CRITICAL_STATUS else 85
            BleMeshPacketType.INCIDENT_REPORT -> if (isOriginLocal) PRIORITY_LOCAL_REPORT else PRIORITY_RELAYED_REPORT
        }
    }

    fun calculateNextAdvertiseDelay(createdAtMs: Long, advertiseCount: Int): Long {
        val ageMs = System.currentTimeMillis() - createdAtMs
        val baseIntervalMs = when {
            ageMs < 10 * 60 * 1000L -> 2_000L
            ageMs < 30 * 60 * 1000L -> 4_000L
            ageMs < 60 * 60 * 1000L -> 8_000L
            else -> 16_000L
        }
        // A little jitter prevents two relays that heard the same packet at the same
        // time from staying synchronized forever.
        val jitter = rng.nextInt(750).toLong()
        return baseIntervalMs + jitter + (advertiseCount.coerceAtMost(5) * 50L)
    }

    suspend fun markAdvertised(entity: RelayQueueEntity) {
        val delayMs = calculateNextAdvertiseDelay(entity.createdAtMs, entity.advertiseCount + 1)
        val now = System.currentTimeMillis()
        relayDao.markAdvertised(entity.queueId, now, now + delayMs)
        BleMeshLog.d(
            "RELAY_MARK_ADVERTISED",
            "queueId" to entity.queueId,
            "token" to BleMeshLog.token(entity.transportToken),
            "type" to entity.packetType,
            "count" to (entity.advertiseCount + 1),
            "nextDelayMs" to delayMs
        )
    }

    suspend fun enqueueForRelay(
        packet: BleMeshPacket,
        payloadBytes: ByteArray,
        isOriginLocal: Boolean
    ): Long {
        if (packet.hopCount > MAX_HOP) {
            BleMeshLog.w(
                "RELAY_ENQUEUE_HOP_REJECT",
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "hop" to packet.hopCount,
                "maxHop" to MAX_HOP
            )
            return -1L
        }

        val isTerminal = relayDao.isTokenTerminal(packet.transportTokenHex)
        if (isTerminal && packet.packetType != BleMeshPacketType.STATUS) {
            BleMeshLog.d(
                "RELAY_ENQUEUE_TERMINAL_REJECT",
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "type" to packet.packetType.name
            )
            return -1L
        }

        val queuePacketType = packet.queueIdentity()
        val now = System.currentTimeMillis()

        // Do not create one Room row per scan callback. The current row is already
        // being advertised repeatedly and is enough for store-carry-forward relaying.
        relayDao.getQueuedRelay(packet.transportTokenHex, queuePacketType, now)?.let {
            BleMeshLog.d(
                "RELAY_QUEUE_REUSE",
                "queueId" to it.queueId,
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "type" to queuePacketType
            )
            return it.queueId
        }

        val priority = calculatePriority(packet.packetType, isOriginLocal, packet.statusCode)
        val ttlMs = if (
            packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS
        ) {
            ACK_STATUS_TTL_MS
        } else {
            SOS_REPORT_TTL_MS
        }

        val entity = RelayQueueEntity(
            transportToken = packet.transportTokenHex,
            packetType = queuePacketType,
            payloadBytesHex = payloadBytes.joinToString("") { "%02x".format(it) },
            priority = priority,
            isOriginLocal = isOriginLocal,
            hopCount = packet.hopCount,
            createdAtMs = now,
            expiryMs = now + ttlMs,
            lastAdvertiseMs = 0,
            nextAdvertiseMs = now,
            advertiseCount = 0,
            relayState = "QUEUED"
        )

        val rowId = relayDao.enqueueRelay(entity)
        BleMeshLog.i(
            "RELAY_ENQUEUED",
            "queueId" to rowId,
            "token" to BleMeshLog.token(packet.transportTokenHex),
            "type" to queuePacketType,
            "hop" to packet.hopCount,
            "originLocal" to isOriginLocal,
            "priority" to priority,
            "ttlMs" to ttlMs
        )

        // Mark locally-created packets as already seen so a phone never treats its
        // own advertisement as a new incoming packet and creates a relay loop.
        if (isOriginLocal) {
            relayDao.insertSeenPacket(
                SeenMeshPacketEntity(
                    transportToken = packet.transportTokenHex,
                    packetType = packet.seenIdentity(),
                    highestHop = packet.hopCount,
                    firstSeenMs = now,
                    lastSeenMs = now,
                    expiryMs = now + ttlMs,
                    deliveryState = "ORIGIN_LOCAL"
                )
            )
        }

        return rowId
    }

    suspend fun getNextPacketToBroadcast(): RelayQueueEntity? {
        return relayDao.getNextRelayPackets().firstOrNull()
    }

    fun observeQueuedRelayCount(): Flow<Int> = relayDao.observeQueuedRelayCount()

    fun observeQueuedRelays(limit: Int): Flow<List<RelayQueueEntity>> =
        relayDao.observeQueuedRelays(limit.coerceIn(1, 2_000))

    suspend fun cleanupExpired() {
        val now = System.currentTimeMillis()
        relayDao.deleteExpiredRelayRows(now)
        relayDao.deleteExpiredSeenRows(now)
        val removedDuplicates = relayDao.deleteDuplicateQueuedRelays()
        if (removedDuplicates > 0) {
            BleMeshLog.w(
                "RELAY_DUPLICATES_CLEANED",
                "removed" to removedDuplicates
            )
        }
    }

    suspend fun makeRelayEligibleNow(queueId: Long) {
        relayDao.makeRelayEligibleNow(queueId)
        BleMeshLog.i("RELAY_MANUAL_RETRY", "queueId" to queueId)
    }

    suspend fun removeQueueEntry(queueId: Long) {
        relayDao.removeQueueEntry(queueId)
        BleMeshLog.w("RELAY_QUEUE_REMOVED", "queueId" to queueId)
    }
}

internal fun BleMeshPacket.seenIdentity(): String {
    return when (packetType) {
        BleMeshPacketType.ACK, BleMeshPacketType.STATUS -> "${packetType.name}:$statusCode"
        else -> packetType.name
    }
}

internal fun BleMeshPacket.queueIdentity(): String = seenIdentity()
