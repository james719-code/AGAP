package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PredefinedMessageDto(
    @SerialName("id") val id: Int,
    @SerialName("code") val code: String,
    @SerialName("message_en") val messageEn: String,
    @SerialName("message_fil") val messageFil: String? = null,
    @SerialName("message_bikol") val messageBikol: String? = null,
    @SerialName("message_type") val messageType: String = "ACK",
    @SerialName("emergency_type_id") val emergencyTypeId: Long? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class MeshDeliveryEventsBatchRequestDto(
    @SerialName("events") val events: List<MeshDeliveryEventUploadDto>
)

@Serializable
data class MeshDeliveryEventUploadDto(
    @SerialName("packet_uuid") val packetUuid: String,
    @SerialName("packet_type") val packetType: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("device_identifier") val deviceIdentifier: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("recorded_at") val recordedAt: String
)

@Serializable
data class MeshDeliveryEventsBatchResponseDto(
    @SerialName("status") val status: String,
    @SerialName("recorded") val recorded: Int
)
