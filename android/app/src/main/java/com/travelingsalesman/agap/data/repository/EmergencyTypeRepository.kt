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
            protocolCode = protocolCode,
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
            protocolCode = protocolCode,
            targetAgencyType = targetAgencyType,
            isActive = isActive,
            updatedAt = serverUpdatedAt
        )
    }

    private fun defaultEmergencyTypes(): List<EmergencyTypeDto> {
        return listOf(
            EmergencyTypeDto(id = 1, code = "MEDICAL", name = "Medical", description = "Immediate medical assistance", defaultPriority = 1, protocolCode = 1, targetAgencyType = "MEDICAL"),
            EmergencyTypeDto(id = 2, code = "ACCIDENT", name = "Accident", description = "Vehicular or public accident", defaultPriority = 2, protocolCode = 2, targetAgencyType = "RESCUE"),
            EmergencyTypeDto(id = 3, code = "FIRE", name = "Fire", description = "Fire incident or smoke report", defaultPriority = 1, protocolCode = 3, targetAgencyType = "FIRE"),
            EmergencyTypeDto(id = 4, code = "DISASTER", name = "Disaster", description = "Flood, typhoon, earthquake, or volcanic emergency", defaultPriority = 2, protocolCode = 4, targetAgencyType = "RESCUE"),
            EmergencyTypeDto(id = 5, code = "CRIME", name = "Crime", description = "Police or public safety emergency", defaultPriority = 2, protocolCode = 5, targetAgencyType = "POLICE")
        )
    }
}
