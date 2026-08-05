package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryUplinkRequest(
    @SerialName("packet_hash") val packetHash: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("emergency_type") val emergencyType: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("hop_count") val hopCount: Int,
    @SerialName("encrypted_payload") val encryptedPayload: String
)

@Serializable
data class TelemetryUplinkResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("incident_id") val incidentId: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("deduplicated") val deduplicated: Boolean = false
)

@Serializable
data class WebSocketEventDto(
    @SerialName("event_type") val eventType: String, // "INCIDENT_BROADCAST", "DISPATCH_CONFIRMED"
    @SerialName("incident_id") val incidentId: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("emergency_type") val emergencyType: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("status") val status: String
)
