package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface AgencyApiService {
    @GET("agencies")
    suspend fun listAgencies(): Response<List<AgencyDto>>

    @GET("agencies/{id}")
    suspend fun getAgency(
        @Path("id") id: Long
    ): Response<AgencyDto>
}
