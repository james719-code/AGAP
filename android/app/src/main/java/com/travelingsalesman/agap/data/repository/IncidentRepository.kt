package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.core.network.LiveSyncState
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
    private val responderDao: ResponderDao
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

    override suspend fun cacheIncidents(
        incidents: List<IncidentDto>
    ) {
        if (incidents.isEmpty()) {
            return
        }

        val incoming =
            incidents.map {
                it.toEntity()
            }

        // SQLite has a finite bind-parameter budget. A large admin bootstrap or
        // post-outage sync can easily contain more incident IDs than one IN(...)
        // query should carry, so read the existing rows in bounded chunks.
        val existing = mutableListOf<IncidentEntity>()
        incoming
            .map { it.incidentId }
            .chunked(INCIDENT_ID_QUERY_CHUNK)
            .forEach { ids ->
                existing += incidentDao.getIncidentsByIds(ids)
            }
        val existingById = existing.associateBy { it.incidentId }

        /*
         * Critical performance fix:
         *
         * The live admin dashboard periodically polls as a WebSocket fallback.
         * Previously every poll REPLACED every returned Room row even when nothing
         * changed. That invalidated all incident observers and rebuilt lists/maps.
         *
         * Only new or semantically changed incidents are written now.
         */
        val changed = incoming.mapNotNull { remote ->
            val localByServerId = existingById[remote.incidentId]
            val localByLogicalId = remote.logicalId.takeIf { it.isNotBlank() }?.let {
                incidentDao.getIncidentByLogicalId(it)
            }
            val local = localByServerId ?: localByLogicalId

            when {
                local == null -> remote
                !local.hasSameRemoteContent(remote) || local.serverIncidentId != remote.serverIncidentId ->
                    // Keep the local placeholder primary key so a mesh row and
                    // its later numeric server row never become two UI items.
                    remote.copy(
                        incidentId = local.incidentId,
                        serverIncidentId = remote.serverIncidentId ?: remote.incidentId.toLongOrNull()
                    )
                else -> null
            }
        }

        if (changed.isNotEmpty()) {
            // Smaller transactions keep Room/SQLite responsive to the relay and
            // UI while a large server catch-up is being cached.
            changed.chunked(INCIDENT_WRITE_CHUNK).forEach { batch ->
                incidentDao.insertIncidents(batch)
            }
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
                val local = incidentDao.getIncidentById(id.toString())
                if (local != null) Result.Success(local)
                else Result.Error(UserMessageResolver.http("load this incident", response.code()))
            }
        } catch (e: Exception) {
            val local = incidentDao.getIncidentById(id.toString())
            if (local != null) Result.Success(local)
            else Result.Error(UserMessageResolver.network("Load this incident", e))
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
                Result.Error(UserMessageResolver.http("load incidents", response.code()))
            }
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
                    incidentDao.insertIncident(local.copy(status = "DISPATCHED"))
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
            deliveryState = "RECEIVED_BY_COMMAND",
            channel = channel,
            deliveryPath = deliveryPath,
            senderDeviceId = senderDeviceId,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun IncidentEntity.hasSameRemoteContent(
        other: IncidentEntity
    ): Boolean {
        return incidentId ==
                other.incidentId &&
                targetId ==
                other.targetId &&
                emergencyType ==
                other.emergencyType &&
                latitude ==
                other.latitude &&
                longitude ==
                other.longitude &&
                altitude ==
                other.altitude &&
                gpsAccuracy ==
                other.gpsAccuracy &&
                timestamp ==
                other.timestamp &&
                status ==
                other.status &&
                description ==
                other.description &&
                dispatcherNotes ==
                other.dispatcherNotes &&
                dispatchedUnitId ==
                other.dispatchedUnitId &&
                dispatchedAt ==
                other.dispatchedAt &&
                resolvedAt ==
                other.resolvedAt &&
                packetHash ==
                other.packetHash &&
                channel ==
                other.channel &&
                deliveryPath ==
                other.deliveryPath &&
                senderDeviceId ==
                other.senderDeviceId &&
                logicalId ==
                other.logicalId &&
                serverIncidentId ==
                other.serverIncidentId &&
                deliveryState ==
                other.deliveryState &&
                isDirty ==
                other.isDirty
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
        private const val INCIDENT_ID_QUERY_CHUNK = 400
        private const val INCIDENT_WRITE_CHUNK = 200
    }

}
