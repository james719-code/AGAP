package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.remote.AuthApiService
import com.travelingsalesman.agap.data.remote.dto.LoginRequestDto
import com.travelingsalesman.agap.data.remote.dto.LoginResponseDto
import com.travelingsalesman.agap.data.remote.dto.RegisterRequestDto
import com.travelingsalesman.agap.data.remote.dto.RegisterResponseDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.mock.MockDataProvider

import com.travelingsalesman.agap.data.remote.dto.AddEmergencyContactRequest
import com.travelingsalesman.agap.data.remote.dto.AddUserDeviceRequest
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.remote.dto.UpdateProfileRequestDto
import com.travelingsalesman.agap.data.remote.dto.UpdateUserStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.UserDeviceDto

interface AuthRepository {
    val authToken: Flow<String?>
    val userRole: Flow<String?>
    val username: Flow<String?>
    val userId: Flow<Long?>
    val userDisplayId: Flow<String?>
    val agencyId: Flow<Long?>
    val userFullName: Flow<String?>
    val userEmail: Flow<String?>
    val userPhone: Flow<String?>
    val userMedicalInfo: Flow<String?>
    val userNationalId: Flow<String?>
    val userIsVerified: Flow<Boolean>

    suspend fun login(username: String, password: String, deviceId: String?): Result<LoginResponseDto>
    suspend fun register(username: String, password: String, role: String, deviceId: String?, agencyId: Long?, rescueUnitId: Long? = null, name: String? = null, phoneNumber: String? = null): Result<RegisterResponseDto>
    suspend fun getProfile(): Result<UserDto>
    suspend fun updateProfile(name: String?, email: String?, phone: String?, medicalInfo: String?): Result<UserDto>
    suspend fun listUserDevices(): Result<List<UserDeviceDto>>
    suspend fun addUserDevice(deviceHardwareId: String, deviceName: String, platform: String): Result<UserDeviceDto>
    suspend fun deleteUserDevice(deviceId: Long): Result<Unit>
    suspend fun listEmergencyContacts(): Result<List<EmergencyContactDto>>
    suspend fun addEmergencyContact(name: String, phone: String, relation: String, isPrimary: Boolean = false): Result<EmergencyContactDto>
    suspend fun updateEmergencyContact(contactId: Long, name: String, phone: String, relation: String, isPrimary: Boolean? = null): Result<EmergencyContactDto>
    suspend fun setPrimaryEmergencyContact(contactId: Long): Result<EmergencyContactDto>
    suspend fun deleteEmergencyContact(contactId: Long): Result<Unit>
    suspend fun listUsers(): Result<List<UserDto>>
    suspend fun verifyUser(id: Long): Result<Boolean>
    suspend fun updateUserStatus(id: Long, status: String, remarks: String? = null): Result<Boolean>
    suspend fun updateUserByID(id: Long, name: String?, email: String?, phone: String?, medicalInfo: String?): Result<UserDto>
    suspend fun deleteUser(id: Long): Result<Boolean>
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val authTokenManager: AuthTokenManager
) : AuthRepository {

    override val authToken: Flow<String?> = authTokenManager.authToken
    override val userRole: Flow<String?> = authTokenManager.userRole
    override val username: Flow<String?> = authTokenManager.username
    override val userId: Flow<Long?> = authTokenManager.userId
    override val userDisplayId: Flow<String?> = authTokenManager.userDisplayId
    override val agencyId: Flow<Long?> = authTokenManager.agencyId
    override val userFullName: Flow<String?> = authTokenManager.userFullName
    override val userEmail: Flow<String?> = authTokenManager.userEmail
    override val userPhone: Flow<String?> = authTokenManager.userPhone
    override val userMedicalInfo: Flow<String?> = authTokenManager.userMedicalInfo
    override val userNationalId: Flow<String?> = authTokenManager.userNationalId
    override val userIsVerified: Flow<Boolean> = authTokenManager.userIsVerified

    override suspend fun login(
        username: String,
        password: String,
        deviceId: String?
    ): Result<LoginResponseDto> {
        timber.log.Timber.i("AuthRepository: Attempting login for username='%s'", username)
        return try {
            val response = authApiService.login(LoginRequestDto(username, password, deviceId))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                timber.log.Timber.i("AuthRepository: Login SUCCESS for username='%s', role='%s'", username, body.user.role)
                authTokenManager.saveAuthSession(body.token, body.user)
                Result.Success(body)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                timber.log.Timber.e("AuthRepository: Login FAILED HTTP %d %s - %s", response.code(), response.message(), errorBody)
                Result.Error("Login failed (HTTP ${response.code()}): ${if (errorBody.isNotBlank()) errorBody else response.message()}")
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AuthRepository: Login Network EXCEPTION for user='%s': %s", username, e.localizedMessage)
            val detailedMsg = when (e) {
                is java.net.SocketTimeoutException -> "Network Timeout (15s exceeded). Server took too long to respond."
                is java.net.UnknownHostException -> "Unknown Host. Unable to resolve server address."
                is java.net.ConnectException -> "Connection Refused. Server is unreachable or offline."
                is javax.net.ssl.SSLException -> "SSL/TLS Security Error: ${e.localizedMessage}"
                else -> "Network Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
            Result.Error(detailedMsg)
        }
    }

    override suspend fun register(
        username: String,
        password: String,
        role: String,
        deviceId: String?,
        agencyId: Long?,
        rescueUnitId: Long?,
        name: String?,
        phoneNumber: String?
    ): Result<RegisterResponseDto> {
        timber.log.Timber.i("AuthRepository: Attempting registration for username='%s', role='%s'", username, role)
        return try {
            val response = authApiService.register(
                RegisterRequestDto(
                    username = username,
                    password = password,
                    role = role,
                    deviceId = deviceId,
                    agencyId = agencyId,
                    rescueUnitId = rescueUnitId,
                    name = name,
                    phoneNumber = phoneNumber
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                timber.log.Timber.i("AuthRepository: Registration SUCCESS for username='%s'", username)
                Result.Success(body)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                timber.log.Timber.e("AuthRepository: Registration FAILED HTTP %d %s - %s", response.code(), response.message(), errorBody)
                Result.Error("Registration failed (HTTP ${response.code()}): ${if (errorBody.isNotBlank()) errorBody else response.message()}")
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AuthRepository: Registration Network EXCEPTION for user='%s': %s", username, e.localizedMessage)
            val detailedMsg = when (e) {
                is java.net.SocketTimeoutException -> "Network Timeout (15s exceeded)."
                is java.net.UnknownHostException -> "Unknown Host. Unable to resolve server address."
                is java.net.ConnectException -> "Connection Refused by server."
                else -> "Network Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
            Result.Error(detailedMsg)
        }
    }

    override suspend fun getProfile(): Result<UserDto> {
        return try {
            val response = authApiService.getProfile()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                authTokenManager.saveUserProfile(user)
                Result.Success(user)
            } else {
                Result.Error("Failed to fetch profile (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error fetching profile: ${e.localizedMessage}")
        }
    }

    override suspend fun updateProfile(name: String?, email: String?, phone: String?, medicalInfo: String?): Result<UserDto> {
        return try {
            val req = UpdateProfileRequestDto(
                name = name,
                email = email,
                phoneNumber = phone,
                medicalInfo = medicalInfo
            )
            val response = authApiService.updateProfile(req)
            if (response.isSuccessful && response.body() != null) {
                val updated = response.body()!!
                authTokenManager.saveUserProfile(updated)
                Result.Success(updated)
            } else {
                Result.Error("Failed to update profile (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error updating profile: ${e.localizedMessage}")
        }
    }

    override suspend fun listUserDevices(): Result<List<UserDeviceDto>> {
        return try {
            val response = authApiService.listUserDevices()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to fetch devices (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error fetching devices: ${e.localizedMessage}")
        }
    }

    override suspend fun addUserDevice(deviceHardwareId: String, deviceName: String, platform: String): Result<UserDeviceDto> {
        return try {
            val response = authApiService.addUserDevice(AddUserDeviceRequest(deviceHardwareId, deviceName, platform))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to add device (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error adding device: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteUserDevice(deviceId: Long): Result<Unit> {
        return try {
            val response = authApiService.deleteUserDevice(deviceId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to delete device (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error deleting device: ${e.localizedMessage}")
        }
    }

    override suspend fun listEmergencyContacts(): Result<List<EmergencyContactDto>> {
        return try {
            val response = authApiService.listEmergencyContacts()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to fetch contacts (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error fetching contacts: ${e.localizedMessage}")
        }
    }

    override suspend fun addEmergencyContact(name: String, phone: String, relation: String, isPrimary: Boolean): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.addEmergencyContact(AddEmergencyContactRequest(name, phone, relation, isPrimary))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to add contact (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error adding contact: ${e.localizedMessage}")
        }
    }

    override suspend fun updateEmergencyContact(contactId: Long, name: String, phone: String, relation: String, isPrimary: Boolean?): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.updateEmergencyContact(contactId, AddEmergencyContactRequest(name, phone, relation, isPrimary))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to update contact (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error updating contact: ${e.localizedMessage}")
        }
    }

    override suspend fun setPrimaryEmergencyContact(contactId: Long): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.setPrimaryEmergencyContact(contactId)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to set primary contact (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error setting primary contact: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteEmergencyContact(contactId: Long): Result<Unit> {
        return try {
            val response = authApiService.deleteEmergencyContact(contactId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to delete contact (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error deleting contact: ${e.localizedMessage}")
        }
    }

    override suspend fun listUsers(): Result<List<UserDto>> {
        return try {
            val response = authApiService.listUsers()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to list users (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error listing users: ${e.localizedMessage}")
        }
    }

    override suspend fun verifyUser(id: Long): Result<Boolean> {
        return try {
            val response = authApiService.verifyUser(id)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to verify user (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error verifying user: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserStatus(id: Long, status: String, remarks: String?): Result<Boolean> {
        return try {
            val response = authApiService.updateUserStatus(id, UpdateUserStatusRequestDto(status, remarks))
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to update user status (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error updating user status: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserByID(
        id: Long,
        name: String?,
        email: String?,
        phone: String?,
        medicalInfo: String?
    ): Result<UserDto> {
        return try {
            val response = authApiService.updateUserByID(
                id,
                UpdateProfileRequestDto(
                    name = name,
                    email = email,
                    phoneNumber = phone,
                    medicalInfo = medicalInfo
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to update user (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error updating user: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteUser(id: Long): Result<Boolean> {
        return try {
            val response = authApiService.deleteUser(id)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to delete user (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Result.Error("Network error deleting user: ${e.localizedMessage}")
        }
    }

    override suspend fun logout() {
        authTokenManager.clearAuthSession()
    }
}

