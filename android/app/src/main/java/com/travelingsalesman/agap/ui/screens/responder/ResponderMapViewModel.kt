package com.travelingsalesman.agap.ui.screens.responder

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.notification.EmergencyNotificationCoordinator
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.routing.OfflineRouteEngine
import com.travelingsalesman.agap.core.routing.OfflineRouteResult
import com.travelingsalesman.agap.core.routing.RouteCoordinate
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.NotificationRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResponderMapUiState(
    val incidents: List<IncidentEntity> = emptyList(),
    val assignedDispatches: List<DispatchDto> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val myUnit: ResponderUnitDto? = null,
    val userLocation: Location? = null,
    val responderStatus: String = "IDLE", // IDLE, DISPATCHED, OFFLINE
    val agencyId: Long? = null,
    val userId: Long? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingDispatches: Boolean = false,
    val feedbackMessage: String? = null,
    val isOnline: Boolean = true,
    val routingIncidentId: String? = null,
    val activeRoute: OfflineRouteResult? = null,
    val isCalculatingRoute: Boolean = false,
    val bleMeshState: BleMeshState = BleMeshState(),
    val unreadNotificationCount: Int = 0,
    val pendingPacketCount: Int = 0
)

@HiltViewModel
class ResponderMapViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val telemetryRepository: TelemetryRepository,
    private val responderRepository: ResponderRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val bleMeshManager: BleMeshManager,
    private val emergencyNotifications: EmergencyNotificationCoordinator,
    private val networkMonitor: NetworkMonitor,
    private val offlineRouteEngine: OfflineRouteEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResponderMapUiState())
    val uiState: StateFlow<ResponderMapUiState> = _uiState.asStateFlow()

    private var previousIncidentIds = emptySet<String>()
    private var incidentObservationInitialized = false
    private var routeJob: Job? = null
    private var dispatchRefreshJob: Job? = null
    private var routingPrefetchJob: Job? = null
    private var lastRouteStart: RouteCoordinate? = null
    private var lastRouteIncidentId: String? = null

    init {
        viewModelScope.launch {
            bleMeshManager.meshState.collect { bState ->
                _uiState.update { it.copy(bleMeshState = bState) }
            }
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (online) {
                    prefetchDeploymentRoads()
                }
            }
        }
        // Fetch current user's responder unit details from API
        fetchMyResponderUnit()
        fetchRemoteIncidents()
        startDispatchRefreshLoop()
        observeNotifications()
        observePendingPacketCount()

        // Observe auth agencyId and userId
        viewModelScope.launch {
            authRepository.agencyId.collect { aid ->
                _uiState.update { it.copy(agencyId = aid) }
                fetchResponders(aid)
            }
        }
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                _uiState.update { it.copy(userId = uid) }
            }
        }

        // Observe live incidents from Room / WebSocket combined with local QUEUED pending packets
        viewModelScope.launch {
            combine(
                incidentRepository
                    .getRecentActiveIncidents(
                        RESPONDER_INCIDENT_WORKING_SET
                    )
                    .distinctUntilChanged(),

                telemetryRepository
                    .getRecentPendingPacketsFlow(
                        RESPONDER_PENDING_WORKING_SET
                    )
                    .distinctUntilChanged()
            ) { dbIncidents, pendingPackets ->
                val queuedEntities = pendingPackets.map { pkt ->
                    IncidentEntity(
                        incidentId = pkt.incidentUuid,
                        targetId = pkt.targetId,
                        emergencyType = pkt.emergencyType,
                        latitude = pkt.latitude,
                        longitude = pkt.longitude,
                        timestamp = pkt.timestamp,
                        status = "QUEUED",
                        packetHash = pkt.packetHash,
                        channel = "BLE_MESH",
                        deliveryPath = "BLE_CLOUD_SHORTCUT",
                        lastSyncedAt = pkt.createdAt
                    )
                }
                val existingKeys = dbIncidents.map { it.incidentId }.toSet()
                val newQueued = queuedEntities.filter { it.incidentId !in existingKeys }
                newQueued + dbIncidents
            }.collect { mergedIncidents ->
                val currentIds = mergedIncidents.map { it.incidentId }.toSet()

                if (incidentObservationInitialized) {
                    val newIncidents = mergedIncidents.filter { incident ->
                        incident.incidentId !in previousIncidentIds &&
                            !incident.status.equals("QUEUED", ignoreCase = true)
                    }
                    if (newIncidents.isNotEmpty()) {
                        emergencyNotifications.recordIncidents(
                            incidents = newIncidents,
                            pendingCount = mergedIncidents.count { incident ->
                                incident.status.equals("RECEIVED", ignoreCase = true) ||
                                    incident.status.equals("DISPATCHED", ignoreCase = true) ||
                                    incident.status.equals("EN_ROUTE", ignoreCase = true)
                            }
                        )
                    }
                }

                previousIncidentIds = currentIds
                incidentObservationInitialized = true

                _uiState.update { it.copy(incidents = mergedIncidents) }
            }
        }

        // Observe responder location
        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { loc ->
                    _uiState.update { it.copy(userLocation = loc) }
                }
            } catch (e: Exception) {
                // Location permissions
            }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
    }

    private fun observePendingPacketCount() {
        viewModelScope.launch {
            telemetryRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }
    }

    private fun prefetchDeploymentRoads() {
        if (routingPrefetchJob?.isActive == true) return
        routingPrefetchJob = viewModelScope.launch {
            offlineRouteEngine.prefetchActiveMapRoads(allowNetwork = true)
        }
    }

    /**
     * The current backend exposes responder assignments as a REST list rather than
     * a dedicated dispatch WebSocket event. Keep a lightweight foreground-scoped
     * poll so an admin dispatch appears on the responder map without requiring a
     * manual pull-to-refresh.
     */
    private fun startDispatchRefreshLoop() {
        dispatchRefreshJob?.cancel()
        dispatchRefreshJob = viewModelScope.launch {
            while (isActive) {
                if (
                    _uiState.value.isOnline &&
                    !ServerConfigManager.isBleOnlyActive &&
                    _uiState.value.myUnit != null
                ) {
                    when (val result = responderRepository.getMyDispatches()) {
                        is Result.Success -> {
                            _uiState.update { state ->
                                val hasActiveDispatch = result.data.any { dispatch ->
                                    dispatch.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
                                }
                                val unitStatus = when {
                                    hasActiveDispatch -> "DISPATCHED"
                                    state.responderStatus == "OFFLINE" -> "OFFLINE"
                                    else -> "IDLE"
                                }
                                if (state.assignedDispatches == result.data && state.responderStatus == unitStatus) {
                                    state
                                } else {
                                    state.copy(
                                        assignedDispatches = result.data,
                                        responderStatus = unitStatus
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                delay(DISPATCH_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                fetchMyResponderUnit()
                fetchRemoteIncidents()
                _uiState.value.agencyId?.let { fetchResponders(it) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun fetchMyResponderUnit() {
        viewModelScope.launch {
            when (val res = responderRepository.getMyResponderUnit()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            myUnit = res.data,
                            responderStatus = normalizeResponderStatusValue(res.data.status)
                        )
                    }
                    fetchMyDispatches()
                }
                else -> {}
            }
        }
    }

    private fun fetchRemoteIncidents() {
        viewModelScope.launch {
            incidentRepository.fetchRemoteIncidents()
        }
    }

    fun fetchMyDispatches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDispatches = true) }
            when (val result = responderRepository.getMyDispatches()) {
                is Result.Success -> {
                    _uiState.update { state ->
                        val hasActiveDispatch = result.data.any { dispatch ->
                            dispatch.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
                        }
                        state.copy(
                            isLoadingDispatches = false,
                            assignedDispatches = result.data,
                            responderStatus = when {
                                hasActiveDispatch -> "DISPATCHED"
                                state.responderStatus == "OFFLINE" -> "OFFLINE"
                                else -> "IDLE"
                            }
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            feedbackMessage = "Assignments unavailable: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoadingDispatches = false) }
                }
            }
        }
    }

    fun fetchResponders(agencyId: Long? = null) {
        viewModelScope.launch {
            val result = responderRepository.getResponders(agencyId)
            if (result is Result.Success) {
                _uiState.update { it.copy(responders = result.data) }
            }
        }
    }

    fun updateMyStatus(newStatus: String) {
        val normalizedStatus = normalizeResponderStatusValue(newStatus)
        if (normalizedStatus !in setOf("IDLE", "OFFLINE")) {
            _uiState.update {
                it.copy(feedbackMessage = "Unit status can only be IDLE or OFFLINE here. Use dispatch controls for EN_ROUTE / ON_SCENE.")
            }
            return
        }

        val state = _uiState.value
        val hasActiveDispatch = state.assignedDispatches.any { dispatch ->
            dispatch.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
        }
        if (hasActiveDispatch && normalizedStatus == "IDLE") {
            _uiState.update {
                it.copy(feedbackMessage = "This unit has an active dispatch and cannot be set to IDLE yet.")
            }
            return
        }
        val unit = state.myUnit
        val unitId = unit?.id ?: state.userId ?: return
        val loc = state.userLocation
        val fallbackLat = unit?.currentLat?.takeIf { it != 0.0 }
        val fallbackLon = unit?.currentLon?.takeIf { it != 0.0 }
        val lat = loc?.latitude ?: fallbackLat
        val lon = loc?.longitude ?: fallbackLon

        if (lat == null || lon == null) {
            _uiState.update {
                it.copy(feedbackMessage = "GPS location is not available yet. Status was not sent with a fake position.")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val result = responderRepository.updateLocation(unitId, lat, lon, normalizedStatus)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            responderStatus = normalizedStatus,
                            myUnit = it.myUnit?.copy(status = normalizedStatus),
                            feedbackMessage = "Responder status set to $normalizedStatus"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun updateDispatchStatus(dispatch: DispatchDto, newStatus: String) {
        _uiState.update { it.copy(isLoadingDispatches = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.updateDispatchStatus(dispatch.incidentId, dispatch.id, newStatus)) {
                is Result.Success -> {
                    val currentUnitStatus = normalizeResponderStatusValue(_uiState.value.responderStatus)
                    val unitStatus = when (newStatus.uppercase()) {
                        "ASSIGNED", "ACCEPTED", "EN_ROUTE", "ON_SCENE" -> "DISPATCHED"
                        "COMPLETED", "CANCELLED" -> if (currentUnitStatus == "OFFLINE") "OFFLINE" else "IDLE"
                        else -> currentUnitStatus
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            responderStatus = unitStatus,
                            myUnit = it.myUnit?.copy(status = unitStatus),
                            assignedDispatches = it.assignedDispatches.map { item ->
                                if (item.id == dispatch.id) item.copy(status = newStatus) else item
                            },
                            feedbackMessage = "Dispatch updated to $newStatus"
                        )
                    }
                    fetchRemoteIncidents()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun sendBleAcknowledgment(dispatch: DispatchDto, incident: IncidentEntity?) {
        val incidentIdentifier = incident?.packetHash?.takeIf { it.isNotBlank() }
            ?: incident?.incidentId
            ?: dispatch.incidentId.toString()
        val emergencyType = incident?.emergencyType ?: "MEDICAL"

        bleMeshManager.startAdvertisingAcknowledgment(
            incidentIdentifier = incidentIdentifier,
            emergencyType = emergencyType,
            messageCode = 1
        )
        _uiState.update {
            it.copy(feedbackMessage = "BLE acknowledgment queued for GATT relay for Incident #${dispatch.incidentId}")
        }
    }

    /**
     * Recalculates the responder's assigned route when the target changes or the
     * responder has moved far enough to justify rerouting. GPS updates can arrive
     * frequently, so A* is deliberately not run for every location sample.
     */
    fun calculateRouteTo(incident: IncidentEntity?) {
        if (incident == null) {
            routeJob?.cancel()
            lastRouteIncidentId = null
            lastRouteStart = null
            _uiState.update {
                it.copy(
                    routingIncidentId = null,
                    activeRoute = null,
                    isCalculatingRoute = false
                )
            }
            return
        }

        val state = _uiState.value
        val start = state.userLocation?.let {
            RouteCoordinate(it.latitude, it.longitude)
        } ?: state.myUnit?.let { unit ->
            if (unit.currentLat != 0.0 || unit.currentLon != 0.0) {
                RouteCoordinate(unit.currentLat, unit.currentLon)
            } else {
                null
            }
        }

        if (start == null) {
            _uiState.update {
                it.copy(
                    routingIncidentId = incident.incidentId,
                    activeRoute = null,
                    isCalculatingRoute = false
                )
            }
            return
        }

        val targetChanged = lastRouteIncidentId != incident.incidentId
        val movedMeters = lastRouteStart?.let { previous ->
            val out = FloatArray(1)
            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                start.latitude,
                start.longitude,
                out
            )
            out[0].toDouble()
        } ?: Double.POSITIVE_INFINITY

        val shouldRetryFallback =
            state.activeRoute?.isRoadRoute == false && state.isOnline

        if (
            !targetChanged &&
            movedMeters < ROUTE_RECALCULATE_DISTANCE_METERS &&
            state.activeRoute != null &&
            !shouldRetryFallback
        ) {
            return
        }

        routeJob?.cancel()
        lastRouteIncidentId = incident.incidentId
        lastRouteStart = start
        _uiState.update {
            it.copy(
                routingIncidentId = incident.incidentId,
                isCalculatingRoute = true
            )
        }

        routeJob = viewModelScope.launch {
            val result = offlineRouteEngine.route(
                start = start,
                end = RouteCoordinate(incident.latitude, incident.longitude),
                allowNetwork = _uiState.value.isOnline
            )

            if (_uiState.value.routingIncidentId == incident.incidentId) {
                _uiState.update {
                    it.copy(
                        activeRoute = result,
                        isCalculatingRoute = false
                    )
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    companion object {
        private const val ROUTE_RECALCULATE_DISTANCE_METERS = 50.0
        private const val DISPATCH_REFRESH_INTERVAL_MS = 5_000L
        private const val RESPONDER_INCIDENT_WORKING_SET = 250
        private const val RESPONDER_PENDING_WORKING_SET = 100
    }


}