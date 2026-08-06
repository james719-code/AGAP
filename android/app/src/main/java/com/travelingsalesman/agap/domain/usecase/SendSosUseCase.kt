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

import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import kotlinx.coroutines.flow.firstOrNull

class SendSosUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val serverConfigManager: ServerConfigManager
) {
    suspend operator fun invoke(
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean,
        forcedMode: TransmissionMode? = null
    ): Result<TelemetryUplinkResponse> {
        val targetId = TargetIdEncoder.encodeTargetId()
        val timestamp = System.currentTimeMillis()
        val rawData = "$emergencyType|$latitude|$longitude|$timestamp"
        val encryptedPayload = CryptoManager.encrypt(rawData)
        val packetHash = CryptoManager.computeHash("$targetId|$timestamp|$encryptedPayload")

        val activeMode = forcedMode ?: serverConfigManager.transmissionMode.firstOrNull() ?: TransmissionMode.AUTO

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

        val pendingPacket = PendingPacketEntity(
            packetHash = packetHash,
            targetId = targetId,
            emergencyType = emergencyType,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            hopCount = 0,
            encryptedPayload = encryptedPayload
        )

        return when (activeMode) {
            TransmissionMode.FORCE_BLE_MESH -> {
                telemetryRepository.savePendingPacket(pendingPacket)
                Result.Success(
                    TelemetryUplinkResponse(
                        success = true,
                        message = "Force BLE Mesh Mode: Saved & Broadcasting P2P via Bluetooth!"
                    )
                )
            }
            TransmissionMode.FORCE_INTERNET -> {
                telemetryRepository.sendUplink(request)
            }
            TransmissionMode.AUTO -> {
                if (isOnline) {
                    val result = telemetryRepository.sendUplink(request)
                    if (result is Result.Error) {
                        telemetryRepository.savePendingPacket(pendingPacket)
                    }
                    result
                } else {
                    telemetryRepository.savePendingPacket(pendingPacket)
                    Result.Success(
                        TelemetryUplinkResponse(
                            success = true,
                            message = "Offline: Stored for BLE Mesh Store-and-Forward relaying."
                        )
                    )
                }
            }
        }
    }
}
