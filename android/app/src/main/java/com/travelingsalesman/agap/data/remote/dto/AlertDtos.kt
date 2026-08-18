package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AlertCategory {
    @SerialName("WEATHER") WEATHER,
    @SerialName("EVACUATION") EVACUATION,
    @SerialName("SAFETY") SAFETY,
    @SerialName("HEALTH") HEALTH,
    @SerialName("INFRASTRUCTURE") INFRASTRUCTURE,
    @SerialName("GENERAL") GENERAL
}

@Serializable
enum class AlertSeverity {
    @SerialName("INFO") INFO,
    @SerialName("WARNING") WARNING,
    @SerialName("CRITICAL") CRITICAL,
    @SerialName("EMERGENCY") EMERGENCY
}

@Serializable
data class AdminAlertDto(
    @SerialName("id") val id: Long,
    @SerialName("uuid") val uuid: String,
    @SerialName("author_id") val authorId: Long,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("predefined_message_id") val predefinedMessageId: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("category") val category: AlertCategory,
    @SerialName("severity") val severity: AlertSeverity,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_count") val readCount: Int = 0,
    @SerialName("is_read") val isRead: Boolean = false
)

@Serializable
data class CreateAlertRequestDto(
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("category") val category: String,
    @SerialName("severity") val severity: String,
    @SerialName("predefined_message_id") val predefinedMessageId: Long? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("target_region_code") val targetRegionCode: String? = null,
    @SerialName("target_province_code") val targetProvinceCode: String? = null,
    @SerialName("target_muni_code") val targetMuniCode: String? = null,
    @SerialName("target_barangay_code") val targetBarangayCode: String? = null
)

@Serializable
data class AlertListResponseDto(
    @SerialName("alerts") val alerts: List<AdminAlertDto>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)
