package com.travelingsalesman.agap.data.remote

import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderLocationDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface ResponderApiService {
    @GET("responders")
    suspend fun listResponders(
        @Query("agency_id") agencyId: Long? = null
    ): Response<List<ResponderUnitDto>>

    @GET("responders/{id}")
    suspend fun getResponder(
        @Path("id") id: Long
    ): Response<ResponderUnitDto>

    @PATCH("responders/{id}/location")
    suspend fun updateLocation(
        @Path("id") id: Long,
        @Body request: UpdateResponderLocationDto
    ): Response<Map<String, String>>
}
