package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.NotificationDto
import com.travelingsalesman.agap.data.remote.dto.MarkReadResponseDto
import com.travelingsalesman.agap.data.remote.dto.AdminAlertDto
import com.travelingsalesman.agap.data.remote.dto.CreateAlertRequestDto
import com.travelingsalesman.agap.data.remote.dto.AlertListResponseDto
import retrofit2.Response
import retrofit2.http.*

interface NotificationApiService {
    @GET("notifications")
    suspend fun listNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<NotificationDto>>

    @PUT("notifications/{id}/read")
    suspend fun markAsRead(
        @Path("id") notificationId: Long
    ): Response<MarkReadResponseDto>

    @GET("alerts")
    suspend fun listAlerts(
        @Query("active_only") activeOnly: Boolean = true,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<AlertListResponseDto>

    @POST("alerts")
    suspend fun createAlert(
        @Body request: CreateAlertRequestDto
    ): Response<AdminAlertDto>

    @DELETE("alerts/{id}")
    suspend fun deleteAlert(
        @Path("id") id: Long
    ): Response<Unit>
}
