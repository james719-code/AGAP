package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.AgapWebSocketClient
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface IncidentRepository {
    fun getIncidents(): Flow<List<IncidentEntity>>
    suspend fun addIncident(incident: IncidentEntity)
    fun listenLiveIncidents()
}

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val incidentDao: IncidentDao,
    private val webSocketClient: AgapWebSocketClient
) : IncidentRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        listenLiveIncidents()
    }

    override fun getIncidents(): Flow<List<IncidentEntity>> {
        return incidentDao.getAllIncidentsFlow()
    }

    override suspend fun addIncident(incident: IncidentEntity) {
        incidentDao.insertIncident(incident)
    }

    override fun listenLiveIncidents() {
        scope.launch {
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
        }
    }
}
