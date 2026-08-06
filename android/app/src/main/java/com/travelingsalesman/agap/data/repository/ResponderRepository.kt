package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.ResponderApiService
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderLocationDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ResponderRepository {
    suspend fun getResponders(agencyId: Long? = null): Result<List<ResponderUnitDto>>
    suspend fun getResponder(id: Long): Result<ResponderUnitDto>
    suspend fun updateLocation(id: Long, lat: Double, lon: Double, status: String): Result<Boolean>
    fun getRespondersFlow(agencyId: Long? = null): Flow<List<ResponderUnitEntity>>
}

@Singleton
class ResponderRepositoryImpl @Inject constructor(
    private val responderApiService: ResponderApiService,
    private val responderDao: ResponderDao
) : ResponderRepository {

    override fun getRespondersFlow(agencyId: Long?): Flow<List<ResponderUnitEntity>> {
        return if (agencyId != null) {
            responderDao.getRespondersByAgencyFlow(agencyId)
        } else {
            responderDao.getAllRespondersFlow()
        }
    }

    override suspend fun getResponders(agencyId: Long?): Result<List<ResponderUnitDto>> {
        return try {
            val response = responderApiService.listResponders(agencyId)
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                val dtos = response.body()!!
                val entities = dtos.map { dto ->
                    ResponderUnitEntity(
                        id = dto.id,
                        agencyId = dto.agencyId,
                        targetId = dto.targetId,
                        unitCallsign = dto.unitCallsign,
                        deviceId = dto.deviceId,
                        status = dto.status,
                        currentLat = dto.currentLat,
                        currentLon = dto.currentLon,
                        lastSeenAt = dto.lastSeenAt
                    )
                }
                responderDao.insertResponders(entities)
                Result.Success(dtos)
            } else {
                fetchCachedRespondersFallback(agencyId, "Failed to fetch responders: ${response.code()}")
            }
        } catch (e: Exception) {
            fetchCachedRespondersFallback(agencyId, "Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun fetchCachedRespondersFallback(agencyId: Long?, errorMessage: String): Result<List<ResponderUnitDto>> {
        val cached = if (agencyId != null) responderDao.getRespondersByAgency(agencyId) else responderDao.getAllResponders()
        if (cached.isNotEmpty()) {
            val dtos = cached.map { entity ->
                ResponderUnitDto(
                    id = entity.id,
                    agencyId = entity.agencyId,
                    targetId = entity.targetId,
                    unitCallsign = entity.unitCallsign,
                    deviceId = entity.deviceId,
                    status = entity.status,
                    currentLat = entity.currentLat,
                    currentLon = entity.currentLon,
                    lastSeenAt = entity.lastSeenAt
                )
            }
            return Result.Success(dtos)
        }
        return Result.Error(errorMessage)
    }

    override suspend fun getResponder(id: Long): Result<ResponderUnitDto> {
        return try {
            val response = responderApiService.getResponder(id)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                responderDao.insertResponder(
                    ResponderUnitEntity(
                        id = dto.id,
                        agencyId = dto.agencyId,
                        targetId = dto.targetId,
                        unitCallsign = dto.unitCallsign,
                        deviceId = dto.deviceId,
                        status = dto.status,
                        currentLat = dto.currentLat,
                        currentLon = dto.currentLon,
                        lastSeenAt = dto.lastSeenAt
                    )
                )
                Result.Success(dto)
            } else {
                fetchCachedResponderFallback(id, "Failed to fetch responder: ${response.code()}")
            }
        } catch (e: Exception) {
            fetchCachedResponderFallback(id, "Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun fetchCachedResponderFallback(id: Long, errorMessage: String): Result<ResponderUnitDto> {
        val cached = responderDao.getResponderById(id)
        if (cached != null) {
            return Result.Success(
                ResponderUnitDto(
                    id = cached.id,
                    agencyId = cached.agencyId,
                    targetId = cached.targetId,
                    unitCallsign = cached.unitCallsign,
                    deviceId = cached.deviceId,
                    status = cached.status,
                    currentLat = cached.currentLat,
                    currentLon = cached.currentLon,
                    lastSeenAt = cached.lastSeenAt
                )
            )
        }
        return Result.Error(errorMessage)
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
                // Update local Room database as well if available
                val local = responderDao.getResponderById(id)
                if (local != null) {
                    responderDao.insertResponder(local.copy(currentLat = lat, currentLon = lon, status = status))
                }
                Result.Success(true)
            } else {
                Result.Error("Failed to update location: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }
}

