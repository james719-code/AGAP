package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapApiService
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.EmergencyTypeDao
import com.travelingsalesman.agap.data.local.entity.EmergencyTypeEntity
import com.travelingsalesman.agap.data.remote.dto.EmergencyTypeDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface EmergencyTypeRepository {
    fun getActiveEmergencyTypesFlow(): Flow<List<EmergencyTypeEntity>>
    suspend fun refreshEmergencyTypes(): Result<List<EmergencyTypeDto>>
    suspend fun syncEmergencyTypes(types: List<EmergencyTypeDto>)
    suspend fun seedDefaultEmergencyTypes()
}

@Singleton
class EmergencyTypeRepositoryImpl @Inject constructor(
    private val apiService: AgapApiService,
    private val emergencyTypeDao: EmergencyTypeDao
) : EmergencyTypeRepository {

    override fun getActiveEmergencyTypesFlow(): Flow<List<EmergencyTypeEntity>> {
        return emergencyTypeDao.getActiveEmergencyTypesFlow()
    }

    override suspend fun refreshEmergencyTypes(): Result<List<EmergencyTypeDto>> {
        return try {
            val response = apiService.getEmergencyTypes()
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                val dtos = response.body()!!
                syncEmergencyTypes(dtos)
                Result.Success(dtos)
            } else {
                cachedFallback("Failed to fetch emergency types: ${response.code()}")
            }
        } catch (e: Exception) {
            cachedFallback("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun syncEmergencyTypes(types: List<EmergencyTypeDto>) {
        if (types.isNotEmpty()) {
            emergencyTypeDao.insertEmergencyTypes(types.map { it.toEntity() })
        }
    }

    override suspend fun seedDefaultEmergencyTypes() {
        emergencyTypeDao.insertEmergencyTypes(defaultEmergencyTypes().map { it.toEntity() })
    }

    private suspend fun cachedFallback(errorMessage: String): Result<List<EmergencyTypeDto>> {
        val cached = emergencyTypeDao.getAllEmergencyTypes()
        if (cached.isNotEmpty()) {
            return Result.Success(cached.map { it.toDto() })
        }

        seedDefaultEmergencyTypes()
        val seeded = emergencyTypeDao.getAllEmergencyTypes()
        return if (seeded.isNotEmpty()) {
            Result.Success(seeded.map { it.toDto() })
        } else {
            Result.Error(errorMessage)
        }
    }

    private fun EmergencyTypeDto.toEntity(): EmergencyTypeEntity {
        return EmergencyTypeEntity(
            id = id,
            code = code,
            name = name,
            description = description,
            defaultPriority = defaultPriority,
            targetAgencyType = targetAgencyType,
            isActive = isActive,
            serverUpdatedAt = updatedAt
        )
    }

    private fun EmergencyTypeEntity.toDto(): EmergencyTypeDto {
        return EmergencyTypeDto(
            id = id,
            code = code,
            name = name,
            description = description,
            defaultPriority = defaultPriority,
            targetAgencyType = targetAgencyType,
            isActive = isActive,
            updatedAt = serverUpdatedAt
        )
    }

    private fun defaultEmergencyTypes(): List<EmergencyTypeDto> {
        return listOf(
            EmergencyTypeDto(1, "MEDICAL", "Medical", "Immediate medical assistance", 1, "MEDICAL"),
            EmergencyTypeDto(2, "ACCIDENT", "Accident", "Vehicular or public accident", 2, "RESCUE"),
            EmergencyTypeDto(3, "FIRE", "Fire", "Fire incident or smoke report", 1, "FIRE"),
            EmergencyTypeDto(4, "DISASTER", "Disaster", "Flood, typhoon, earthquake, or volcanic emergency", 2, "RESCUE"),
            EmergencyTypeDto(5, "CRIME", "Crime", "Police or public safety emergency", 2, "POLICE")
        )
    }
}
