package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.LoginRequestDto
import com.travelingsalesman.agap.data.remote.dto.LoginResponseDto
import com.travelingsalesman.agap.data.remote.dto.RegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<LoginResponseDto>
}
