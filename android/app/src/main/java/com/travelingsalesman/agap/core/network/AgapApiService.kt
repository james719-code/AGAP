package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkRequest
import com.travelingsalesman.agap.data.remote.dto.TelemetryUplinkResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AgapApiService {
    @POST("telemetry/uplink")
    suspend fun sendTelemetryUplink(
        @Body request: TelemetryUplinkRequest
    ): Response<TelemetryUplinkResponse>
}
