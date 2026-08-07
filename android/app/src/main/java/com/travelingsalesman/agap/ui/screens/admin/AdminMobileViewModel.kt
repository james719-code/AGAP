package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext

import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState

data class AdminMobileUiState(
    val isOnline: Boolean = true,
    val username: String? = null,
    val pendingPacketCount: Int = 0,
    val incidents: List<IncidentEntity> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val userLocation: Location? = null,
    val isRefreshing: Boolean = false,
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

    init {
        viewModelScope.launch {
            bleMeshManager.meshState.collect { bState ->
                _uiState.update { it.copy(bleMeshState = bState) }
            }
        }
        viewModelScope.launch {
            authRepository.username.collect { name ->
                _uiState.update { it.copy(username = name) }
            }
        }

        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }

        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                }
            } catch (e: Exception) {
                // Location permission may not be granted in the admin field view.
            }
        }

        viewModelScope.launch {
            telemetryRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }

        viewModelScope.launch {
            incidentRepository.getIncidents().collect { list ->
                val newActive = list.filter { it.status == "RECEIVED" && !previousIncidentIds.contains(it.incidentId) }
                if (previousIncidentIds.isNotEmpty()) {
                    newActive.forEach { incident ->
                        triggerSosNotification(incident)
                    }
                }
                previousIncidentIds = list.map { it.incidentId }.toSet()
                _uiState.update { it.copy(incidents = list) }
            }
        }

        fetchResponders()
    }

    private fun triggerSosNotification(incident: IncidentEntity) {
        try {
            val notification = NotificationCompat.Builder(context, "agap_sos_channel")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("CRITICAL SOS ALERT")
                .setContentText("Emergency #${incident.incidentId}: ${incident.emergencyType} distress signal received!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(incident.incidentId.hashCode(), notification)
        } catch (e: Exception) {
            // Permission or notification build error
        }
    }

    fun fetchResponders() {
        viewModelScope.launch {
            when (val res = responderRepository.getResponders()) {
                is Result.Success -> {
                    _uiState.update { it.copy(responders = res.data) }
                }
                else -> {}
            }
        }
    }

    fun openDispatchSheet(incidentId: String) {
        fetchResponders()
        _uiState.update { it.copy(selectedIncidentIdForDispatch = incidentId) }
    }

    fun closeDispatchSheet() {
        _uiState.update { it.copy(selectedIncidentIdForDispatch = null) }
    }

    fun dispatchResponder(incidentId: String, unitId: Long) {
        val idLong = incidentId.toLongOrNull()
        if (idLong == null) {
            _uiState.update {
                it.copy(
                    selectedIncidentIdForDispatch = null,
                    feedbackMessage = "Dispatch unavailable until Incident #$incidentId is synced with the server"
                )
            }
            return
        }
        viewModelScope.launch {
            val result = incidentRepository.dispatchIncident(idLong, unitId)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedIncidentIdForDispatch = null,
                            feedbackMessage = "Dispatched Unit #$unitId to Incident #$incidentId"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            selectedIncidentIdForDispatch = null,
                            feedbackMessage = "Dispatch failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun resolveIncident(incidentId: String) {
        val idLong = incidentId.toLongOrNull()
        if (idLong == null) {
            _uiState.update { it.copy(feedbackMessage = "Resolve unavailable until Incident #$incidentId is synced with the server") }
            return
        }
        viewModelScope.launch {
            incidentRepository.updateStatus(idLong, "RESOLVED")
            _uiState.update { it.copy(feedbackMessage = "Incident #$incidentId marked RESOLVED") }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
