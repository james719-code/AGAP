package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.sync.ThrottleConfig
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AuthTokenManager
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
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class FlushPendingPacketsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val authTokenManager: AuthTokenManager
) {
    suspend fun flushMeshDeliveryEvents(): Result<Int> {
        return telemetryRepository.flushUnsyncedMeshDeliveryEvents()
    }

    suspend operator fun invoke(jitterOnStart: Boolean = false): Int {
        if (jitterOnStart) {
            val jitter = Random.nextLong(0, ThrottleConfig.MAX_RECONNECT_JITTER_MS)
            Timber.d("Jittering start of packet flush by ${jitter}ms to prevent server flood")
            delay(jitter)
        }

        val allPending = telemetryRepository.getPendingPackets()
        if (allPending.isEmpty()) return 0

        // Take up to BATCH_SIZE items per run (sorted by priority DESC, created_at ASC via DAO query)
        val batch = allPending.take(ThrottleConfig.BATCH_SIZE)
        val senderDeviceId = authTokenManager.deviceId.firstOrNull() ?: "android"
        if (flushBatchViaSyncPush(batch, senderDeviceId)) {
            Timber.i("Flushed ${batch.size} pending packets via sync push")
            return batch.size
        }

        var flushedCount = 0

        for (packet in batch) {
            val request = TelemetryUplinkRequest(
                incidentUuid = packet.incidentUuid,
                packetUuid = packet.packetUuid,
                packetHash = packet.packetHash,
                targetId = TargetIdDto(raw = packet.targetId),
                incidentType = packet.emergencyType,
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
                        Timber.w("Attempt $attempts failed for packet ${packet.packetHash}: ${result.message}")
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

        Timber.i("Flushed $flushedCount / ${batch.size} pending packets in this cycle")
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
                    Timber.w("Sync push returned ${response.errors.size} errors; falling back to per-packet uplink")
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
                Timber.w("Sync push failed; falling back to per-packet uplink: ${result.message}")
                false
            }
            else -> false
        }
    }
}
