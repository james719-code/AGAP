package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class TelemetryUplinkRequest(
    @SerialName("incident_uuid") val incidentUuid: String,
    @SerialName("packet_uuid") val packetUuid: String,
    @SerialName("packet_hash") val packetHash: String,
    @SerialName("target_id") val targetId: TargetIdDto,
    @SerialName("incident_type") val incidentType: String,
    @SerialName("channel") val channel: String = "ONLINE",
    @SerialName("delivery_path") val deliveryPath: String = "DIRECT_UPLINK",
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double = 0.0,
    @SerialName("gps_accuracy") val gpsAccuracy: Double = 0.0,
    @SerialName("hop_count") val hopCount: Int,
    @SerialName("ttl") val ttl: Int = 10,
    @SerialName("aes_payload") val aesPayload: String,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("timestamp") val timestamp: String
)

@Serializable
data class TelemetryUplinkResponse(
    @SerialName("success") val success: Boolean = true,
    @SerialName("status") val status: String = "",
    @SerialName("deduplicated") val deduplicated: Boolean = false,
    @SerialName("incident_id") val incidentId: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("incident") val incident: IncidentDto? = null
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

@Serializable
data class WebSocketEnvelopeDto(
    @SerialName("type") val type: String,
    @SerialName("payload") val payload: JsonElement = JsonObject(emptyMap())
)

@Serializable
data class LocationUpdatePayload(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double = 0.0,
    @SerialName("gps_accuracy") val gpsAccuracy: Double = 0.0
)

@Serializable
data class LocationUpdateResponse(
    @SerialName("status") val status: String = "success",
    @SerialName("incident_id") val incidentId: Long? = null,
    @SerialName("message") val message: String? = null
)

