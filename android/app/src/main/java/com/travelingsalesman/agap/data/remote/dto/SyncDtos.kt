package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncBootstrapDto(
    @SerialName("user") val user: UserDto,
    @SerialName("agencies") val agencies: List<AgencyDto> = emptyList(),
    @SerialName("emergency_types") val emergencyTypes: List<EmergencyTypeDto> = emptyList(),
    @SerialName("predefined_messages") val predefinedMessages: List<PredefinedMessageDto> = emptyList(),
    @SerialName("assigned_dispatches") val assignedDispatches: List<DispatchDto> = emptyList(),
    @SerialName("active_incidents") val activeIncidents: List<IncidentDto> = emptyList(),
    @SerialName("catalog_version") val catalogVersion: String = "1.0.0",
    @SerialName("geography_version") val geographyVersion: String = "1.0.0",
    @SerialName("routing_version") val routingVersion: String = "1.0.0",
    @SerialName("map_version") val mapVersion: String = "1.0.0"
)

@Serializable
data class EmergencyTypeDto(
    @SerialName("id") val id: Long,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("default_priority") val defaultPriority: Int = 3,
    @SerialName("protocol_code") val protocolCode: Int = 0,
    @SerialName("target_agency_type") val targetAgencyType: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SyncPushRequestDto(
    @SerialName("telemetry_packets") val telemetryPackets: List<SyncTelemetryPacketDto> = emptyList(),
    @SerialName("mesh_events") val meshEvents: List<MeshDeliveryEventUploadDto> = emptyList()
)

@Serializable
data class SyncTelemetryPacketDto(
    @SerialName("incident_uuid") val incidentUuid: String? = null,
    @SerialName("packet_uuid") val packetUuid: String,
    @SerialName("packet_hash") val packetHash: String? = null,
    @SerialName("target_id") val targetId: TargetIdDto,
    @SerialName("incident_type") val incidentType: String,
    @SerialName("channel") val channel: String = "BLE_MESH",
    @SerialName("delivery_path") val deliveryPath: String = "BLE_CLOUD_SHORTCUT",
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double = 0.0,
    @SerialName("gps_accuracy") val gpsAccuracy: Double = 0.0,
    @SerialName("hop_count") val hopCount: Int = 0,
    @SerialName("ttl") val ttl: Int = 0,
    @SerialName("aes_payload") val aesPayload: String? = null,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("received_at") val receivedAt: String? = null
)

@Serializable
data class SyncPushResultDto(
    @SerialName("status") val status: String,
    @SerialName("telemetry_accepted") val telemetryAccepted: Int = 0,
    @SerialName("telemetry_duplicate") val telemetryDuplicate: Int = 0,
    @SerialName("mesh_events_recorded") val meshEventsRecorded: Int = 0,
    @SerialName("errors") val errors: List<String> = emptyList()
)

@Serializable
data class SyncChangesDto(
    @SerialName("since") val since: String,
    @SerialName("server_time") val serverTime: String,
    @SerialName("incidents") val incidents: List<IncidentDto> = emptyList(),
    @SerialName("agencies") val agencies: List<AgencyDto> = emptyList(),
    @SerialName("emergency_types") val emergencyTypes: List<EmergencyTypeDto> = emptyList(),
    @SerialName("predefined_messages") val predefinedMessages: List<PredefinedMessageDto> = emptyList()
)
