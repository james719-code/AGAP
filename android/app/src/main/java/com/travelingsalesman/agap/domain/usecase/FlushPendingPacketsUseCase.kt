package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.sync.ThrottleConfig
import com.travelingsalesman.agap.core.sync.QueuedUploadRateLimiter
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.remote.dto.TargetIdDto
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FlushPendingPacketsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val authTokenManager: AuthTokenManager,
    private val serverConfigManager: ServerConfigManager,
    private val relayDao: RelayDao,
    private val uploadRateLimiter: QueuedUploadRateLimiter
) {
    private val flushMutex = Mutex()

    suspend fun hasPendingPackets(): Boolean =
        telemetryRepository.hasPendingPackets()

    suspend fun flushMeshDeliveryEvents(): Result<Int> {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("MESH_EVENT_UPLOAD_SKIPPED_BLE_ONLY")
            return Result.Success(0)
        }
        return telemetryRepository.flushUnsyncedMeshDeliveryEvents()
    }

    /**
     * Flushes BLE-to-cloud gateway packets sequentially.
     *
     * Important guarantees:
     *  - FORCE_BLE_MESH never touches the network.
     *  - packets are deleted only after a confirmed server success.
     *  - consecutive queued uploads are separated by 30 seconds.
     *  - only one flush can run in-process at a time.
     *  - the queue is re-read after every upload so packets arriving while the
     *    worker is active are picked up in the same run when capacity remains.
     *
     * @return uploaded count, or -1 when a packet exhausted its retries and the
     * caller should schedule/retry later.
     */
    suspend operator fun invoke(
        jitterOnStart: Boolean = false,
        maxItems: Int = ThrottleConfig.BATCH_SIZE
    ): Int = flushMutex.withLock {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("UPLINK_FLUSH_SKIPPED_BLE_ONLY")
            return@withLock 0
        }

        if (jitterOnStart) {
            val jitter = Random.nextLong(0, ThrottleConfig.MAX_RECONNECT_JITTER_MS + 1)
            if (jitter > 0L) {
                BleMeshLog.d("UPLINK_FLUSH_JITTER", "delayMs" to jitter)
                delay(jitter)
            }
        }

        val senderDeviceId = authTokenManager.deviceId.firstOrNull() ?: "android"
        var flushedCount = 0

        while (flushedCount < maxItems.coerceAtLeast(1)) {
            if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
                BleMeshLog.i(
                    "UPLINK_FLUSH_PAUSED_MODE_CHANGED",
                    "flushed" to flushedCount
                )
                break
            }

            val packet = telemetryRepository.getNextPendingPacket() ?: break
            BleMeshLog.i(
                "UPLINK_PACKET_START",
                "token" to BleMeshLog.token(packet.packetHash),
                "hop" to packet.hopCount,
                "position" to (flushedCount + 1)
            )

            val cameFromBle = packet.targetId.startsWith("BLE-")
            val request = TelemetryUplinkRequest(
                incidentUuid = packet.incidentUuid,
                packetUuid = packet.packetUuid,
                packetHash = packet.packetHash,
                targetId = TargetIdDto(raw = packet.targetId),
                incidentType = packet.emergencyType,
                channel = if (cameFromBle) "BLE_MESH" else "ONLINE",
                deliveryPath = if (cameFromBle) "BLE_CLOUD_SHORTCUT" else "DIRECT_UPLINK",
                latitude = packet.latitude,
                longitude = packet.longitude,
                hopCount = packet.hopCount,
                aesPayload = packet.encryptedPayload,
                senderDeviceId = senderDeviceId,
                timestamp = Instant.ofEpochMilli(packet.timestamp).toString()
            )

            var attempts = 0
            var uploaded = false

            while (attempts < ThrottleConfig.MAX_RETRY_ATTEMPTS && !uploaded) {
                if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
                    BleMeshLog.i(
                        "UPLINK_PACKET_ABORTED_BLE_ONLY",
                        "token" to BleMeshLog.token(packet.packetHash)
                    )
                    return@withLock flushedCount
                }

                attempts++
                val result = uploadRateLimiter.runWithSpacing(
                    label = "gateway"
                ) {
                    telemetryRepository.sendUplink(request)
                }

                when (result) {
                    is Result.Success -> {
                        telemetryRepository.recordMeshDeliveryEvent(
                            MeshDeliveryEventEntity(
                                packetHash = packet.packetHash,
                                packetType = "SOS",
                                eventType = "UPLINKED",
                                latitude = packet.latitude,
                                longitude = packet.longitude,
                                hopCount = packet.hopCount,
                                syncedAt = System.currentTimeMillis()
                            )
                        )
                        telemetryRepository.deletePendingPacket(packet.packetHash)

                        // This device has now completed its BLE -> cloud gateway duty
                        // for the token. Other offline phones may still carry their own
                        // copies until they hear a response or their relay TTL expires.
                        relayDao.removeTokenFromQueue(packet.packetHash)

                        flushedCount++
                        uploaded = true
                        BleMeshLog.i(
                            "UPLINK_PACKET_SUCCESS",
                            "token" to BleMeshLog.token(packet.packetHash),
                            "attempt" to attempts,
                            "flushed" to flushedCount
                        )
                    }

                    is Result.Error -> {
                        BleMeshLog.w(
                            "UPLINK_PACKET_ATTEMPT_FAILED",
                            "token" to BleMeshLog.token(packet.packetHash),
                            "attempt" to attempts,
                            "message" to result.message
                        )
                        if (attempts < ThrottleConfig.MAX_RETRY_ATTEMPTS) {
                            val backoff = ThrottleConfig.RETRY_BASE_DELAY_MS * (1 shl (attempts - 1))
                            delay(backoff)
                        }
                    }

                    Result.Loading -> Unit
                }
            }

            if (!uploaded) {
                BleMeshLog.w(
                    "UPLINK_PACKET_RETRY_LATER",
                    "token" to BleMeshLog.token(packet.packetHash),
                    "attempts" to attempts
                )
                return@withLock -1
            }

        }

        if (flushedCount == 0) {
            BleMeshLog.d("UPLINK_QUEUE_EMPTY")
        } else {
            BleMeshLog.i("UPLINK_FLUSH_DONE", "flushed" to flushedCount)
        }
        flushedCount
    }
}
