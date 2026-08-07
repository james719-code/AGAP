package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.PredefinedMessageDto
import retrofit2.Response
import retrofit2.http.GET

interface MessageApiService {
    @GET("messages/catalog")
    suspend fun getCatalog(): Response<List<PredefinedMessageDto>>
}
