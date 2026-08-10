package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.remote.AuthApiService
import com.travelingsalesman.agap.data.remote.dto.LoginRequestDto
import com.travelingsalesman.agap.data.remote.dto.LoginResponseDto
import com.travelingsalesman.agap.data.remote.dto.RegisterRequestDto
import com.travelingsalesman.agap.data.remote.dto.RegisterResponseDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    suspend fun getUserById(id: Long): Result<UserDto>
    suspend fun verifyUser(id: Long): Result<Boolean>
    suspend fun updateUserStatus(id: Long, status: String, remarks: String? = null): Result<Boolean>
    suspend fun updateUserByID(id: Long, name: String?, email: String?, phone: String?, medicalInfo: String?): Result<UserDto>
    suspend fun deleteUser(id: Long): Result<Boolean>
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val authTokenManager: AuthTokenManager,
    private val serverConfigManager: ServerConfigManager,
    private val userProfileRepository: UserProfileRepository
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
                userProfileRepository.saveUserSnapshot(body.user)
                Result.Success(body)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                timber.log.Timber.e("AuthRepository: Login FAILED HTTP %d %s - %s", response.code(), response.message(), errorBody)
                Result.Error(UserMessageResolver.http("sign in", response.code(), errorBody))
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AuthRepository: Login Network EXCEPTION for user='%s': %s", username, e.localizedMessage)
            Result.Error(UserMessageResolver.network("Sign in", e))
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
                Result.Error(UserMessageResolver.http("create your account", response.code(), errorBody))
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AuthRepository: Registration Network EXCEPTION for user='%s': %s", username, e.localizedMessage)
            Result.Error(UserMessageResolver.network("Create your account", e))
        }
    }

    override suspend fun getProfile(): Result<UserDto> {
        return try {
            val response = authApiService.getProfile()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                authTokenManager.saveUserProfile(user)
                userProfileRepository.saveUserSnapshot(user)
                Result.Success(user)
            } else {
                Result.Error(UserMessageResolver.http("refresh your profile", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Refresh your profile", e))
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
                userProfileRepository.saveUserSnapshot(updated)
                Result.Success(updated)
            } else {
                Result.Error(UserMessageResolver.http("update your profile", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update your profile", e))
        }
    }

    override suspend fun listUserDevices(): Result<List<UserDeviceDto>> {
        return try {
            val response = authApiService.listUserDevices()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(UserMessageResolver.http("load your devices", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load your devices", e))
        }
    }

    override suspend fun addUserDevice(deviceHardwareId: String, deviceName: String, platform: String): Result<UserDeviceDto> {
        return try {
            val response = authApiService.addUserDevice(AddUserDeviceRequest(deviceHardwareId, deviceName, platform))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(UserMessageResolver.http("add this device", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Add this device", e))
        }
    }

    override suspend fun deleteUserDevice(deviceId: Long): Result<Unit> {
        return try {
            val response = authApiService.deleteUserDevice(deviceId)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(UserMessageResolver.http("remove this device", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Remove this device", e))
        }
    }

    override suspend fun listEmergencyContacts(): Result<List<EmergencyContactDto>> {
        return try {
            val response = authApiService.listEmergencyContacts()
            if (response.isSuccessful && response.body() != null) {
                val contacts = response.body()!!
                authTokenManager.userId.firstOrNull()?.let { userId ->
                    userProfileRepository.replaceEmergencyContacts(userId, contacts)
                }
                Result.Success(contacts)
            } else {
                Result.Error(UserMessageResolver.http("load emergency contacts", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load emergency contacts", e))
        }
    }

    override suspend fun addEmergencyContact(name: String, phone: String, relation: String, isPrimary: Boolean): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.addEmergencyContact(AddEmergencyContactRequest(name, phone, relation, isPrimary))
            if (response.isSuccessful && response.body() != null) {
                val contact = response.body()!!
                authTokenManager.userId.firstOrNull()?.let { userProfileRepository.saveEmergencyContact(contact.toEntity(it)) }
                Result.Success(contact)
            } else {
                Result.Error(UserMessageResolver.http("add the emergency contact", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Add the emergency contact", e))
        }
    }

    override suspend fun updateEmergencyContact(contactId: Long, name: String, phone: String, relation: String, isPrimary: Boolean?): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.updateEmergencyContact(contactId, AddEmergencyContactRequest(name, phone, relation, isPrimary))
            if (response.isSuccessful && response.body() != null) {
                val contact = response.body()!!
                authTokenManager.userId.firstOrNull()?.let { userProfileRepository.saveEmergencyContact(contact.toEntity(it)) }
                Result.Success(contact)
            } else {
                Result.Error(UserMessageResolver.http("update the emergency contact", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update the emergency contact", e))
        }
    }

    override suspend fun setPrimaryEmergencyContact(contactId: Long): Result<EmergencyContactDto> {
        return try {
            val response = authApiService.setPrimaryEmergencyContact(contactId)
            if (response.isSuccessful && response.body() != null) {
                val contact = response.body()!!
                authTokenManager.userId.firstOrNull()?.let { userProfileRepository.saveEmergencyContact(contact.toEntity(it)) }
                Result.Success(contact)
            } else {
                Result.Error(UserMessageResolver.http("set the primary contact", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Set the primary contact", e))
        }
    }

    override suspend fun deleteEmergencyContact(contactId: Long): Result<Unit> {
        return try {
            val response = authApiService.deleteEmergencyContact(contactId)
            if (response.isSuccessful) {
                authTokenManager.userId.firstOrNull()?.let { userProfileRepository.deleteEmergencyContact(it, contactId) }
                Result.Success(Unit)
            } else {
                Result.Error(UserMessageResolver.http("remove the emergency contact", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Remove the emergency contact", e))
        }
    }

    override suspend fun listUsers(): Result<List<UserDto>> {
        return try {
            val response = authApiService.listUsers()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(UserMessageResolver.http("load users", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load users", e))
        }
    }

    override suspend fun getUserById(id: Long): Result<UserDto> {
        return try {
            val response = authApiService.getUserById(id)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(UserMessageResolver.http("load user details", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load user details", e))
        }
    }

    override suspend fun verifyUser(id: Long): Result<Boolean> {
        return try {
            val response = authApiService.verifyUser(id)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error(UserMessageResolver.http("verify the user", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Verify the user", e))
        }
    }

    override suspend fun updateUserStatus(id: Long, status: String, remarks: String?): Result<Boolean> {
        return try {
            val response = authApiService.updateUserStatus(id, UpdateUserStatusRequestDto(status, remarks))
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error(UserMessageResolver.http("update user status", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update user status", e))
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
                Result.Error(UserMessageResolver.http("update the user", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update the user", e))
        }
    }

    override suspend fun deleteUser(id: Long): Result<Boolean> {
        return try {
            val response = authApiService.deleteUser(id)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error(UserMessageResolver.http("delete the user", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Delete the user", e))
        }
    }

    override suspend fun logout() {
        authTokenManager.userId.firstOrNull()?.let { userProfileRepository.clearUser(it) }
        // Login requires the network. Never leave the app stranded behind the
        // FORCE_BLE_MESH network gate after the authenticated session ends.
        serverConfigManager.resetBleOnlyToAuto(reason = "logout")
        authTokenManager.clearAuthSession()
    }

    private fun EmergencyContactDto.toEntity(userId: Long) =
        com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity(
            contactId = id,
            userId = userId,
            name = contactName,
            phone = phoneNumber,
            relationship = relationship,
            isPrimary = isPrimary
        )
}
