package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRelayScheduler @Inject constructor(
    private val relayDao: RelayDao
) {
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
            BleMeshPacketType.ANNOUNCEMENT -> 110
        }
    }

    /**
     * BLE/GATT packets are always eligible immediately.
     *
     * The old advertisement transport persisted a future nextAdvertiseMs timestamp
     * and delayed retries by several seconds. GATT v4 does not use a queue-level
     * delay: if any eligible peer exists, the packet may be handed to it now.
     * Same-peer connection-loop protection is handled in BleMeshManager only.
     */
    fun calculateNextAttemptDelay(
        @Suppress("UNUSED_PARAMETER") createdAtMs: Long,
        @Suppress("UNUSED_PARAMETER") attemptCount: Int
    ): Long = 0L

    suspend fun markAttempted(entity: RelayQueueEntity) {
        val now = System.currentTimeMillis()
        // Legacy Room column names are retained to avoid a database migration.
        // nextAdvertiseMs is deliberately kept at `now`, never in the future.
        relayDao.markAdvertised(entity.queueId, now, now)
        BleMeshLog.d(
            "RELAY_ATTEMPT_RECORDED",
            "queueId" to entity.queueId,
            "token" to BleMeshLog.token(entity.transportToken),
            "type" to entity.packetType,
            "count" to (entity.advertiseCount + 1),
            "nextDelayMs" to 0L
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
        val priority = calculatePriority(packet.packetType, isOriginLocal, packet.statusCode)
        val ttlMs = when (packet.packetType) {
            BleMeshPacketType.ACK, BleMeshPacketType.STATUS -> ACK_STATUS_TTL_MS
            BleMeshPacketType.ANNOUNCEMENT -> 24 * 60 * 60_000L // 24 hours
            else -> SOS_REPORT_TTL_MS
        }

        // Update existing queued work row with latest payload & timestamp if it already exists
        relayDao.getQueuedRelay(packet.transportTokenHex, queuePacketType, now)?.let { existing ->
            BleMeshLog.d(
                "RELAY_QUEUE_UPDATE_PAYLOAD",
                "queueId" to existing.queueId,
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "type" to queuePacketType
            )
            val updated = existing.copy(
                payloadBytesHex = payloadBytes.joinToString("") { "%02x".format(it) },
                createdAtMs = now,
                expiryMs = now + ttlMs,
                nextAdvertiseMs = now
            )
            relayDao.enqueueRelay(updated)
            return existing.queueId
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

        // Mark locally-created packets as already seen so receiving the same token
        // back from another peer cannot create a relay loop.
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

    suspend fun getNextPacketToTransfer(): RelayQueueEntity? {
        return relayDao.getNextRelayPacket()
    }

    // Compatibility aliases retained for older tests/callers while storage column
    // names are migrated separately. No emergency payload is advertised in v3.
    fun calculateNextAdvertiseDelay(createdAtMs: Long, advertiseCount: Int): Long =
        calculateNextAttemptDelay(createdAtMs, advertiseCount)

    suspend fun markAdvertised(entity: RelayQueueEntity) = markAttempted(entity)

    suspend fun getNextPacketToBroadcast(): RelayQueueEntity? = getNextPacketToTransfer()

    fun observeQueuedRelayCount(): Flow<Int> = relayDao.observeQueuedRelayCount()

    fun observeUndeliveredMessageCount(): Flow<Int> = relayDao.observeUndeliveredMessageCount()

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

    suspend fun removeTokenFromQueue(token: String) {
        relayDao.removeTokenFromQueue(token)
        BleMeshLog.w("RELAY_TOKEN_REMOVED", "token" to BleMeshLog.token(token))
    }

    suspend fun clearAll() {
        relayDao.clearAllRelayQueue()
        BleMeshLog.w("RELAY_QUEUE_CLEARED_ALL")
    }
}


internal fun BleMeshPacket.seenIdentity(): String {
    return when (packetType) {
        BleMeshPacketType.ACK, BleMeshPacketType.STATUS, BleMeshPacketType.ANNOUNCEMENT -> "${packetType.name}:$statusCode"
        else -> packetType.name
    }
}

internal fun BleMeshPacket.queueIdentity(): String = seenIdentity()
