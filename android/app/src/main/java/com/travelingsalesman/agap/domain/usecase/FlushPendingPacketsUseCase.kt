package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import timber.log.Timber
import javax.inject.Inject

class FlushPendingPacketsUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository
) {
    suspend operator fun invoke(): Int {
        val pendingPackets = telemetryRepository.getPendingPackets()
        var flushedCount = 0

        for (packet in pendingPackets) {
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

            when (val result = telemetryRepository.sendUplink(request)) {
                is Result.Success -> {
                    telemetryRepository.deletePendingPacket(packet.packetHash)
                    flushedCount++
                }
                is Result.Error -> {
                    Timber.w("Failed to flush pending packet ${packet.packetHash}: ${result.message}")
                }
                else -> {}
            }
        }
        return flushedCount
    }
}
