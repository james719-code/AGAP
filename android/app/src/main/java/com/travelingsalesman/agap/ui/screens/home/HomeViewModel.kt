package com.travelingsalesman.agap.ui.screens.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import com.travelingsalesman.agap.domain.usecase.SendSosUseCase
import com.travelingsalesman.agap.location.LocationRepository
import com.travelingsalesman.agap.ui.components.EmergencyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isOnline: Boolean = true,
    val selectedEmergencyType: EmergencyType = EmergencyType.MEDICAL,
    val pendingPacketCount: Int = 0,
    val userLocation: Location? = null,
    val incidents: List<IncidentEntity> = emptyList(),
    val isSendingSos: Boolean = false,
    val sosFeedbackMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sendSosUseCase: SendSosUseCase,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase,
    private val networkMonitor: NetworkMonitor,
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
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

        // Observe live WebSocket/cached incidents
        viewModelScope.launch {
            incidentRepository.getIncidents().collect { incidents ->
                _uiState.update { it.copy(incidents = incidents) }
            }
        }
    }

    fun selectEmergencyType(type: EmergencyType) {
        _uiState.update { it.copy(selectedEmergencyType = type) }
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
                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage = result.data.message ?: "SOS Dispatch Sent Successfully!"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingSos = false,
                            sosFeedbackMessage = "Failed: ${result.message}"
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
