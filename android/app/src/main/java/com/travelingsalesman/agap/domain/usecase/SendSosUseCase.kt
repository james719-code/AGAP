package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.core.security.CryptoManager
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.TargetIdEncoder
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import javax.inject.Inject

class SendSosUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository
) {
    suspend operator fun invoke(
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean
    ): Result<TelemetryUplinkResponse> {
        val targetId = TargetIdEncoder.encodeTargetId()
        val timestamp = System.currentTimeMillis()
        val rawData = "$emergencyType|$latitude|$longitude|$timestamp"
        val encryptedPayload = CryptoManager.encrypt(rawData)
        val packetHash = CryptoManager.computeHash("$targetId|$timestamp|$encryptedPayload")

        val request = TelemetryUplinkRequest(
            packetHash = packetHash,
            targetId = targetId,
            emergencyType = emergencyType,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            hopCount = 0,
            encryptedPayload = encryptedPayload
        )

        return if (isOnline) {
            val result = telemetryRepository.sendUplink(request)
            if (result is Result.Error) {
                // Network failed mid-request; cache in Room for offline retry
                telemetryRepository.savePendingPacket(
                    PendingPacketEntity(
                        packetHash = packetHash,
                        targetId = targetId,
                        emergencyType = emergencyType,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp,
                        hopCount = 0,
                        encryptedPayload = encryptedPayload
                    )
                )
            }
            result
        } else {
            // Offline Mode: store in local Room DB for P2P BLE broadcast and later flush
            telemetryRepository.savePendingPacket(
                PendingPacketEntity(
                    packetHash = packetHash,
                    targetId = targetId,
                    emergencyType = emergencyType,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = timestamp,
                    hopCount = 0,
                    encryptedPayload = encryptedPayload
                )
            )
            Result.Success(
                TelemetryUplinkResponse(
                    success = true,
                    message = "Alert stored locally for BLE Mesh Store-and-Forward relaying."
                )
            )
        }
    }
}
