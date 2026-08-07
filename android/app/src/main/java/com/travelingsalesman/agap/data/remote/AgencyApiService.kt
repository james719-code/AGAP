package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AgencyApiService {
    @GET("agencies")
    suspend fun listAgencies(): Response<List<AgencyDto>>

    @GET("agencies/{id}")
    suspend fun getAgency(
        @Path("id") id: Long
    ): Response<AgencyDto>

    @POST("agencies")
    suspend fun createAgency(
        @Body agency: AgencyDto
    ): Response<AgencyDto>

    @PUT("agencies/{id}")
    suspend fun updateAgency(
        @Path("id") id: Long,
        @Body agency: AgencyDto
    ): Response<AgencyDto>

    @DELETE("agencies/{id}")
    suspend fun deleteAgency(
        @Path("id") id: Long
    ): Response<Unit>
}
