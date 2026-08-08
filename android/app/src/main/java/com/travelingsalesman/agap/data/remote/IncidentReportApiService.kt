package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
import com.travelingsalesman.agap.data.remote.dto.CreateReportResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface IncidentReportApiService {
    @POST("incident-reports")
    suspend fun createReport(
        @Body request: CreateReportRequestDto
    ): Response<CreateReportResponseDto>
}
