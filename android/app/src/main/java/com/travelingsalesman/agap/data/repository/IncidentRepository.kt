package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapWebSocketClient
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    suspend fun addIncident(incident: IncidentEntity)
    fun listenLiveIncidents()
}

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val incidentDao: IncidentDao,
    private val incidentApiService: IncidentApiService,
    private val webSocketClient: AgapWebSocketClient
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
                val entities = remoteIncidents.map { dto ->
                    IncidentEntity(
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
                }
                incidentDao.insertIncidents(entities)
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

    override suspend fun addIncident(incident: IncidentEntity) {
        incidentDao.insertIncident(incident)
    }

    override fun listenLiveIncidents() {
        scope.launch {
            try {
                webSocketClient.connect().collect { event ->
                    val entity = IncidentEntity(
                        incidentId = event.incidentId,
                        targetId = event.targetId,
                        emergencyType = event.emergencyType,
                        latitude = event.latitude,
                        longitude = event.longitude,
                        timestamp = event.timestamp,
                        status = event.status
                    )
                    incidentDao.insertIncident(entity)
                }
            } catch (e: Exception) {
                // WebSocket connection error handled gracefully
            }
        }
    }
}
