package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.IncidentListResponseDto
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
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

    @PATCH("incidents/{id}/status")
    suspend fun updateStatus(
        @Path("id") incidentId: Long,
        @Body request: UpdateIncidentStatusRequestDto
    ): Response<UpdateIncidentStatusResponseDto>
}
