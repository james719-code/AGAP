package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IncidentDto(
    @SerialName("id") val id: Long,
    @SerialName("incident_uuid") val incidentUuid: String = "",
    @SerialName("packet_uuid") val packetUuid: String = "",
    @SerialName("packet_hash") val packetHash: String = "",
    @SerialName("target_id") val targetId: TargetIdDto? = null,
    @SerialName("incident_type") val incidentType: String,
    @SerialName("channel") val channel: String = "ONLINE",
    @SerialName("delivery_path") val deliveryPath: String = "DIRECT_UPLINK",
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double = 0.0,
    @SerialName("gps_accuracy") val gpsAccuracy: Double = 0.0,
    @SerialName("hop_count") val hopCount: Int = 0,
    @SerialName("ttl") val ttl: Int = 0,
    @SerialName("sender_device_id") val senderDeviceId: String = "",
    @SerialName("status") val status: String,
    @SerialName("event_timestamp") val eventTimestamp: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TargetIdDto(
    @SerialName("raw") val raw: String = "",
    @SerialName("region") val region: String = "",
    @SerialName("province") val province: String = "",
    @SerialName("muni") val muni: String = "",
    @SerialName("barangay") val barangay: String = "",
    @SerialName("uuid") val uuid: String = ""
)

@Serializable
data class IncidentListResponseDto(
    @SerialName("count") val count: Int,
    @SerialName("limit") val limit: Int = 0,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("incidents") val incidents: List<IncidentDto> = emptyList()
)

@Serializable
data class UpdateIncidentStatusRequestDto(
    @SerialName("status") val status: String
)

@Serializable
data class UpdateDispatchStatusRequestDto(
    @SerialName("status") val status: String
)

@Serializable
data class UpdateIncidentStatusResponseDto(
    @SerialName("status") val status: String,
    @SerialName("incident_id") val incidentId: Long,
    @SerialName("new_status") val newStatus: String
)

@Serializable
data class UpdateDispatchStatusResponseDto(
    @SerialName("status") val status: String,
    @SerialName("dispatch_id") val dispatchId: Long,
    @SerialName("new_status") val newStatus: String
)

@Serializable
data class CreateDispatchRequestDto(
    @SerialName("unit_id") val unitId: Long
)

@Serializable
data class DispatchDto(
    @SerialName("id") val id: Long,
    @SerialName("incident_id") val incidentId: Long,
    @SerialName("unit_id") val unitId: Long,
    @SerialName("status") val status: String,
    @SerialName("dispatched_at") val dispatchedAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("departed_at") val departedAt: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("unit_callsign") val unitCallsign: String? = null,
    @SerialName("agency_name") val agencyName: String? = null,
    @SerialName("responder_name") val responderName: String? = null
)
