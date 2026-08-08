package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("id") val id: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("title") val title: String,
    @SerialName("message") val message: String,
    @SerialName("type") val type: String, // DISPATCH, STATUS_CHANGE, ALERT
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("reference_id") val referenceId: String? = null, // e.g. incidentId
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class MarkReadResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String? = null
)
