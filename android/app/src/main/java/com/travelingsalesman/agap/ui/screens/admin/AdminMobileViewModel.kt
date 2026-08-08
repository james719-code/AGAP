package com.travelingsalesman.agap.ui.screens.admin

import android.app.NotificationManager
import android.content.Context
import android.location.Location
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * Background 10-second polling is intentionally silent so the dashboard does not flash.
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminMobileUiState())
    val uiState: StateFlow<AdminMobileUiState> = _uiState.asStateFlow()

    private var previousIncidentIds = setOf<String>()
    private var incidentObservationInitialized = false

    private var autoRefreshJob: Job? = null
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

                // If the dashboard is currently visible and connectivity comes back,
                // synchronize immediately rather than waiting for the next 10-second tick.
                if (isOnline && wasOffline && _uiState.value.autoRefreshActive) {
                    refreshCommandData(
                        showLoading = false,
                        refreshResponders = true
                    )
                }
            }
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
            telemetryRepository.getPendingCount().collect { count ->
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
                        list.asSequence()
                            .filter { incident ->
                                incident.status.equals("RECEIVED", ignoreCase = true) &&
                                        incident.incidentId !in previousIncidentIds
                            }
                            .forEach(::triggerSosNotification)
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
     * every 10 seconds -> REST list incidents -> Room -> Flow -> UI
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

    private fun triggerSosNotification(incident: IncidentEntity) {
        try {
            val notification = NotificationCompat.Builder(
                context,
                "agap_sos_channel"
            )
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("CRITICAL SOS ALERT")
                .setContentText(
                    "Emergency #${incident.incidentId}: " +
                            "${incident.emergencyType} distress signal received!"
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.notify(
                incident.incidentId.hashCode(),
                notification
            )
        } catch (_: Exception) {
            // Notification permission/build errors must never break command data observation.
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
        fetchResponders()

        _uiState.update {
            it.copy(
                selectedIncidentIdForDispatch = incidentId
            )
        }
    }

    fun closeDispatchSheet() {
        _uiState.update {
            it.copy(selectedIncidentIdForDispatch = null)
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
                    _uiState.update {
                        it.copy(
                            selectedIncidentIdForDispatch = null,
                            feedbackMessage =
                                "Dispatch failed: ${result.message}"
                        )
                    }
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
         * This 10-second poll is only a reliability fallback.
         */
        private const val INCIDENT_REFRESH_INTERVAL_MS = 10_000L

        /**
         * 3 x 10 seconds = responder API refresh every ~30 seconds.
         */
        private const val RESPONDER_REFRESH_EVERY_N_TICKS = 3

        /**
         * Admin Mobile is a live command dashboard, not the full archive.
         */
        private const val DASHBOARD_INCIDENT_WORKING_SET = 300
    }
}