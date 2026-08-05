package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponderUnitDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("agency_id") val agencyId: Long,
    @SerialName("target_id") val targetId: String = "",
    @SerialName("unit_callsign") val unitCallsign: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("status") val status: String = "IDLE",
    @SerialName("current_lat") val currentLat: Double = 0.0,
    @SerialName("current_lon") val currentLon: Double = 0.0,
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

@Serializable
data class UpdateResponderLocationDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("status") val status: String
)
