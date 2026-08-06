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
        agencyId: Long?
    ): Result<LoginResponseDto> {
        timber.log.Timber.i("AuthRepository: Attempting registration for username='%s', role='%s'", username, role)
        return try {
            val response = authApiService.register(
                RegisterRequestDto(username, password, role, deviceId, agencyId)
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                timber.log.Timber.i("AuthRepository: Registration SUCCESS for username='%s'", username)
                authTokenManager.saveAuthSession(body.token, body.user)
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

    override suspend fun logout() {
        authTokenManager.clearAuthSession()
    }
}
