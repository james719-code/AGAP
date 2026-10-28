package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.LiveSyncState
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.network.RealtimeSyncCoordinator
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.dto.UpdateIncidentStatusRequestDto
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.IncidentDto
import com.travelingsalesman.agap.data.remote.dto.IncidentMessageDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.withNormalizedStatus
import com.travelingsalesman.agap.data.remote.dto.UpdateDispatchStatusRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.remote.dto.CreateDispatchRequestDto

interface IncidentRepository {
    val liveSyncState: StateFlow<LiveSyncState>
    fun getIncidents(): Flow<List<IncidentEntity>>

    /**
     * Preferred observable APIs for mobile screens.
     */
    fun getRecentIncidents(
        limit: Int
    ): Flow<List<IncidentEntity>>

    fun getRecentActiveIncidents(
        limit: Int
    ): Flow<List<IncidentEntity>>
    suspend fun getIncidentById(incidentId: String): IncidentEntity?
    suspend fun fetchIncidentById(id: Long): Result<IncidentEntity>
    suspend fun fetchRemoteIncidents(type: String? = null, status: String? = null): Result<List<IncidentEntity>>
    suspend fun updateStatus(incidentId: Long, newStatus: String): Result<Boolean>
    suspend fun dispatchIncident(incidentId: Long, unitId: Long): Result<Boolean>
    suspend fun fetchAvailableResponders(incidentId: Long): Result<List<ResponderUnitDto>>
    suspend fun fetchDispatches(incidentId: Long): Result<List<DispatchDto>>
    suspend fun fetchDispatchesBulk(incidentIds: List<Long>): Result<Map<Long, List<DispatchDto>>>
    suspend fun updateDispatchStatus(incidentId: Long, dispatchId: Long, newStatus: String): Result<Boolean>
    suspend fun addIncident(incident: IncidentEntity)
    suspend fun cacheIncidents(incidents: List<IncidentDto>)
    fun listenLiveIncidents()
}

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val incidentDao: IncidentDao,
    private val incidentApiService: IncidentApiService,
    private val realtimeSyncCoordinator: RealtimeSyncCoordinator,
    private val json: Json,
    private val responderDao: ResponderDao,
    private val incidentMessagingRepository: IncidentMessagingRepository,
    private val networkMonitor: NetworkMonitor
) : IncidentRepository {

    override val liveSyncState: StateFlow<LiveSyncState> = realtimeSyncCoordinator.state

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var liveIncidentJob: Job? = null

    init {
        listenLiveIncidents()
    }

    override fun getIncidents(): Flow<List<IncidentEntity>> {
        return incidentDao.getAllIncidentsFlow()
    }

    override fun getRecentIncidents(
        limit: Int
    ): Flow<List<IncidentEntity>> {
        return incidentDao.getRecentIncidentsFlow(
            limit.coerceIn(1, 5_000)
        )
    }

    override fun getRecentActiveIncidents(
        limit: Int
    ): Flow<List<IncidentEntity>> {
        return incidentDao.getRecentActiveIncidentsFlow(
            limit.coerceIn(1, 2_000)
        )
    }

    override suspend fun getIncidentById(incidentId: String): IncidentEntity? {
        return incidentDao.getIncidentById(incidentId)
    }

    /**
     * Reconciled mesh incidents retain their UUID primary key, so a numeric
     * route id must also consult serverIncidentId when falling back to Room.
     */
    private suspend fun cachedIncident(serverIncidentId: Long): IncidentEntity? {
        return incidentDao.getIncidentByServerId(serverIncidentId)
            ?: incidentDao.getIncidentById(serverIncidentId.toString())
    }

    override suspend fun cacheIncidents(
        incidents: List<IncidentDto>
    ) {
        incidentDao.cacheIncidentSnapshot(incidents.map { it.toEntity() })
    }

    override suspend fun fetchIncidentById(id: Long): Result<IncidentEntity> {
        if (!networkMonitor.canAccessDataService()) {
            return cachedIncident(id)
                ?.let { Result.Success(it) }
                ?: Result.Error("Internet is unavailable and this incident is not cached")
        }

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
                    logicalId = dto.packetHash.ifBlank { dto.id.toString() },
                    serverIncidentId = dto.id,
                    deliveryState = "RECEIVED_BY_COMMAND",
                    channel = dto.channel,
                    deliveryPath = dto.deliveryPath,
                    senderDeviceId = dto.senderDeviceId,
                    lastSyncedAt = System.currentTimeMillis()
                )
                val local = dto.packetHash.takeIf { it.isNotBlank() }?.let {
                    incidentDao.getIncidentByPacketHash(it)
                }
                val merged = entity.copy(incidentId = local?.incidentId ?: entity.incidentId)
                incidentDao.insertIncident(merged)
                Result.Success(merged)
            } else {
                // Do not silently present an old cached incident as current after
                // an authoritative denial/deletion response from a reachable API.
                Result.Error(UserMessageResolver.http("load this incident", response.code()))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            // The device may have gone offline after the initial validated
            // connectivity check. Only then may Room stand in for the API.
            if (!networkMonitor.canAccessDataService()) {
                cachedIncident(id)
                    ?.let { Result.Success(it) }
                    ?: Result.Error(UserMessageResolver.network("Load this incident", e))
            } else {
                Result.Error(UserMessageResolver.network("Load this incident", e))
            }
        }
    }

    override suspend fun fetchRemoteIncidents(
        type: String?,
        status: String?
    ): Result<List<IncidentEntity>> {
        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; showing cached incidents")
        }

        return try {
            val response = incidentApiService.listIncidents(type = type, status = status)
            if (response.isSuccessful && response.body() != null) {
                val remoteIncidents = response.body()!!.incidents
                val entities = remoteIncidents.map { it.toEntity() }
                cacheIncidents(remoteIncidents)
                Result.Success(entities)
            } else {
                Result.Error(UserMessageResolver.http("load incidents", response.code()))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load incidents", e))
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
                Result.Error(UserMessageResolver.http("update the incident status", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update the incident status", e))
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
                    incidentDao.insertIncident(local.copy(status = "DISPATCHED", dispatchedUnitId = unitId))
                }
                Result.Success(true)
            } else {
                Result.Error(response.backendError("Failed to dispatch unit"))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Dispatch a responder", e))
        }
    }

    override suspend fun fetchAvailableResponders(incidentId: Long): Result<List<ResponderUnitDto>> {
        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; responder availability cannot be refreshed")
        }

        return try {
            val response = incidentApiService.listAvailableResponders(incidentId)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.map { it.withNormalizedStatus() })
            } else {
                Result.Error(response.backendError("Unable to load available responders"))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load available responders", e))
        }
    }

    override suspend fun fetchDispatches(incidentId: Long): Result<List<DispatchDto>> {
        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; using cached dispatch details")
        }

        return try {
            val response = incidentApiService.listDispatches(incidentId)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error(UserMessageResolver.http("load dispatch details", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load dispatch details", e))
        }
    }

    override suspend fun fetchDispatchesBulk(incidentIds: List<Long>): Result<Map<Long, List<DispatchDto>>> {
        val ids = incidentIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) return Result.Success(emptyMap())

        if (!networkMonitor.canAccessDataService()) {
            return Result.Error("Internet is unavailable; using cached dispatch assignments")
        }

        return try {
            val merged = linkedMapOf<Long, List<DispatchDto>>()
            // Backend limits a bulk request to 200 incident ids.
            ids.chunked(200).forEach { chunk ->
                val response = incidentApiService.listDispatchesBulk(chunk.joinToString(","))
                if (!response.isSuccessful || response.body() == null) {
                    return Result.Error(UserMessageResolver.http("load dispatch assignments", response.code()))
                }
                response.body().orEmpty().forEach { (incidentId, dispatches) ->
                    incidentId.toLongOrNull()?.let { merged[it] = dispatches }
                }
            }
            Result.Success(merged)
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Load dispatch assignments", e))
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
                val incidentStatus = when (newStatus.uppercase()) {
                    "EN_ROUTE", "ON_SCENE" -> newStatus.uppercase()
                    "COMPLETED" -> "RESOLVED"
                    "CANCELLED", "DECLINED", "CANCEL" -> "RECEIVED"
                    else -> null
                }
                val local = incidentDao.getIncidentById(incidentId.toString())
                if (local != null && incidentStatus != null) {
                    incidentDao.insertIncident(local.copy(status = incidentStatus))
                }
                Result.Success(true)
            } else {
                Result.Error(UserMessageResolver.http("update responder status", response.code()))
            }
        } catch (e: Exception) {
            Result.Error(UserMessageResolver.network("Update responder status", e))
        }
    }

    override suspend fun addIncident(incident: IncidentEntity) {
        incidentDao.insertIncident(incident)
    }

    override fun listenLiveIncidents() {
        if (liveIncidentJob?.isActive == true) {
            return
        }

        liveIncidentJob = scope.launch {
            var retryDelayMs = LIVE_RETRY_INITIAL_MS

            while (isActive) {
                try {
                    var receivedLiveEvent = false
                    realtimeSyncCoordinator.connect().collect { event ->
                        receivedLiveEvent = true
                        // Any successfully processed event proves the live channel
                        // is healthy again, so future reconnects restart at the
                        // shortest backoff rather than remaining artificially slow.
                        retryDelayMs = LIVE_RETRY_INITIAL_MS

                        when (event.type) {
                            "NEW_INCIDENT" -> {
                                val incident =
                                    json.decodeFromJsonElement<IncidentDto>(event.payload)
                                cacheIncidents(listOf(incident))
                            }

                            "INCIDENT_DEDUPLICATED" -> {
                                // A deduplication acknowledgement is not a new incident.
                                // The next REST catch-up enriches the existing logical row.
                                event.payload.jsonObject["incident"]?.let { raw ->
                                    runCatching {
                                        cacheIncidents(listOf(json.decodeFromJsonElement<IncidentDto>(raw)))
                                    }
                                }
                            }

                            "INCIDENT_STATUS_UPDATED" -> {
                                applyStatusEvent(event.payload.jsonObject)
                            }

                            "RESPONDER_LOCATION_UPDATED" -> {
                                applyResponderLocationEvent(event.payload.jsonObject)
                            }

                            // Canonical backend event plus the legacy event name for
                            // rolling upgrades. Persisting it in Room updates every
                            // open incident conversation immediately.
                            "INCIDENT_MESSAGE_CREATED", "INCIDENT_MESSAGE" -> {
                                val message = json.decodeFromJsonElement<IncidentMessageDto>(event.payload)
                                incidentMessagingRepository.ingestRealtimeMessage(message)
                            }
                        }
                    }
                    // A clean close can still hide events sent between the last
                    // frame and reconnect. Reconcile immediately before retrying.
                    if (receivedLiveEvent) fetchRemoteIncidents()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A dropped socket must not permanently disable live incident
                    // updates. Retry with bounded exponential backoff; periodic/
                    // manual REST refresh remains the authoritative catch-up path.
                }

                delay(retryDelayMs)
                retryDelayMs =
                    (retryDelayMs * 2L).coerceAtMost(LIVE_RETRY_MAX_MS)
            }
        }
    }

    private suspend fun applyResponderLocationEvent(payload: JsonObject) {
        val responderId = payload["responder_id"]?.jsonPrimitive?.longOrNull
            ?: payload["unit_id"]?.jsonPrimitive?.longOrNull
            ?: return
        val current = responderDao.getResponderById(responderId)
        val latitude = payload["latitude"]?.jsonPrimitive?.doubleOrNull ?: current?.currentLat ?: return
        val longitude = payload["longitude"]?.jsonPrimitive?.doubleOrNull ?: current?.currentLon ?: return
        val updated = (current ?: ResponderUnitEntity(
            id = responderId,
            agencyId = payload["agency_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            unitCallsign = payload["unit_callsign"]?.jsonPrimitive?.contentOrNull ?: "Responder $responderId",
            deviceId = payload["device_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )).copy(
            currentLat = latitude,
            currentLon = longitude,
            status = payload["status"]?.jsonPrimitive?.contentOrNull ?: current?.status ?: "AVAILABLE",
            lastSeenAt = payload["last_seen_at"]?.jsonPrimitive?.contentOrNull,
            lastSyncedAt = System.currentTimeMillis()
        )
        responderDao.insertResponder(updated)
    }

    private suspend fun applyStatusEvent(payload: JsonObject) {
        val incidentId = payload["incident_id"]?.jsonPrimitive?.longOrNull ?: return
        val status = payload["status"]?.jsonPrimitive?.contentOrNull
            ?: payload["new_status"]?.jsonPrimitive?.contentOrNull
            ?: return
        val local = incidentDao.getIncidentById(incidentId.toString())
            ?: incidentDao.getIncidentByServerId(incidentId)
            ?: incidentDao.getIncidentByLogicalId(incidentId.toString())
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
                logicalId = incidentId.toString(),
                serverIncidentId = incidentId,
                deliveryState = "RECEIVED_BY_COMMAND",
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
            altitude = altitude,
            gpsAccuracy = gpsAccuracy,
            timestamp = parseServerTimestamp(eventTimestamp ?: createdAt),
            status = status,
            packetHash = packetHash,
            logicalId = packetHash.ifBlank { id.toString() },
            serverIncidentId = id,
            dispatchedUnitId = dispatchedUnitId,
            deliveryState = "RECEIVED_BY_COMMAND",
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

    private fun retrofit2.Response<*>.backendError(prefix: String): String {
        val raw = runCatching { errorBody()?.string().orEmpty() }.getOrDefault("")
        val backendMessage = runCatching { JSONObject(raw).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (backendMessage != null) "$prefix: $backendMessage" else "$prefix: HTTP ${code()}"
    }

    companion object {
        private const val LIVE_RETRY_INITIAL_MS = 1_500L
        private const val LIVE_RETRY_MAX_MS = 30_000L
    }

}
