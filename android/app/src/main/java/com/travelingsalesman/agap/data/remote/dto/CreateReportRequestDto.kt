package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateReportRequestDto(
    @SerialName("report_uuid") val reportUuid: String,
    @SerialName("transport_token") val transportToken: String,
    @SerialName("submission_type") val submissionType: String = "DETAILED",
    @SerialName("subject_relation") val subjectRelation: String = "SELF",
    @SerialName("emergency_type") val emergencyType: String,
    @SerialName("subtype") val subtype: String = "GENERAL",
    @SerialName("description") val description: String = "",
    @SerialName("people_affected") val peopleAffected: Int = 1,
    @SerialName("immediate_danger") val immediateDanger: Boolean = false,
    @SerialName("location_source") val locationSource: String = "GPS",
    @SerialName("selected_barangay_code") val selectedBarangayCode: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("gps_accuracy_meters") val gpsAccuracyMeters: Float? = null,
    @SerialName("gateway_device_id") val gatewayDeviceId: String? = null,
    @SerialName("origin_channel") val originChannel: String = "CELLULAR"
)

@Serializable
data class CreateReportResponseDto(
    val success: Boolean,
    val message: String? = null,
    val report: CreatedReportDto? = null
)

@Serializable
data class CreatedReportDto(
    @SerialName("report_uuid") val reportUuid: String = "",
    @SerialName("transport_token") val transportToken: String = "",
    @SerialName("incident_id") val incidentId: Long? = null
)
