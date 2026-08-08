package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.sync.ThrottleConfig
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.remote.dto.SyncPushRequestDto
import com.travelingsalesman.agap.data.remote.dto.SyncTelemetryPacketDto
import com.travelingsalesman.agap.data.remote.dto.TargetIdDto
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random

class FlushPendingPacketsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val authTokenManager: AuthTokenManager,
    private val serverConfigManager: ServerConfigManager
) {
    suspend fun flushMeshDeliveryEvents(): Result<Int> {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("MESH_EVENT_UPLOAD_SKIPPED_BLE_ONLY")
            return Result.Success(0)
        }
        return telemetryRepository.flushUnsyncedMeshDeliveryEvents()
    }

    suspend operator fun invoke(jitterOnStart: Boolean = false): Int {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("UPLINK_FLUSH_SKIPPED_BLE_ONLY")
            return 0
        }

        if (jitterOnStart) {
            val jitter = Random.nextLong(0, ThrottleConfig.MAX_RECONNECT_JITTER_MS)
            BleMeshLog.d("UPLINK_FLUSH_JITTER", "delayMs" to jitter)
            delay(jitter)
        }

        val allPending = telemetryRepository.getPendingPackets()
        if (allPending.isEmpty()) {
            BleMeshLog.d("UPLINK_QUEUE_EMPTY")
            return 0
        }
        BleMeshLog.i("UPLINK_FLUSH_START", "pending" to allPending.size)

        // Take up to BATCH_SIZE items per run (sorted by priority DESC, created_at ASC via DAO query)
        val batch = allPending.take(ThrottleConfig.BATCH_SIZE)
        val senderDeviceId = authTokenManager.deviceId.firstOrNull() ?: "android"
        if (flushBatchViaSyncPush(batch, senderDeviceId)) {
            BleMeshLog.i("UPLINK_BATCH_SUCCESS", "count" to batch.size)
            return batch.size
        }

        var flushedCount = 0

        for (packet in batch) {
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
            var success = false

            while (attempts < ThrottleConfig.MAX_RETRY_ATTEMPTS && !success) {
                attempts++
                when (val result = telemetryRepository.sendUplink(request)) {
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
                        flushedCount++
                        success = true
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
                    else -> break
                }
            }

            // Apply inter-packet delay to avoid flooding the DB/network connection pool
            delay(ThrottleConfig.INTER_PACKET_DELAY_MS)
        }

        BleMeshLog.i("UPLINK_FLUSH_DONE", "flushed" to flushedCount, "batch" to batch.size)
        return flushedCount
    }

    private suspend fun flushBatchViaSyncPush(batch: List<PendingPacketEntity>, senderDeviceId: String): Boolean {
        if (batch.isEmpty()) return true

        val request = SyncPushRequestDto(
            telemetryPackets = batch.map { packet ->
                SyncTelemetryPacketDto(
                    packetUuid = packet.packetUuid,
                    incidentUuid = packet.incidentUuid,
                    packetHash = packet.packetHash,
                    targetId = TargetIdDto(raw = packet.targetId),
                    incidentType = packet.emergencyType,
                    channel = if (packet.targetId.startsWith("BLE-")) "BLE_MESH" else "ONLINE",
                    deliveryPath = if (packet.targetId.startsWith("BLE-")) "BLE_CLOUD_SHORTCUT" else "DIRECT_UPLINK",
                    latitude = packet.latitude,
                    longitude = packet.longitude,
                    hopCount = packet.hopCount,
                    aesPayload = packet.encryptedPayload,
                    senderDeviceId = senderDeviceId,
                    timestamp = Instant.ofEpochMilli(packet.timestamp).toString()
                )
            }
        )

        return when (val result = telemetryRepository.pushSync(request)) {
            is Result.Success -> {
                val response = result.data
                if (response.errors.isNotEmpty()) {
                    BleMeshLog.w("UPLINK_BATCH_SERVER_ERRORS", "count" to response.errors.size)
                    false
                } else {
                    batch.forEach { packet ->
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
                    }
                    true
                }
            }
            is Result.Error -> {
                BleMeshLog.w("UPLINK_BATCH_FAILED", "message" to result.message)
                false
            }
            else -> false
        }
    }
}
