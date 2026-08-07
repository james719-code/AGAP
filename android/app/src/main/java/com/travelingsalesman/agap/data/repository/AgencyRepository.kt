package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.remote.AgencyApiService
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AgencyRepository {
    suspend fun getAgencies(): Result<List<AgencyDto>>
    suspend fun getAgency(id: Long): Result<AgencyDto>
    suspend fun createAgency(agency: AgencyDto): Result<AgencyDto>
    suspend fun updateAgency(id: Long, agency: AgencyDto): Result<AgencyDto>
    suspend fun deleteAgency(id: Long): Result<Boolean>
    suspend fun cacheAgencies(agencies: List<AgencyDto>)
    fun getAgenciesFlow(): Flow<List<AgencyEntity>>
}

@Singleton
class AgencyRepositoryImpl @Inject constructor(
    private val agencyApiService: AgencyApiService,
    private val agencyDao: AgencyDao
) : AgencyRepository {

    override fun getAgenciesFlow(): Flow<List<AgencyEntity>> {
        return agencyDao.getAllAgenciesFlow()
    }

    override suspend fun cacheAgencies(agencies: List<AgencyDto>) {
        if (agencies.isNotEmpty()) {
            agencyDao.insertAgencies(agencies.map { it.toEntity() })
        }
    }

    override suspend fun getAgencies(): Result<List<AgencyDto>> {
        return try {
            val response = agencyApiService.listAgencies()
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                val dtos = response.body()!!
                cacheAgencies(dtos)
                Result.Success(dtos)
            } else {
                fetchCachedAgenciesFallback("Failed to fetch agencies: ${response.code()}")
            }
        } catch (e: Exception) {
            fetchCachedAgenciesFallback("Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun fetchCachedAgenciesFallback(errorMessage: String): Result<List<AgencyDto>> {
        val cached = agencyDao.getAllAgencies()
        if (cached.isNotEmpty()) {
            val dtos = cached.map { entity ->
                AgencyDto(
                    id = entity.id,
                    code = entity.code,
                    name = entity.name,
                    type = entity.type,
                    regionCode = entity.regionCode,
                    provinceCode = entity.provinceCode,
                    muniCode = entity.muniCode,
                    barangayCode = entity.barangayCode,
                    contactNumber = entity.contactNumber,
                    latitude = entity.latitude,
                    longitude = entity.longitude
                )
            }
            return Result.Success(dtos)
        }
        return Result.Error(errorMessage)
    }

    override suspend fun getAgency(id: Long): Result<AgencyDto> {
        return try {
            val response = agencyApiService.getAgency(id)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                agencyDao.insertAgency(
                    dto.toEntity()
                )
                Result.Success(dto)
            } else {
                fetchCachedAgencyFallback(id, "Failed to fetch agency: ${response.code()}")
            }
        } catch (e: Exception) {
            fetchCachedAgencyFallback(id, "Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun fetchCachedAgencyFallback(id: Long, errorMessage: String): Result<AgencyDto> {
        val cached = agencyDao.getAgencyById(id)
        if (cached != null) {
            return Result.Success(
                AgencyDto(
                    id = cached.id,
                    code = cached.code,
                    name = cached.name,
                    type = cached.type,
                    regionCode = cached.regionCode,
                    provinceCode = cached.provinceCode,
                    muniCode = cached.muniCode,
                    barangayCode = cached.barangayCode,
                    contactNumber = cached.contactNumber,
                    latitude = cached.latitude,
                    longitude = cached.longitude
                )
            )
        }
        return Result.Error(errorMessage)
    }

    override suspend fun createAgency(agency: AgencyDto): Result<AgencyDto> {
        return try {
            val response = agencyApiService.createAgency(agency)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                agencyDao.insertAgency(
                    dto.toEntity()
                )
                Result.Success(dto)
            } else {
                Result.Error("Failed to create agency: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun updateAgency(id: Long, agency: AgencyDto): Result<AgencyDto> {
        return try {
            val response = agencyApiService.updateAgency(id, agency)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                agencyDao.insertAgency(
                    dto.toEntity()
                )
                Result.Success(dto)
            } else {
                Result.Error("Failed to update agency: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteAgency(id: Long): Result<Boolean> {
        return try {
            val response = agencyApiService.deleteAgency(id)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to delete agency: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    private fun AgencyDto.toEntity(): AgencyEntity {
        return AgencyEntity(
            id = id,
            code = code,
            name = name,
            type = type,
            regionCode = regionCode,
            provinceCode = provinceCode,
            muniCode = muniCode,
            barangayCode = barangayCode,
            contactNumber = contactNumber,
            latitude = latitude,
            longitude = longitude
        )
    }
}
