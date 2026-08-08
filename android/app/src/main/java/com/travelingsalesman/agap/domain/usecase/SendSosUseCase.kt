package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.core.security.CryptoManager
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.TargetIdEncoder
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import javax.inject.Inject

import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.dto.TargetIdDto
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.util.UUID

class SendSosUseCase @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val serverConfigManager: ServerConfigManager,
    private val authTokenManager: AuthTokenManager
) {
    suspend operator fun invoke(
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        isOnline: Boolean,
        forcedMode: TransmissionMode? = null
    ): Result<TelemetryUplinkResponse> {
        val targetId = "BLE_P2P"
        val timestamp = System.currentTimeMillis()
        val timestampIso = Instant.ofEpochMilli(timestamp).toString()
        val incidentUuid = UUID.randomUUID().toString()
        val packetUuid = UUID.randomUUID().toString()
        val rawData = "$emergencyType|$latitude|$longitude|$timestamp"
        val encryptedPayload = CryptoManager.encrypt(rawData)
        val packetHash = CryptoManager.computeHash("$packetUuid|$targetId|$timestamp|$encryptedPayload")
        val senderDeviceId = authTokenManager.deviceId.firstOrNull() ?: BuildConfig.APPLICATION_ID

        val activeMode = forcedMode ?: serverConfigManager.transmissionMode.firstOrNull() ?: TransmissionMode.AUTO

        val request = TelemetryUplinkRequest(
            incidentUuid = incidentUuid,
            packetUuid = packetUuid,
            packetHash = packetHash,
            targetId = TargetIdDto(raw = targetId),
            incidentType = emergencyType,
            latitude = latitude,
            longitude = longitude,
            hopCount = 0,
            aesPayload = encryptedPayload,
            senderDeviceId = senderDeviceId,
            timestamp = timestampIso
        )

        val pendingPacket = PendingPacketEntity(
            packetHash = packetHash,
            incidentUuid = incidentUuid,
            packetUuid = packetUuid,
            targetId = targetId,
            emergencyType = emergencyType,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            hopCount = 0,
            encryptedPayload = encryptedPayload
        )
        val localIncident = IncidentEntity(
            incidentId = incidentUuid,
            targetId = targetId,
            emergencyType = emergencyType,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            status = "QUEUED",
            description = "Target agency: ${targetAgencyTypeFor(emergencyType)}",
            packetHash = packetHash,
            channel = if (activeMode == TransmissionMode.FORCE_BLE_MESH || !isOnline) "BLE_MESH" else "ONLINE",
            deliveryPath = if (activeMode == TransmissionMode.FORCE_BLE_MESH || !isOnline) "BLE_CLOUD_SHORTCUT" else "DIRECT_UPLINK",
            senderDeviceId = senderDeviceId,
            isDirty = activeMode == TransmissionMode.FORCE_BLE_MESH || !isOnline
        )

        suspend fun persistOfflineIncident() {
            telemetryRepository.savePendingPacket(pendingPacket)
            incidentRepository.addIncident(localIncident)
        }

        return when (activeMode) {
            TransmissionMode.FORCE_BLE_MESH -> {
                persistOfflineIncident()
                Result.Success(
                    TelemetryUplinkResponse(
                        success = true,
                        message = "Force BLE Relay Mode: Saved & Broadcasting P2P via Bluetooth delay-tolerant relay!"
                    )
                )
            }
            TransmissionMode.FORCE_INTERNET -> {
                sendOnlineAndCache(request, localIncident)
            }
            TransmissionMode.AUTO -> {
                if (isOnline) {
                    val result = sendOnlineAndCache(request, localIncident)
                    if (result is Result.Error) {
                        persistOfflineIncident()
                    }
                    result
                } else {
                    persistOfflineIncident()
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

    private suspend fun sendOnlineAndCache(
        request: TelemetryUplinkRequest,
        localIncident: IncidentEntity
    ): Result<TelemetryUplinkResponse> {
        return when (val result = telemetryRepository.sendUplink(request)) {
            is Result.Success -> {
                val incident = result.data.incident
                if (incident != null) {
                    incidentRepository.cacheIncidents(listOf(incident))
                } else {
                    incidentRepository.addIncident(
                        localIncident.copy(
                            incidentId = result.data.incidentId ?: localIncident.incidentId,
                            status = if (result.data.status.isNotBlank()) result.data.status.uppercase() else "RECEIVED",
                            isDirty = false
                        )
                    )
                }
                result
            }
            else -> result
        }
    }

    private fun targetAgencyTypeFor(emergencyType: String): String {
        return when (emergencyType.uppercase()) {
            "MEDICAL" -> "MEDICAL"
            "FIRE" -> "FIRE"
            "CRIME" -> "POLICE"
            "ACCIDENT" -> "RESCUE"
            "DISASTER" -> "DRRMO"
            else -> "RESCUE"
        }
    }
}
