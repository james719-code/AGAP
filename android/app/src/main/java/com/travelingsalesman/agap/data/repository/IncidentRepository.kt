package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapWebSocketClient
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.IncidentDto
import com.travelingsalesman.agap.data.remote.dto.UpdateDispatchStatusRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.remote.dto.CreateDispatchRequestDto

interface IncidentRepository {
    fun getIncidents(): Flow<List<IncidentEntity>>
    suspend fun getIncidentById(incidentId: String): IncidentEntity?
    suspend fun fetchIncidentById(id: Long): Result<IncidentEntity>
    suspend fun fetchRemoteIncidents(type: String? = null, status: String? = null): Result<List<IncidentEntity>>
    suspend fun updateStatus(incidentId: Long, newStatus: String): Result<Boolean>
    suspend fun dispatchIncident(incidentId: Long, unitId: Long): Result<Boolean>
    suspend fun fetchDispatches(incidentId: Long): Result<List<DispatchDto>>
    suspend fun updateDispatchStatus(incidentId: Long, dispatchId: Long, newStatus: String): Result<Boolean>
    suspend fun addIncident(incident: IncidentEntity)
    suspend fun cacheIncidents(incidents: List<IncidentDto>)
    fun listenLiveIncidents()
}

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val incidentDao: IncidentDao,
    private val incidentApiService: IncidentApiService,
    private val webSocketClient: AgapWebSocketClient,
    private val json: Json
) : IncidentRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        listenLiveIncidents()
    }

    override fun getIncidents(): Flow<List<IncidentEntity>> {
        return incidentDao.getAllIncidentsFlow()
    }

    override suspend fun getIncidentById(incidentId: String): IncidentEntity? {
        return incidentDao.getIncidentById(incidentId)
    }

    override suspend fun cacheIncidents(incidents: List<IncidentDto>) {
        if (incidents.isNotEmpty()) {
            incidentDao.insertIncidents(incidents.map { it.toEntity() })
        }
    }

    override suspend fun fetchIncidentById(id: Long): Result<IncidentEntity> {
        return try {
            val response = incidentApiService.getIncidentById(id)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val entity = IncidentEntity(
                    incidentId = dto.id.toString(),
                    targetId = dto.targetId?.raw ?: "UNASSIGNED",
                    emergencyType = dto.incidentType,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    timestamp = System.currentTimeMillis(),
                    status = dto.status,
                    packetHash = dto.packetHash,
                    channel = dto.channel,
                    deliveryPath = dto.deliveryPath,
                    senderDeviceId = dto.senderDeviceId,
                    lastSyncedAt = System.currentTimeMillis()
                )
                incidentDao.insertIncident(entity)
                Result.Success(entity)
            } else {
                val local = incidentDao.getIncidentById(id.toString())
                if (local != null) Result.Success(local)
                else Result.Error("Incident not found: ${response.code()}")
            }
        } catch (e: Exception) {
            val local = incidentDao.getIncidentById(id.toString())
            if (local != null) Result.Success(local)
            else Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchRemoteIncidents(
        type: String?,
        status: String?
    ): Result<List<IncidentEntity>> {
        return try {
            val response = incidentApiService.listIncidents(type = type, status = status)
            if (response.isSuccessful && response.body() != null) {
                val remoteIncidents = response.body()!!.incidents
                val entities = remoteIncidents.map { it.toEntity() }
                cacheIncidents(remoteIncidents)
                Result.Success(entities)
            } else {
                Result.Error("Failed to fetch incidents: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun updateStatus(incidentId: Long, newStatus: String): Result<Boolean> {
        return try {
            val response = incidentApiService.updateStatus(
                incidentId,
                UpdateIncidentStatusRequestDto(newStatus)
            )
            if (response.isSuccessful) {
                // Update local Room database as well
                val local = incidentDao.getIncidentById(incidentId.toString())
                if (local != null) {
                    incidentDao.insertIncident(local.copy(status = newStatus))
                }
                Result.Success(true)
            } else {
                Result.Error("Failed to update status: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun dispatchIncident(incidentId: Long, unitId: Long): Result<Boolean> {
        return try {
            val response = incidentApiService.dispatch(
                incidentId,
                CreateDispatchRequestDto(unitId)
            )
            if (response.isSuccessful) {
                val local = incidentDao.getIncidentById(incidentId.toString())
                if (local != null) {
                    incidentDao.insertIncident(local.copy(status = "DISPATCHED"))
                }
                Result.Success(true)
            } else {
                Result.Error("Failed to dispatch unit: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchDispatches(incidentId: Long): Result<List<DispatchDto>> {
        return try {
            val response = incidentApiService.listDispatches(incidentId)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to fetch dispatches: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun updateDispatchStatus(
        incidentId: Long,
        dispatchId: Long,
        newStatus: String
    ): Result<Boolean> {
        return try {
            val response = incidentApiService.updateDispatchStatus(
                incidentId,
                dispatchId,
                UpdateDispatchStatusRequestDto(newStatus)
            )
            if (response.isSuccessful) {
                val incidentStatus = when (newStatus) {
                    "EN_ROUTE", "ON_SCENE" -> newStatus
                    "COMPLETED" -> "RESOLVED"
                    else -> null
                }
                val local = incidentDao.getIncidentById(incidentId.toString())
                if (local != null && incidentStatus != null) {
                    incidentDao.insertIncident(local.copy(status = incidentStatus))
                }
                Result.Success(true)
            } else {
                Result.Error("Failed to update dispatch status: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.localizedMessage}")
        }
    }

    override suspend fun addIncident(incident: IncidentEntity) {
        incidentDao.insertIncident(incident)
    }

    override fun listenLiveIncidents() {
        scope.launch {
            try {
                webSocketClient.connect().collect { event ->
                    when (event.type) {
                        "NEW_INCIDENT" -> {
                            val incident = json.decodeFromJsonElement<IncidentDto>(event.payload)
                            incidentDao.insertIncident(incident.toEntity())
                        }
                        "INCIDENT_STATUS_UPDATED" -> {
                            applyStatusEvent(event.payload.jsonObject)
                        }
                    }
                }
            } catch (e: Exception) {
                // WebSocket connection error handled gracefully
            }
        }
    }

    private suspend fun applyStatusEvent(payload: JsonObject) {
        val incidentId = payload["incident_id"]?.jsonPrimitive?.longOrNull ?: return
        val status = payload["status"]?.jsonPrimitive?.contentOrNull
            ?: payload["new_status"]?.jsonPrimitive?.contentOrNull
            ?: return
        val local = incidentDao.getIncidentById(incidentId.toString())
        if (local != null) {
            incidentDao.insertIncident(local.copy(status = status, lastSyncedAt = System.currentTimeMillis()))
            return
        }

        val lat = payload["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val lon = payload["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        incidentDao.insertIncident(
            IncidentEntity(
                incidentId = incidentId.toString(),
                targetId = payload["target_id"]?.jsonPrimitive?.contentOrNull ?: "UNASSIGNED",
                emergencyType = payload["incident_type"]?.jsonPrimitive?.contentOrNull
                    ?: payload["emergency_type"]?.jsonPrimitive?.contentOrNull
                    ?: "UNKNOWN",
                latitude = lat,
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                status = status,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    private fun IncidentDto.toEntity(): IncidentEntity {
        return IncidentEntity(
            incidentId = id.toString(),
            targetId = targetId?.raw ?: "UNASSIGNED",
            emergencyType = incidentType,
            latitude = latitude,
            longitude = longitude,
            timestamp = parseServerTimestamp(eventTimestamp ?: createdAt),
            status = status,
            packetHash = packetHash,
            channel = channel,
            deliveryPath = deliveryPath,
            senderDeviceId = senderDeviceId,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun parseServerTimestamp(value: String?): Long {
        return value?.let { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
        } ?: System.currentTimeMillis()
    }
}
