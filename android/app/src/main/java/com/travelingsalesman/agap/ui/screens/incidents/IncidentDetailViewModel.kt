package com.travelingsalesman.agap.ui.screens.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
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

data class IncidentDetailUiState(
    val incident: IncidentEntity? = null,
    val userRole: String = "CITIZEN",
    val isUpdatingStatus: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
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

        // Fetch incident details from local store
        viewModelScope.launch {
            val entity = incidentRepository.getIncidentById(incidentId)
            _uiState.update { it.copy(incident = entity) }
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

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
