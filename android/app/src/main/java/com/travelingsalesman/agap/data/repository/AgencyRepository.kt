package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.AgencyApiService
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.mock.MockDataProvider

interface AgencyRepository {
    suspend fun getAgencies(): Result<List<AgencyDto>>
    suspend fun getAgency(id: Long): Result<AgencyDto>
}

@Singleton
class AgencyRepositoryImpl @Inject constructor(
    private val agencyApiService: AgencyApiService
) : AgencyRepository {

    override suspend fun getAgencies(): Result<List<AgencyDto>> {
        return try {
            val response = agencyApiService.listAgencies()
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                Result.Success(response.body()!!)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(listOf(MockDataProvider.sampleAgency))
            } else {
                Result.Error("Failed to fetch agencies: ${response.code()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(listOf(MockDataProvider.sampleAgency))
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun getAgency(id: Long): Result<AgencyDto> {
        return try {
            val response = agencyApiService.getAgency(id)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleAgency)
            } else {
                Result.Error("Failed to fetch agency: ${response.code()}")
            }
        } catch (e: Exception) {
            if (MockDataProvider.ENABLE_MOCK_DATA) {
                Result.Success(MockDataProvider.sampleAgency)
            } else {
                Result.Error("Network error: ${e.localizedMessage}")
            }
        }
    }
}
