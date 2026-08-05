package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.ResponderApiService
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderLocationDto
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.mock.MockDataProvider

interface ResponderRepository {
    suspend fun getResponders(agencyId: Long? = null): Result<List<ResponderUnitDto>>
    suspend fun getResponder(id: Long): Result<ResponderUnitDto>
    suspend fun updateLocation(id: Long, lat: Double, lon: Double, status: String): Result<Boolean>
}

@Singleton
class ResponderRepositoryImpl @Inject constructor(
    private val responderApiService: ResponderApiService
) : ResponderRepository {

    override suspend fun getResponders(agencyId: Long?): Result<List<ResponderUnitDto>> {
        return try {
            val response = responderApiService.listResponders(agencyId)
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                Result.Success(response.body()!!)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleResponders)
            } else {
                Result.Error("Failed to fetch responders: ${response.code()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleResponders)
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun getResponder(id: Long): Result<ResponderUnitDto> {
        return try {
            val response = responderApiService.getResponder(id)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleResponders.firstOrNull { it.id == id } ?: MockDataProvider.sampleResponders[0])
            } else {
                Result.Error("Failed to fetch responder: ${response.code()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleResponders.firstOrNull { it.id == id } ?: MockDataProvider.sampleResponders[0])
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun updateLocation(
        id: Long,
        lat: Double,
        lon: Double,
        status: String
    ): Result<Boolean> {
        return try {
            val response = responderApiService.updateLocation(
                id,
                UpdateResponderLocationDto(lat, lon, status)
            )
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to update location: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }
}
