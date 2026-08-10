package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import com.travelingsalesman.agap.data.remote.dto.MeshDeliveryEventsBatchRequestDto
import com.travelingsalesman.agap.data.remote.dto.MeshDeliveryEventsBatchResponseDto
import com.travelingsalesman.agap.data.remote.dto.SyncBootstrapDto
import com.travelingsalesman.agap.data.remote.dto.SyncChangesDto
import com.travelingsalesman.agap.data.remote.dto.SyncPushRequestDto
import com.travelingsalesman.agap.data.remote.dto.SyncPushResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AgapApiService {
    @POST("telemetry/uplink")
    suspend fun sendTelemetryUplink(
        @Body request: TelemetryUplinkRequest
    ): Response<TelemetryUplinkResponse>

    @POST("mesh/events/batch")
    suspend fun sendMeshDeliveryEvents(
        @Body request: MeshDeliveryEventsBatchRequestDto
    ): Response<MeshDeliveryEventsBatchResponseDto>

    @GET("sync/bootstrap")
    suspend fun getSyncBootstrap(): Response<SyncBootstrapDto>

    @POST("sync/push")
    suspend fun pushSync(
        @Body request: SyncPushRequestDto
    ): Response<SyncPushResultDto>

    @GET("sync/changes")
    suspend fun getSyncChanges(
        @Query("since") since: String
    ): Response<SyncChangesDto>

    @GET("emergency-types")
    suspend fun getEmergencyTypes(
        @Query("include_inactive") includeInactive: Boolean = false
    ): Response<List<com.travelingsalesman.agap.data.remote.dto.EmergencyTypeDto>>

    @retrofit2.http.PUT("incidents/{id}/location")
    suspend fun updateIncidentLocation(
        @retrofit2.http.Path("id") incidentId: Long,
        @Body request: com.travelingsalesman.agap.data.remote.dto.LocationUpdatePayload
    ): Response<com.travelingsalesman.agap.data.remote.dto.LocationUpdateResponse>
}

