package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.notification.EmergencyNotificationCoordinator
import com.travelingsalesman.agap.core.routing.OfflineRouteEngine
import com.travelingsalesman.agap.core.routing.OfflineRouteResult
import com.travelingsalesman.agap.core.routing.RouteCoordinate
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class AdminMobileUiState(
    val isOnline: Boolean = true,
    val username: String? = null,
    val pendingPacketCount: Int = 0,
    val incidents: List<IncidentEntity> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val userLocation: Location? = null,

    /**
     * Used only for an explicit/initial foreground refresh.
     * Background 30-second polling is intentionally silent so the dashboard does not flash.
     */
    val isRefreshing: Boolean = false,

    /**
     * True only while this screen is visible and the fallback polling job is active.
     */
    val autoRefreshActive: Boolean = false,

    /**
     * Last successful incident API synchronization time.
     * WebSocket/Room updates may still arrive between these polling timestamps.
     */
    val lastDataRefreshAt: Long? = null,

    val selectedIncidentIdForDispatch: String? = null,
    val availableDispatchResponders: List<ResponderUnitDto> = emptyList(),
    val isLoadingDispatchResponders: Boolean = false,

    /** Responder/incident pair currently previewed on the command map. */
    val dispatchRouteIncidentId: String? = null,
    val dispatchRouteResponderId: Long? = null,
    val dispatchRoute: OfflineRouteResult? = null,
    val isCalculatingDispatchRoute: Boolean = false,

    val feedbackMessage: String? = null,
    val bleMeshState: BleMeshState = BleMeshState()
)

@HiltViewModel
class AdminMobileViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val telemetryRepository: TelemetryRepository,
    private val networkMonitor: NetworkMonitor,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler,
    private val emergencyNotifications: EmergencyNotificationCoordinator,
    private val offlineRouteEngine: OfflineRouteEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminMobileUiState())
    val uiState: StateFlow<AdminMobileUiState> = _uiState.asStateFlow()

    private var previousIncidentIds = setOf<String>()
    private var incidentObservationInitialized = false

    private var autoRefreshJob: Job? = null
    private var dispatchRouteJob: Job? = null
    private var routingPrefetchJob: Job? = null
    private val refreshMutex = Mutex()

    init {
        observeMesh()
        observeUser()
        observeConnectivity()
        observeLocation()
        observePendingPackets()
        observeIncidents()

        // Room/cache is rendered immediately. The network refresh begins only when the
        // Admin Mobile screen becomes visible via startAutoRefresh().
        fetchResponders()
    }

    private fun observeMesh() {
        viewModelScope.launch {
            bleMeshManager.meshState.collect { meshState ->
                _uiState.update { it.copy(bleMeshState = meshState) }
            }
        }
    }

    private fun observeUser() {
        viewModelScope.launch {
            authRepository.username.collect { name ->
                _uiState.update { it.copy(username = name) }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = isOnline) }

                if (isOnline) {
                    prefetchDeploymentRoads()
                }

                // If the dashboard is currently visible and connectivity comes back,
                // synchronize immediately rather than waiting for the next fallback tick.
                if (isOnline && wasOffline && _uiState.value.autoRefreshActive) {
                    refreshCommandData(
                        showLoading = false,
                        refreshResponders = true
                    )
                }
            }
        }
    }

    private fun prefetchDeploymentRoads() {
        if (routingPrefetchJob?.isActive == true) return
        routingPrefetchJob = viewModelScope.launch {
            offlineRouteEngine.prefetchActiveMapRoads(allowNetwork = true)
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                }
            } catch (_: Exception) {
                // Location permission may not be granted in the admin field view.
            }
        }
    }

    private fun observePendingPackets() {
        viewModelScope.launch {
            combine(
                telemetryRepository.getPendingCount(),
                relayScheduler.observeQueuedRelayCount()
            ) { cloudPending, meshPending ->
                cloudPending + meshPending
            }.distinctUntilChanged().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }
    }

    /**
     * Room is the source of truth for what Compose renders.
     *
     * NEW_INCIDENT WebSocket events are already written into Room by IncidentRepository,
     * so a newly received accident can appear on this dashboard immediately without
     * waiting for the fallback poll.
     */
    private fun observeIncidents() {
        viewModelScope.launch {
            incidentRepository
                .getRecentIncidents(
                    DASHBOARD_INCIDENT_WORKING_SET
                )
                .distinctUntilChanged()
                .collect { list ->
                    val currentIds = list.map { it.incidentId }.toSet()

                    if (incidentObservationInitialized) {
                        val newIncidents = list.filter { incident ->
                            incident.status.equals("RECEIVED", ignoreCase = true) &&
                                incident.incidentId !in previousIncidentIds
                        }
                        if (newIncidents.isNotEmpty()) {
                            emergencyNotifications.recordIncidents(
                                incidents = newIncidents,
                                pendingCount = list.count {
                                    it.status.equals("RECEIVED", ignoreCase = true)
                                }
                            )
                        }
                    }

                    previousIncidentIds = currentIds
                    incidentObservationInitialized = true

                    _uiState.update {
                        it.copy(incidents = list)
                    }
                }
        }
    }

    /**
     * Called by AdminMobileScreen while it is visible.
     *
     * Live path:
     * WebSocket -> IncidentRepository -> Room -> Flow -> UI
     *
     * Reliability fallback:
     * every 30 seconds -> REST list incidents -> Room -> Flow -> UI
     *
     * The map tiles are NOT refreshed here. Map caching is controlled exclusively in
     * AdminMobileScreen/OsmdroidMapView by forcing isOnline = false for the base map.
     */
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return

        _uiState.update {
            it.copy(autoRefreshActive = true)
        }

        autoRefreshJob = viewModelScope.launch {
            // Synchronize as soon as the operator opens this screen.
            refreshCommandData(
                showLoading = _uiState.value.incidents.isEmpty(),
                refreshResponders = true
            )

            var tick = 0

            while (isActive) {
                delay(INCIDENT_REFRESH_INTERVAL_MS)

                if (!_uiState.value.isOnline) {
                    continue
                }

                tick++

                // Incidents are high priority: refresh every tick.
                // Responder roster changes less frequently, so refresh it every third tick.
                refreshCommandData(
                    showLoading = false,
                    refreshResponders = tick % RESPONDER_REFRESH_EVERY_N_TICKS == 0
                )
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null

        _uiState.update {
            it.copy(autoRefreshActive = false, isRefreshing = false)
        }
    }

    /**
     * Optional explicit refresh hook if you later add a swipe-to-refresh or toolbar action.
     */
    fun refreshNow() {
        viewModelScope.launch {
            refreshCommandData(
                showLoading = true,
                refreshResponders = true
            )
        }
    }

    private suspend fun refreshCommandData(
        showLoading: Boolean,
        refreshResponders: Boolean
    ) {
        if (!_uiState.value.isOnline) return

        refreshMutex.withLock {
            if (showLoading) {
                _uiState.update { it.copy(isRefreshing = true) }
            }

            val incidentResult = incidentRepository.fetchRemoteIncidents(
                type = null,
                status = null
            )

            var respondersResult: Result<List<ResponderUnitDto>>? = null
            if (refreshResponders) {
                respondersResult = responderRepository.getResponders()
            }

            when (respondersResult) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(responders = respondersResult.data)
                    }
                }
                else -> Unit
            }

            when (incidentResult) {
                is Result.Success -> {
                    // fetchRemoteIncidents() already writes the response into Room.
                    // The Room Flow above updates incidents/stats/cards automatically.
                    _uiState.update {
                        it.copy(
                            lastDataRefreshAt = System.currentTimeMillis()
                        )
                    }
                }

                is Result.Error -> {
                    // Keep showing cached Room data. Do not clear the dashboard on a network miss.
                }

                Result.Loading -> Unit
            }

            if (showLoading) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }


    fun fetchResponders() {
        viewModelScope.launch {
            when (val result = responderRepository.getResponders()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(responders = result.data)
                    }
                }
                else -> Unit
            }
        }
    }

    fun openDispatchSheet(incidentId: String) {
        dispatchRouteJob?.cancel()
        _uiState.update {
            it.copy(
                selectedIncidentIdForDispatch = incidentId,
                availableDispatchResponders = emptyList(),
                isLoadingDispatchResponders = true,
                dispatchRouteIncidentId = incidentId,
                dispatchRouteResponderId = null,
                dispatchRoute = null,
                isCalculatingDispatchRoute = false
            )
        }
        loadAvailableDispatchResponders(incidentId)
    }

    private fun loadAvailableDispatchResponders(incidentId: String) {
        val serverId = incidentId.toLongOrNull()
        if (serverId == null) {
            _uiState.update {
                it.copy(
                    availableDispatchResponders = emptyList(),
                    isLoadingDispatchResponders = false,
                    feedbackMessage = "Responder assignment is available after this incident is synced with the server"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDispatchResponders = true) }
            when (val result = incidentRepository.fetchAvailableResponders(serverId)) {
                is Result.Success -> {
                    val selectedId = _uiState.value.dispatchRouteResponderId
                    val selectedStillAvailable = selectedId == null || result.data.any { it.id == selectedId }
                    _uiState.update { state ->
                        state.copy(
                            availableDispatchResponders = result.data,
                            isLoadingDispatchResponders = false,
                            dispatchRouteResponderId = if (selectedStillAvailable) state.dispatchRouteResponderId else null,
                            dispatchRoute = if (selectedStillAvailable) state.dispatchRoute else null,
                            isCalculatingDispatchRoute = if (selectedStillAvailable) state.isCalculatingDispatchRoute else false
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            availableDispatchResponders = emptyList(),
                            isLoadingDispatchResponders = false,
                            feedbackMessage = result.message ?: "Unable to load available responder units"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    /**
     * Selecting a unit is now a preview action, not an immediate dispatch.
     * A* runs against the cached OSM road graph. The preferred graph is a
     * backend-prepared SQLite package downloaded once for the deployment area;
     * a small Overpass corridor is only a compatibility fallback.
     */
    fun previewResponderRoute(
        incidentId: String,
        unitId: Long
    ) {
        val incident = _uiState.value.incidents.firstOrNull {
            it.incidentId == incidentId
        }
        val responder = _uiState.value.availableDispatchResponders.firstOrNull {
            it.id == unitId
        }

        if (incident == null || responder == null) {
            _uiState.update {
                it.copy(feedbackMessage = "Unable to preview route: incident or responder is unavailable")
            }
            return
        }

        val responderStatus = normalizeResponderStatusValue(responder.status)
        if (responderStatus != "IDLE") {
            _uiState.update {
                it.copy(
                    dispatchRouteResponderId = null,
                    dispatchRoute = null,
                    isCalculatingDispatchRoute = false,
                    feedbackMessage = "${responder.unitCallsign} is $responderStatus and cannot be assigned"
                )
            }
            loadAvailableDispatchResponders(incidentId)
            return
        }

        if (
            (incident.latitude == 0.0 && incident.longitude == 0.0) ||
            (responder.currentLat == 0.0 && responder.currentLon == 0.0)
        ) {
            _uiState.update {
                it.copy(
                    dispatchRouteIncidentId = incidentId,
                    dispatchRouteResponderId = unitId,
                    dispatchRoute = null,
                    isCalculatingDispatchRoute = false,
                    feedbackMessage = "Responder location is unavailable for route preview"
                )
            }
            return
        }

        dispatchRouteJob?.cancel()
        _uiState.update {
            it.copy(
                dispatchRouteIncidentId = incidentId,
                dispatchRouteResponderId = unitId,
                dispatchRoute = null,
                isCalculatingDispatchRoute = true
            )
        }

        dispatchRouteJob = viewModelScope.launch {
            val route = offlineRouteEngine.route(
                start = RouteCoordinate(responder.currentLat, responder.currentLon),
                end = RouteCoordinate(incident.latitude, incident.longitude),
                allowNetwork = _uiState.value.isOnline
            )

            // Ignore a stale calculation if the operator selected another unit.
            if (
                _uiState.value.dispatchRouteIncidentId == incidentId &&
                _uiState.value.dispatchRouteResponderId == unitId
            ) {
                _uiState.update {
                    it.copy(
                        dispatchRoute = route,
                        isCalculatingDispatchRoute = false
                    )
                }
            }
        }
    }

    fun closeDispatchSheet() {
        dispatchRouteJob?.cancel()
        _uiState.update {
            it.copy(
                selectedIncidentIdForDispatch = null,
                availableDispatchResponders = emptyList(),
                isLoadingDispatchResponders = false,
                dispatchRouteIncidentId = null,
                dispatchRouteResponderId = null,
                dispatchRoute = null,
                isCalculatingDispatchRoute = false
            )
        }
    }

    fun clearCommandRoute() {
        if (_uiState.value.selectedIncidentIdForDispatch != null) return
        dispatchRouteJob?.cancel()
        _uiState.update {
            it.copy(
                dispatchRouteIncidentId = null,
                dispatchRouteResponderId = null,
                dispatchRoute = null,
                isCalculatingDispatchRoute = false
            )
        }
    }

    fun dispatchResponder(
        incidentId: String,
        unitId: Long
    ) {
        val idLong = incidentId.toLongOrNull()

        if (idLong == null) {
            _uiState.update {
                it.copy(
                    selectedIncidentIdForDispatch = null,
                    feedbackMessage =
                        "Dispatch unavailable until Incident #$incidentId is synced with the server"
                )
            }
            return
        }

        val selectedUnit = _uiState.value.availableDispatchResponders.firstOrNull { it.id == unitId }
        if (selectedUnit == null || normalizeResponderStatusValue(selectedUnit.status) != "IDLE") {
            _uiState.update {
                it.copy(feedbackMessage = "That responder is no longer IDLE. Choose another available unit.")
            }
            loadAvailableDispatchResponders(incidentId)
            return
        }

        viewModelScope.launch {
            when (
                val result =
                    incidentRepository.dispatchIncident(
                        idLong,
                        unitId
                    )
            ) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedIncidentIdForDispatch = null,
                            availableDispatchResponders = emptyList(),
                            isLoadingDispatchResponders = false,
                            feedbackMessage =
                                "Dispatched Unit #$unitId to Incident #$incidentId"
                        )
                    }

                    // Local Room is already updated by the repository, but perform a silent
                    // server sync so the rest of the command state quickly converges too.
                    refreshCommandData(
                        showLoading = false,
                        refreshResponders = true
                    )
                }

                is Result.Error -> {
                    // Keep the chooser and calculated route open so command can
                    // retry or select another unit without losing the preview.
                    _uiState.update {
                        it.copy(
                            feedbackMessage =
                                "Dispatch failed: ${result.message}"
                        )
                    }
                    loadAvailableDispatchResponders(incidentId)
                }

                Result.Loading -> Unit
            }
        }
    }

    fun resolveIncident(incidentId: String) {
        val idLong = incidentId.toLongOrNull()

        if (idLong == null) {
            _uiState.update {
                it.copy(
                    feedbackMessage =
                        "Resolve unavailable until Incident #$incidentId is synced with the server"
                )
            }
            return
        }

        viewModelScope.launch {
            when (
                val result =
                    incidentRepository.updateStatus(
                        idLong,
                        "RESOLVED"
                    )
            ) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            feedbackMessage =
                                "Incident #$incidentId marked RESOLVED"
                        )
                    }

                    refreshCommandData(
                        showLoading = false,
                        refreshResponders = false
                    )
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            feedbackMessage =
                                "Resolve failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update {
            it.copy(feedbackMessage = null)
        }
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        super.onCleared()
    }

    companion object {
        /**
         * WebSocket should normally make updates effectively immediate.
         * This 30-second poll is only a reliability fallback.
         */
        private const val INCIDENT_REFRESH_INTERVAL_MS = 30_000L

        /**
         * 4 x 30 seconds = responder API refresh every ~2 minutes.
         */
        private const val RESPONDER_REFRESH_EVERY_N_TICKS = 4

        /**
         * Admin Mobile is a live command dashboard, not the full archive.
         */
        private const val DASHBOARD_INCIDENT_WORKING_SET = 300
    }
}