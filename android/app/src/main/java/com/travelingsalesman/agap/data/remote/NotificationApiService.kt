package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.NotificationDto
import com.travelingsalesman.agap.data.remote.dto.MarkReadResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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
}
