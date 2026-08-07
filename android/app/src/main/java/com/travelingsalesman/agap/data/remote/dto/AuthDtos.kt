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
    @SerialName("agency_id") val agencyId: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null
)

@Serializable
data class EmergencyContactDto(
    @SerialName("id") val id: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("contact_name") val contactName: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("relationship") val relationship: String = "",
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserDeviceDto(
    @SerialName("id") val id: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("device_hardware_id") val deviceHardwareId: String,
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("platform") val platform: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AddUserDeviceRequest(
    @SerialName("device_hardware_id") val deviceHardwareId: String,
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("platform") val platform: String = ""
)

@Serializable
data class AddEmergencyContactRequest(
    @SerialName("contact_name") val contactName: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("relationship") val relationship: String = "",
    @SerialName("is_primary") val isPrimary: Boolean? = null
)

@Serializable
data class UpdateProfileRequestDto(
    @SerialName("name") val name: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("medical_info") val medicalInfo: String? = null,
    @SerialName("national_id") val nationalId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class UpdateUserStatusRequestDto(
    @SerialName("status") val status: String,
    @SerialName("remarks") val remarks: String? = null
)

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("role") val role: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("agency_id") val agencyId: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("medical_info") val medicalInfo: String? = null,
    @SerialName("national_id") val nationalId: String? = null,
    @SerialName("account_status") val accountStatus: String = "PENDING",
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("address_id") val addressId: Long? = null,
    @SerialName("devices") val devices: List<UserDeviceDto> = emptyList(),
    @SerialName("emergency_contacts") val emergencyContacts: List<EmergencyContactDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val displayId: String
        get() = com.travelingsalesman.agap.core.util.UserIdFormatter.formatUserId(role, id)
}

@Serializable
data class LoginResponseDto(
    @SerialName("token") val token: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("user") val user: UserDto
)

@Serializable
data class RegisterResponseDto(
    @SerialName("message") val message: String,
    @SerialName("user") val user: UserDto
)
