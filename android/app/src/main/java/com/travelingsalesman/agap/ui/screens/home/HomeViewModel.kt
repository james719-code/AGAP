package com.travelingsalesman.agap.ui.screens.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import com.travelingsalesman.agap.domain.usecase.SendSosUseCase
import com.travelingsalesman.agap.location.LocationRepository
import com.travelingsalesman.agap.ui.components.EmergencyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity

import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode

import com.travelingsalesman.agap.core.network.WifiHotspotMonitor

data class HomeUiState(
    val isOnline: Boolean = true,
    val selectedEmergencyType: EmergencyType = EmergencyType.MEDICAL,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val pendingPacketCount: Int = 0,
    val userLocation: Location? = null,
    val incidents: List<IncidentEntity> = emptyList(),
    val activeIncident: IncidentEntity? = null,
    val bleMeshState: BleMeshState = BleMeshState(),
    val isSendingSos: Boolean = false,
    val sosFeedbackMessage: String? = null,
    val username: String? = null,
    val role: String? = null,
    val userDisplayId: String? = null,
    val isWifiHotspotBroadcasting: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sendSosUseCase: SendSosUseCase,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase,
    private val networkMonitor: NetworkMonitor,
    private val wifiHotspotMonitor: WifiHotspotMonitor,
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val locationRepository: LocationRepository,
    private val authRepository: AuthRepository,
    private val bleMeshManager: BleMeshManager,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentActiveIncidentId: String? = null

    init {
        // Start BLE scanning and mesh services
        bleMeshManager.startMeshServices()

        // Observe Transmission Mode preference
        viewModelScope.launch {
            serverConfigManager.transmissionMode.collect { mode ->
                _uiState.update { it.copy(transmissionMode = mode) }
            }
        }

        // Observe BLE mesh state
        viewModelScope.launch {
            bleMeshManager.meshState.collect { bState ->
                _uiState.update { it.copy(bleMeshState = bState) }
            }
        }

        // Observe Wi-Fi open hotspot broadcasting state for relay
        viewModelScope.launch {
            wifiHotspotMonitor.isHotspotActive.collect { active ->
                _uiState.update { it.copy(isWifiHotspotBroadcasting = active) }
            }
        }

        // Observe auth user session combined
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                authRepository.username,
                authRepository.userRole,
                authRepository.userDisplayId
            ) { name, r, id ->
                Triple(name, r, id)
            }.collect { (name, r, id) ->
                _uiState.update { it.copy(username = name, role = r, userDisplayId = id) }
            }
        }


        // Observe network state and trigger auto-flush when online
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
                if (isOnline) {
                    flushPendingPacketsUseCase()
                }
            }
        }

        // Observe pending packet count
        viewModelScope.launch {
            telemetryRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }

        // Observe location updates
        viewModelScope.launch {
            try {
                locationRepository.getLocationUpdates().collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                }
            } catch (e: Exception) {
                // Location permissions might not be granted yet
            }
        }

        // Observe live WebSocket/cached incidents and track user's active incident status
        viewModelScope.launch {
            incidentRepository.getIncidents().collect { incidents ->
                val myIncident = if (currentActiveIncidentId != null) {
                    incidents.find { it.incidentId == currentActiveIncidentId }
                } else {
                    incidents.firstOrNull { it.status == "ACTIVE" || it.status == "DISPATCHED" }
                }
                _uiState.update { it.copy(incidents = incidents, activeIncident = myIncident) }
            }
        }
    }

    fun selectEmergencyType(type: EmergencyType) {
        _uiState.update { it.copy(selectedEmergencyType = type) }
    }

    fun selectTransmissionMode(mode: TransmissionMode) {
        viewModelScope.launch {
            serverConfigManager.setTransmissionMode(mode)
        }
    }

    fun triggerSos() {
        val currentState = _uiState.value
        val lat = currentState.userLocation?.latitude ?: 13.1391
        val lng = currentState.userLocation?.longitude ?: 123.7438

        _uiState.update { it.copy(isSendingSos = true, sosFeedbackMessage = null) }

        viewModelScope.launch {
            val result = sendSosUseCase(
                emergencyType = currentState.selectedEmergencyType.name,
                latitude = lat,
                longitude = lng,
                isOnline = currentState.isOnline
            )

            when (result) {
                is Result.Success -> {
                    if (!result.data.incidentId.isNullOrEmpty()) {
                        currentActiveIncidentId = result.data.incidentId
                    }
                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage = result.data.message ?: "SOS Dispatch Sent to Admins & Responders!"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage = "Alert stored for BLE Mesh relay: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(sosFeedbackMessage = null) }
    }
}
