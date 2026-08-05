package com.travelingsalesman.agap.ui.screens.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.domain.usecase.GetIncidentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentListUiState(
    val incidents: List<IncidentEntity> = emptyList(),
    val selectedStatusFilter: String? = null,
    val selectedTypeFilter: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val getIncidentsUseCase: GetIncidentsUseCase,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentListUiState())
    val uiState: StateFlow<IncidentListUiState> = _uiState.asStateFlow()

    init {
        // Observe local cached/live Room incidents
        viewModelScope.launch {
            incidentRepository.getIncidents().collect { localList ->
                _uiState.update { state ->
                    val filtered = localList.filter { incident ->
                        val matchStatus = state.selectedStatusFilter == null || incident.status.equals(state.selectedStatusFilter, true)
                        val matchType = state.selectedTypeFilter == null || incident.emergencyType.equals(state.selectedTypeFilter, true)
                        matchStatus && matchType
                    }
                    state.copy(incidents = filtered)
                }
            }
        }
        refreshRemoteIncidents()
    }

    fun setStatusFilter(status: String?) {
        val newStatus = if (_uiState.value.selectedStatusFilter == status) null else status
        _uiState.update { it.copy(selectedStatusFilter = newStatus) }
        refreshRemoteIncidents()
    }

    fun setTypeFilter(type: String?) {
        val newType = if (_uiState.value.selectedTypeFilter == type) null else type
        _uiState.update { it.copy(selectedTypeFilter = newType) }
        refreshRemoteIncidents()
    }

    fun refreshRemoteIncidents() {
        val currentState = _uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = getIncidentsUseCase(
                type = currentState.selectedTypeFilter,
                status = currentState.selectedStatusFilter
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                else -> {}
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
