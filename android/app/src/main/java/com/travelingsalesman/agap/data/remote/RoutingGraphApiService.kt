package com.travelingsalesman.agap.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming

@Serializable
data class RoutingGraphMetadataDto(
    @SerialName("available") val available: Boolean = false,
    @SerialName("file_name") val fileName: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    @SerialName("sha256") val sha256: String = "",
    @SerialName("modified_at") val modifiedAt: String = ""
)

interface RoutingGraphApiService {
    @GET("maps/routing-graph/metadata")
    suspend fun metadata(): Response<RoutingGraphMetadataDto>

    @Streaming
    @GET("maps/routing-graph")
    suspend fun download(): Response<ResponseBody>
}
