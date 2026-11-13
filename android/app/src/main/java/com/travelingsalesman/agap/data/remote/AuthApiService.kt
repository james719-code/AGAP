package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.AddEmergencyContactRequest
import com.travelingsalesman.agap.data.remote.dto.AddUserDeviceRequest
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.remote.dto.LoginRequestDto
import com.travelingsalesman.agap.data.remote.dto.LoginResponseDto
import com.travelingsalesman.agap.data.remote.dto.RegisterRequestDto
import com.travelingsalesman.agap.data.remote.dto.RefreshRequestDto
import com.travelingsalesman.agap.data.remote.dto.RegisterResponseDto
import com.travelingsalesman.agap.data.remote.dto.UpdateProfileRequestDto
import com.travelingsalesman.agap.data.remote.dto.UpdateUserStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.UserDeviceDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<RegisterResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequestDto): Response<Unit>

    @GET("users/me")
    suspend fun getProfile(): Response<UserDto>

    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): Response<UserDto>

    @GET("users/me/devices")
    suspend fun listUserDevices(): Response<List<UserDeviceDto>>

    @POST("users/me/devices")
    suspend fun addUserDevice(@Body request: AddUserDeviceRequest): Response<UserDeviceDto>

    @DELETE("users/me/devices/{id}")
    suspend fun deleteUserDevice(@Path("id") deviceId: Long): Response<Unit>

    @GET("users/me/contacts")
    suspend fun listEmergencyContacts(): Response<List<EmergencyContactDto>>

    @POST("users/me/contacts")
    suspend fun addEmergencyContact(@Body request: AddEmergencyContactRequest): Response<EmergencyContactDto>

    @PUT("users/me/contacts/{id}")
    suspend fun updateEmergencyContact(
        @Path("id") contactId: Long,
        @Body request: AddEmergencyContactRequest
    ): Response<EmergencyContactDto>

    @PUT("users/me/contacts/{id}/primary")
    suspend fun setPrimaryEmergencyContact(@Path("id") contactId: Long): Response<EmergencyContactDto>

    @DELETE("users/me/contacts/{id}")
    suspend fun deleteEmergencyContact(@Path("id") contactId: Long): Response<Unit>

    @GET("users")
    suspend fun listUsers(): Response<List<UserDto>>

    @GET("admin/users/{id}")
    suspend fun getUserById(@Path("id") id: Long): Response<UserDto>

    @PUT("admin/users/{id}/verify")
    suspend fun verifyUser(@Path("id") id: Long): Response<Unit>

    @PUT("admin/users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") id: Long,
        @Body request: UpdateUserStatusRequestDto
    ): Response<Unit>

    @PUT("admin/users/{id}")
    suspend fun updateUserByID(@Path("id") id: Long, @Body request: UpdateProfileRequestDto): Response<UserDto>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): Response<Unit>
}
