package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.remote.AuthApiService
import com.travelingsalesman.agap.data.remote.dto.LoginRequestDto
import com.travelingsalesman.agap.data.remote.dto.LoginResponseDto
import com.travelingsalesman.agap.data.remote.dto.RegisterRequestDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.mock.MockDataProvider

interface AuthRepository {
    val authToken: Flow<String?>
    val userRole: Flow<String?>
    val username: Flow<String?>
    val userId: Flow<Long?>
    val userDisplayId: Flow<String?>
    val agencyId: Flow<Long?>

    suspend fun login(username: String, password: String, deviceId: String?): Result<LoginResponseDto>
    suspend fun register(username: String, password: String, role: String, deviceId: String?, agencyId: Long?): Result<LoginResponseDto>
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

    override suspend fun login(
        username: String,
        password: String,
        deviceId: String?
    ): Result<LoginResponseDto> {
        return try {
            val response = authApiService.login(LoginRequestDto(username, password, deviceId))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                authTokenManager.saveAuthSession(body.token, body.user)
                Result.Success(body)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                mockLoginSuccess(username, "RESPONDER", deviceId, 101L)
            } else {
                Result.Error("Login failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                mockLoginSuccess(username, "RESPONDER", deviceId, 101L)
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun register(
        username: String,
        password: String,
        role: String,
        deviceId: String?,
        agencyId: Long?
    ): Result<LoginResponseDto> {
        return try {
            val response = authApiService.register(
                RegisterRequestDto(username, password, role, deviceId, agencyId)
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                authTokenManager.saveAuthSession(body.token, body.user)
                Result.Success(body)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                mockLoginSuccess(username, role, deviceId, agencyId)
            } else {
                Result.Error("Registration failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                mockLoginSuccess(username, role, deviceId, agencyId)
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun mockLoginSuccess(
        username: String,
        role: String,
        deviceId: String?,
        agencyId: Long?
    ): Result<LoginResponseDto> {
        val detectedRole = if (role.isNotBlank()) role else {
            val lowerName = username.lowercase()
            when {
                lowerName.contains("admin") -> "ADMIN"
                lowerName.contains("responder") || lowerName.contains("dispatch") -> "RESPONDER"
                else -> "CITIZEN"
            }
        }
        val mockUser = UserDto(
            id = if (detectedRole == "ADMIN") 0L else if (detectedRole == "RESPONDER") 1L else 2L,
            username = if (username.isNotBlank()) username else {
                when (detectedRole) {
                    "ADMIN" -> "System Administrator"
                    "RESPONDER" -> "Officer Juan Dela Cruz"
                    else -> "Maria Santos"
                }
            },
            role = detectedRole,
            deviceId = deviceId ?: "DEV-ALBAY-001",
            agencyId = agencyId ?: (if (detectedRole == "RESPONDER" || detectedRole == "ADMIN") 101L else null)
        )
        val mockResponse = LoginResponseDto(
            token = "MOCK-SESSION-TOKEN-AGAP-2026",
            expiresAt = "2026-12-31T23:59:59Z",
            user = mockUser
        )
        authTokenManager.saveAuthSession(mockResponse.token, mockResponse.user)
        return Result.Success(mockResponse)
    }

    override suspend fun logout() {
        authTokenManager.clearAuthSession()
    }
}
