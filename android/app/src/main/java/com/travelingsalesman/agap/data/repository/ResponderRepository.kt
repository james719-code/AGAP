package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.ResponderApiService
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderLocationDto
import com.travelingsalesman.agap.data.remote.dto.UpdateResponderUnitRequestDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.data.remote.dto.withNormalizedStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface ResponderRepository {
    suspend fun getResponders(agencyId: Long? = null): Result<List<ResponderUnitDto>>
    suspend fun getResponder(id: Long): Result<ResponderUnitDto>
    suspend fun getMyResponderUnit(): Result<ResponderUnitDto>
    suspend fun getMyDispatches(): Result<List<DispatchDto>>
    suspend fun updateLocation(id: Long, lat: Double, lon: Double, status: String): Result<Boolean>
    suspend fun createResponderUnit(unit: ResponderUnitDto): Result<ResponderUnitDto>
    suspend fun updateResponderUnit(id: Long, unit: ResponderUnitDto): Result<ResponderUnitDto>
    suspend fun deleteResponderUnit(id: Long): Result<Boolean>
    fun getRespondersFlow(agencyId: Long? = null): Flow<List<ResponderUnitEntity>>
}

/**
 * An assignment endpoint denial is authoritative: operational screens must
 * immediately remove any previously cached responder assignment. Transient
 * server and transport failures intentionally use ordinary [Result.Error] so
 * the last verified offline snapshot remains available.
 */
class AssignmentVerificationDeniedException(
    val statusCode: Int
) : Exception("Assignment verification denied: HTTP $statusCode")

@Singleton
class ResponderRepositoryImpl @Inject constructor(
    private val responderApiService: ResponderApiService,
    private val responderDao: ResponderDao,
    private val networkMonitor: NetworkMonitor
) : ResponderRepository {

    override fun getRespondersFlow(agencyId: Long?): Flow<List<ResponderUnitEntity>> {
        return if (agencyId != null) {
            responderDao.getRespondersByAgencyFlow(agencyId)
        } else {
            responderDao.getAllRespondersFlow()
        }
    }

    override suspend fun getResponders(agencyId: Long?): Result<List<ResponderUnitDto>> {
        if (!networkMonitor.canAccessDataService()) {
            return fetchCachedRespondersFallback(agencyId, "Internet is unavailable")
        }

        return try {
            val response = responderApiService.listResponders(agencyId)
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!.map { it.withNormalizedStatus() }
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
                // A successful server response is authoritative, including an
                // empty list. Replace just the requested agency slice so units
                // deleted on the backend disappear from admin/respondent views.
                responderDao.replaceResponders(agencyId, entities)
                Result.Success(dtos)
            } else {
                Result.Error(UserMessageResolver.http("load responders", response.code()))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            if (!networkMonitor.canAccessDataService()) {
                fetchCachedRespondersFallback(agencyId, "Internet is unavailable")
            } else {
                Result.Error(UserMessageResolver.network("Load responders", e))
            }
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
                    status = normalizeResponderStatusValue(entity.status),
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
        if (!networkMonitor.canAccessDataService()) {
            return fetchCachedResponderFallback(id, "Internet is unavailable")
        }

        return try {
            val response = responderApiService.getResponder(id)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!.withNormalizedStatus()
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
                Result.Error(UserMessageResolver.http("load responder details", response.code()))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            if (!networkMonitor.canAccessDataService()) {
                fetchCachedResponderFallback(id, "Internet is unavailable")
            } else {
                Result.Error(UserMessageResolver.network("Load responder details", e))
            }
        }
    }

    override suspend fun getMyResponderUnit(): Result<ResponderUnitDto> {
        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; your responder unit has not been refreshed")
        }

        return try {
            val response = responderApiService.getMyResponderUnit()
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!.withNormalizedStatus()
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
                Result.Error("Failed to fetch my responder unit: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load your responder unit", e))
        }
    }

    override suspend fun getMyDispatches(): Result<List<DispatchDto>> {
        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; using the last verified assignments")
        }

        return try {
            val response = responderApiService.getMyDispatches()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val message = "Failed to fetch assigned dispatches: ${response.code()}"
                if (response.code() == 401 || response.code() == 403) {
                    Result.Error(AssignmentVerificationDeniedException(response.code()), message)
                } else {
                    Result.Error(message)
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load assigned dispatches", e))
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
                    status = normalizeResponderStatusValue(cached.status),
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
            val normalizedStatus = normalizeResponderStatusValue(status)
            val response = responderApiService.updateLocation(
                id,
                UpdateResponderLocationDto(lat, lon, normalizedStatus)
            )
            if (response.isSuccessful) {
                // Update local Room database as well if available
                val local = responderDao.getResponderById(id)
                if (local != null) {
                    responderDao.insertResponder(local.copy(currentLat = lat, currentLon = lon, status = normalizedStatus))
                }
                Result.Success(true)
            } else {
                Result.Error(response.backendError("Failed to update location"))
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun createResponderUnit(unit: ResponderUnitDto): Result<ResponderUnitDto> {
        return try {
            val response = responderApiService.createResponderUnit(unit)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!.withNormalizedStatus()
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
                Result.Error(response.backendError("Failed to create responder unit"))
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun updateResponderUnit(id: Long, unit: ResponderUnitDto): Result<ResponderUnitDto> {
        return try {
            val request = UpdateResponderUnitRequestDto(
                agencyId = unit.agencyId,
                unitCallsign = unit.unitCallsign.trim(),
                status = normalizeResponderStatusValue(unit.status),
                targetId = unit.targetId.trim()
            )
            val response = responderApiService.updateResponderUnit(id, request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!.withNormalizedStatus()
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
                Result.Error(response.backendError("Failed to update responder unit"))
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteResponderUnit(id: Long): Result<Boolean> {
        return try {
            val response = responderApiService.deleteResponderUnit(id)
            if (response.isSuccessful) {
                responderDao.deleteResponderById(id)
                Result.Success(true)
            } else {
                Result.Error("Failed to delete responder unit: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    private fun retrofit2.Response<*>.backendError(prefix: String): String {
        val raw = runCatching { errorBody()?.string().orEmpty() }.getOrDefault("")
        val backendMessage = runCatching { JSONObject(raw).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (backendMessage != null) "$prefix: $backendMessage" else "$prefix: HTTP ${code()}"
    }

}
