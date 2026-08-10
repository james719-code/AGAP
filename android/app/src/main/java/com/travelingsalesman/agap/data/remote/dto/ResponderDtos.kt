package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponderUnitDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("agency_id") val agencyId: Long,
    @SerialName("target_id") val targetId: String = "",
    @SerialName("unit_callsign") val unitCallsign: String,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("status") val status: String = "IDLE",
    @SerialName("current_lat") val currentLat: Double = 0.0,
    @SerialName("current_lon") val currentLon: Double = 0.0,
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

/**
 * Unit edits are intentionally partial. Sending ResponderUnitDto for an edit
 * serializes default device/GPS fields and previously overwrote live responder
 * state when an admin changed only callsign/status.
 */
@Serializable
data class UpdateResponderUnitRequestDto(
    @SerialName("agency_id") val agencyId: Long,
    @SerialName("unit_callsign") val unitCallsign: String,
    @SerialName("status") val status: String,
    @SerialName("target_id") val targetId: String
)

@Serializable
data class UpdateResponderLocationDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("status") val status: String
)

@Serializable
data class UpdateResponderLocationResponseDto(
    @SerialName("status") val status: String = "success",
    @SerialName("responder_id") val responderId: Long = 0L
)

fun normalizeResponderStatusValue(status: String?): String {
    val normalized = status?.trim()?.uppercase().orEmpty()
    return when (normalized) {
        "DISPATCHED" -> "DISPATCHED"
        "OFFLINE" -> "OFFLINE"
        "IDLE", "", "NONE", "NULL" -> "IDLE"
        else -> "IDLE"
    }
}

fun ResponderUnitDto.withNormalizedStatus(): ResponderUnitDto =
    copy(status = normalizeResponderStatusValue(status))
