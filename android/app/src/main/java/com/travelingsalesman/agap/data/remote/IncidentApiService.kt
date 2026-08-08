package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.CreateDispatchRequestDto
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.IncidentDto
import com.travelingsalesman.agap.data.remote.dto.IncidentListResponseDto
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.UpdateDispatchStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusResponseDto
import com.travelingsalesman.agap.data.remote.dto.UpdateDispatchStatusResponseDto
import com.travelingsalesman.agap.data.remote.dto.IncidentMessageDto
import com.travelingsalesman.agap.data.remote.dto.SendMessageRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface IncidentApiService {
    @GET("incidents")
    suspend fun listIncidents(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("region") region: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<IncidentListResponseDto>

    @GET("incidents/{id}")
    suspend fun getIncidentById(
        @Path("id") id: Long
    ): Response<IncidentDto>

    @PUT("incidents/{id}/status")
    suspend fun updateStatus(
        @Path("id") incidentId: Long,
        @Body request: UpdateIncidentStatusRequestDto
    ): Response<UpdateIncidentStatusResponseDto>

    @POST("incidents/{id}/dispatch")
    suspend fun dispatch(
        @Path("id") incidentId: Long,
        @Body request: CreateDispatchRequestDto
    ): Response<DispatchDto>

    @GET("incidents/{id}/dispatches")
    suspend fun listDispatches(
        @Path("id") incidentId: Long
    ): Response<List<DispatchDto>>

    @PUT("incidents/{id}/dispatches/{dispatchId}/status")
    suspend fun updateDispatchStatus(
        @Path("id") incidentId: Long,
        @Path("dispatchId") dispatchId: Long,
        @Body request: UpdateDispatchStatusRequestDto
    ): Response<UpdateDispatchStatusResponseDto>

    @GET("incidents/{id}/messages")
    suspend fun listMessages(
        @Path("id") incidentId: Long
    ): Response<List<IncidentMessageDto>>

    @POST("incidents/{id}/messages")
    suspend fun sendMessage(
        @Path("id") incidentId: Long,
        @Body request: SendMessageRequestDto
    ): Response<IncidentMessageDto>
}
