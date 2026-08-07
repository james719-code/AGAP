package com.travelingsalesman.agap.domain.usecase

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.RegisterResponseDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        role: String = "CITIZEN",
        deviceId: String? = null,
        agencyId: Long? = null,
        name: String? = null,
        phoneNumber: String? = null
    ): Result<RegisterResponseDto> {
        if (username.isBlank() || password.isBlank()) {
            return Result.Error("Username and password are required")
        }
        return authRepository.register(
            username = username,
            password = password,
            role = role,
            deviceId = deviceId,
            agencyId = agencyId,
            name = name,
            phoneNumber = phoneNumber
        )
    }
}
