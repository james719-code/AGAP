package com.travelingsalesman.agap.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class RegisterRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("role") val role: String = "CITIZEN",
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("agency_id") val agencyId: Long? = null
)

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("role") val role: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("agency_id") val agencyId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val displayId: String
        get() = com.travelingsalesman.agap.core.util.UserIdFormatter.formatUserId(role, id)
}

@Serializable
data class LoginResponseDto(
    @SerialName("token") val token: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("user") val user: UserDto
)
