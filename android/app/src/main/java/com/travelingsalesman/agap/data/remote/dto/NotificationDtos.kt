package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("id") val id: Long,
    @SerialName("user_id") val userId: Long,
    // The deployed backend emits payload/sent_at/incident_id. Keep the older
    // Android title/message/reference_id/created_at format readable too so a
    // mixed-version rollout never drops a whole notification response.
    @SerialName("title") private val apiTitle: String? = null,
    @SerialName("message") private val apiMessage: String? = null,
    @SerialName("payload") private val payload: String? = null,
    @SerialName("type") val type: String = "ALERT", // DISPATCH, STATUS_CHANGE, ALERT
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("reference_id") private val apiReferenceId: String? = null,
    @SerialName("incident_id") private val incidentId: Long? = null,
    @SerialName("created_at") private val apiCreatedAt: String? = null,
    @SerialName("sent_at") private val sentAt: String? = null
) {
    val title: String
        get() = apiTitle.cleanOrNull() ?: defaultTitle(type)

    val message: String
        get() = apiMessage.cleanOrNull()
            ?: payload.cleanOrNull()
            ?: "Operational update"

    /** The notification screen navigates by incident ID when one is available. */
    val referenceId: String?
        get() = apiReferenceId.cleanOrNull() ?: incidentId?.toString()

    /** Supports both the legacy Android and current backend timestamp fields. */
    val createdAt: String
        get() = apiCreatedAt.cleanOrNull() ?: sentAt.cleanOrNull().orEmpty()

    private fun defaultTitle(rawType: String): String = when (rawType.trim().uppercase()) {
        "DISPATCH", "DISPATCH_ASSIGNMENT" -> "New dispatch assignment"
        "UNIT_DISPATCHED" -> "Responder dispatched"
        "STATUS_CHANGE", "INCIDENT_STATUS_UPDATE", "INCIDENT_STATUS_UPDATED" -> "Incident status updated"
        "MESSAGE", "MESSAGE_RECEIVED" -> "New operational message"
        else -> "AGAP update"
    }
}

private fun String?.cleanOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

@Serializable
data class MarkReadResponseDto(
    // Current backend returns only {"message":"marked as read"}; Retrofit
    // already guarantees this DTO is used for a successful HTTP response.
    @SerialName("success") val success: Boolean = true,
    @SerialName("message") val message: String? = null
)
