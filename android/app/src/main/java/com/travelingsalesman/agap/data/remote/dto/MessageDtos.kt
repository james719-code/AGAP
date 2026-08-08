package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IncidentMessageDto(
    @SerialName("id") val id: Long,
    @SerialName("incident_id") val incidentId: Long,
    @SerialName("sender_id") val senderId: Long,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("message_content") val messageContent: String,
    @SerialName("message_type") val messageType: String = "CHAT", // CHAT, STATUS_UPDATE, SYSTEM
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SendMessageRequestDto(
    @SerialName("message_content") val messageContent: String,
    @SerialName("message_type") val messageType: String = "CHAT"
)
