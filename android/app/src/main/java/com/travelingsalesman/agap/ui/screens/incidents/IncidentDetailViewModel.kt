package com.travelingsalesman.agap.ui.screens.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.domain.usecase.UpdateIncidentStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.ResponderRepository

data class IncidentDetailUiState(
    val incident: IncidentEntity? = null,
    val dispatches: List<DispatchDto> = emptyList(),
    val responders: List<ResponderUnitDto> = emptyList(),
    val showDispatchDialog: Boolean = false,
    val isDispatching: Boolean = false,
    val userRole: String = "CITIZEN",
    val isUpdatingStatus: Boolean = false,
    val isLoadingDispatches: Boolean = false,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val updateIncidentStatusUseCase: UpdateIncidentStatusUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    init {
        // Observe role
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }

        // Fetch incident details from local store or remote API
        viewModelScope.launch {
            val entity = incidentRepository.getIncidentById(incidentId)
            if (entity != null) {
                _uiState.update { it.copy(incident = entity) }
                loadDispatches(entity.incidentId.toLongOrNull())
            } else {
                val idLong = incidentId.toLongOrNull()
                if (idLong != null) {
                    when (val res = incidentRepository.fetchIncidentById(idLong)) {
                        is Result.Success -> {
                            _uiState.update { it.copy(incident = res.data) }
                            loadDispatches(idLong)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            try {
                val entity = incidentRepository.getIncidentById(incidentId)
                if (entity != null) {
                    _uiState.update { it.copy(incident = entity) }
                    loadDispatches(entity.incidentId.toLongOrNull())
                } else {
                    val idLong = incidentId.toLongOrNull()
                    if (idLong != null) {
                        when (val res = incidentRepository.fetchIncidentById(idLong)) {
                            is Result.Success -> {
                                _uiState.update { it.copy(incident = res.data) }
                                loadDispatches(idLong)
                            }
                            else -> {}
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun loadDispatches(idLong: Long?) {
        if (idLong == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDispatches = true) }
            when (val result = incidentRepository.fetchDispatches(idLong)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            dispatches = result.data
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDispatches = false,
                            feedbackMessage = "Dispatches unavailable: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoadingDispatches = false) }
                }
            }
        }
    }

    fun updateDispatchStatus(dispatchId: Long, newStatus: String) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.incidentId.toLongOrNull() ?: return

        _uiState.update { it.copy(isUpdatingStatus = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.updateDispatchStatus(idLong, dispatchId, newStatus)) {
                is Result.Success -> {
                    val incidentStatus = when (newStatus) {
                        "EN_ROUTE", "ON_SCENE" -> newStatus
                        "COMPLETED" -> "RESOLVED"
                        else -> incident.status
                    }
                    _uiState.update {
                        it.copy(
                            isUpdatingStatus = false,
                            incident = it.incident?.copy(status = incidentStatus),
                            dispatches = it.dispatches.map { dispatch ->
                                if (dispatch.id == dispatchId) dispatch.copy(status = newStatus) else dispatch
                            },
                            feedbackMessage = "Dispatch updated to $newStatus"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingStatus = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun updateStatus(newStatus: String) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.incidentId.toLongOrNull() ?: return

        _uiState.update { it.copy(isUpdatingStatus = true, feedbackMessage = null) }

        viewModelScope.launch {
            val result = updateIncidentStatusUseCase(idLong, newStatus)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingStatus = false,
                            incident = it.incident?.copy(status = newStatus),
                            feedbackMessage = "Status updated to $newStatus"
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingStatus = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun openDispatchDialog() {
        _uiState.update { it.copy(showDispatchDialog = true, feedbackMessage = null) }
        viewModelScope.launch {
            when (val res = responderRepository.getResponders()) {
                is Result.Success -> {
                    _uiState.update { it.copy(responders = res.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Responders unavailable: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun closeDispatchDialog() {
        _uiState.update { it.copy(showDispatchDialog = false) }
    }

    fun dispatchResponder(unitId: Long) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.incidentId.toLongOrNull() ?: return

        _uiState.update { it.copy(isDispatching = true, showDispatchDialog = false, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.dispatchIncident(idLong, unitId)) {
                is Result.Success -> {
                    val updatedIncident = incident.copy(status = "DISPATCHED")
                    _uiState.update {
                        it.copy(
                            isDispatching = false,
                            incident = updatedIncident,
                            feedbackMessage = "Dispatched Unit #$unitId to Incident #${incident.incidentId}"
                        )
                    }
                    loadDispatches(idLong)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isDispatching = false,
                            feedbackMessage = "Dispatch failed: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isDispatching = false) }
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
