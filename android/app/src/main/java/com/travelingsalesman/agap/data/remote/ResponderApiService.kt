package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderLocationDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ResponderApiService {
    @GET("rescue-units")
    suspend fun listResponders(
        @Query("agency_id") agencyId: Long? = null
    ): Response<List<ResponderUnitDto>>

    @GET("rescue-units/me")
    suspend fun getMyResponderUnit(): Response<ResponderUnitDto>

    @GET("rescue-units/me/dispatches")
    suspend fun getMyDispatches(): Response<List<DispatchDto>>

    @GET("rescue-units/{id}")
    suspend fun getResponder(
        @Path("id") id: Long
    ): Response<ResponderUnitDto>

    @PUT("rescue-units/{id}/location")
    suspend fun updateLocation(
        @Path("id") id: Long,
        @Body request: UpdateResponderLocationDto
    ): Response<Map<String, String>>

    @POST("rescue-units")
    suspend fun createResponderUnit(
        @Body unit: ResponderUnitDto
    ): Response<ResponderUnitDto>

    @PUT("rescue-units/{id}")
    suspend fun updateResponderUnit(
        @Path("id") id: Long,
        @Body unit: ResponderUnitDto
    ): Response<ResponderUnitDto>

    @DELETE("rescue-units/{id}")
    suspend fun deleteResponderUnit(
        @Path("id") id: Long
    ): Response<Unit>
}
