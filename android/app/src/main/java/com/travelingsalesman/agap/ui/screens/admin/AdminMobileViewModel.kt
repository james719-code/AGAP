package com.travelingsalesman.agap.ui.screens.admin

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminMobileUiState(
    val isOnline: Boolean = true,
    val username: String? = null,
    val pendingPacketCount: Int = 0,
    val incidents: List<IncidentEntity> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class AdminMobileViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val telemetryRepository: TelemetryRepository,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminMobileUiState())
    val uiState: StateFlow<AdminMobileUiState> = _uiState.asStateFlow()

    init {
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
            telemetryRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }

        viewModelScope.launch {
            incidentRepository.getIncidents().collect { list ->
                _uiState.update { it.copy(incidents = list) }
            }
        }

        fetchResponders()
    }

    private fun fetchResponders() {
        viewModelScope.launch {
            when (val res = responderRepository.getResponders()) {
                is Result.Success -> {
                    _uiState.update { it.copy(responders = res.data) }
                }
                else -> {}
            }
        }
    }

    fun dispatchResponder(incidentId: String) {
        val idLong = incidentId.toLongOrNull() ?: 1L
        viewModelScope.launch {
            incidentRepository.updateStatus(idLong, "DISPATCHED")
            _uiState.update { it.copy(feedbackMessage = "Dispatched units for Incident #$incidentId") }
        }
    }

    fun resolveIncident(incidentId: String) {
        val idLong = incidentId.toLongOrNull() ?: 1L
        viewModelScope.launch {
            incidentRepository.updateStatus(idLong, "RESOLVED")
            _uiState.update { it.copy(feedbackMessage = "Incident #$incidentId marked RESOLVED") }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
