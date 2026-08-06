package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.sync.ThrottleConfig
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class FlushPendingPacketsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository
) {
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
        var flushedCount = 0

        for (packet in batch) {
            val request = TelemetryUplinkRequest(
                packetHash = packet.packetHash,
                targetId = packet.targetId,
                emergencyType = packet.emergencyType,
                latitude = packet.latitude,
                longitude = packet.longitude,
                timestamp = packet.timestamp,
                hopCount = packet.hopCount,
                encryptedPayload = packet.encryptedPayload
            )

            var attempts = 0
            var success = false

            while (attempts < ThrottleConfig.MAX_RETRY_ATTEMPTS && !success) {
                attempts++
                when (val result = telemetryRepository.sendUplink(request)) {
                    is Result.Success -> {
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
}

