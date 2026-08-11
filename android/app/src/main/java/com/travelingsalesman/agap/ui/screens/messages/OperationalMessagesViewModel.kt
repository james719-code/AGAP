package com.travelingsalesman.agap.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
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

data class OperationalContactItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val role: String,
    val status: String,
    val incidentId: String?,
    val incidentType: String?,
    val lastSeenAt: String? = null
)

data class OperationalMessagesUiState(
    val userRole: String = "CITIZEN",
    val contacts: List<OperationalContactItem> = emptyList(),
    val activeIncidents: List<IncidentEntity> = emptyList(),
    val responderUnits: List<ResponderUnitEntity> = emptyList(),
    /** Assignments for the authenticated field unit, never the global incident feed. */
    val assignedDispatches: List<DispatchDto> = emptyList(),
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class OperationalMessagesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperationalMessagesUiState())
    val uiState: StateFlow<OperationalMessagesUiState> = _uiState.asStateFlow()
    private var assignmentRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                val normalizedRole = role?.uppercase() ?: "CITIZEN"
                var roleChanged = false
                _uiState.update { state ->
                    roleChanged = state.userRole != normalizedRole
                    contactsState(state.copy(userRole = normalizedRole))
                }
                if (normalizedRole == "RESPONDER") {
                    startAssignmentRefresh()
                } else {
                    assignmentRefreshJob?.cancel()
                    assignmentRefreshJob = null
                }
                if (roleChanged && normalizedRole in OPERATIONAL_ROLES) {
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            combine(
                responderRepository.getRespondersFlow(),
                incidentRepository.getRecentIncidents(100).distinctUntilChanged()
            ) { responders, incidents ->
                _uiState.update {
                    contactsState(it.copy(
                        responderUnits = responders,
                        activeIncidents = incidents
                    ))
                }
            }.collect {}
        }
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, feedbackMessage = null) }
        viewModelScope.launch {
            try {
                val role = _uiState.value.userRole.uppercase()
                if (role == "ADMIN") {
                    responderRepository.getResponders()
                }
                incidentRepository.fetchRemoteIncidents()
                if (role == "RESPONDER") {
                    refreshResponderAssignments(showFailure = true)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = "Contacts refresh: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun startAssignmentRefresh() {
        if (assignmentRefreshJob?.isActive == true) return

        assignmentRefreshJob = viewModelScope.launch {
            while (isActive && _uiState.value.userRole == "RESPONDER") {
                delay(ASSIGNMENT_REFRESH_INTERVAL_MS)
                refreshResponderAssignments(showFailure = false)
            }
        }
    }

    private suspend fun refreshResponderAssignments(showFailure: Boolean) {
        when (val dispatches = responderRepository.getMyDispatches()) {
            is Result.Success -> _uiState.update {
                contactsState(it.copy(assignedDispatches = dispatches.data))
            }
            is Result.Error -> if (showFailure) {
                _uiState.update {
                    // Never replace an assignment inbox with the global incident
                    // feed when the assignment endpoint is unavailable. Retain
                    // the last verified assignments for an offline field unit.
                    contactsState(
                        it.copy(feedbackMessage = "Assignments unavailable: ${dispatches.message}")
                    )
                }
            }
            Result.Loading -> Unit
        }
    }

    private fun contactsState(state: OperationalMessagesUiState): OperationalMessagesUiState {
        val contacts = when (state.userRole.uppercase()) {
            "ADMIN" -> buildAdminContacts(state.responderUnits, state.activeIncidents)
            "RESPONDER" -> buildResponderContacts(state.activeIncidents, state.assignedDispatches)
            else -> emptyList()
        }
        return state.copy(contacts = contacts)
    }

    private fun buildAdminContacts(
        responders: List<ResponderUnitEntity>,
        incidents: List<IncidentEntity>
    ): List<OperationalContactItem> {
        return responders.map { responder ->
            val callsign = responder.unitCallsign.ifBlank { "Unit #${responder.id}" }
            val status = responder.status.uppercase()
            val assignedIncident = incidents.find {
                it.dispatchedUnitId == responder.id || (it.targetId.isNotBlank() && it.targetId == responder.targetId)
            }

            OperationalContactItem(
                id = responder.id.toString(),
                title = callsign,
                subtitle = "Target: ${responder.targetId.ifBlank { "Unassigned" }} • Status: $status",
                role = "RESPONDER",
                status = status,
                incidentId = assignedIncident?.incidentId,
                incidentType = assignedIncident?.emergencyType,
                lastSeenAt = responder.lastSeenAt
            )
        }
    }

    private fun buildResponderContacts(
        incidents: List<IncidentEntity>,
        assignedDispatches: List<DispatchDto>
    ): List<OperationalContactItem> {
        val incidentsByServerId = incidents.associateBy { incident ->
            incident.serverIncidentId ?: incident.incidentId.toLongOrNull()
        }

        return assignedDispatches
            .filter { dispatch -> dispatch.status.uppercase() !in TERMINAL_DISPATCH_STATUSES }
            .sortedByDescending { dispatch -> dispatch.dispatchedAt ?: "" }
            .map { dispatch ->
            val incident = incidentsByServerId[dispatch.incidentId]
            OperationalContactItem(
                id = "command_dispatch_${dispatch.id}",
                title = "Command • ${dispatch.unitCallsign ?: "My unit"}",
                subtitle = incident?.let {
                    "Assigned ${it.emergencyType.uppercase()} incident #${it.incidentId.take(12)}"
                } ?: "Assigned incident #${dispatch.incidentId} • Details are loading",
                role = "COMMAND",
                status = dispatch.status.uppercase(),
                // Numeric server IDs remain navigable even before the local
                // incident cache hydrates; IncidentDetailViewModel fetches it.
                incidentId = incident?.incidentId ?: dispatch.incidentId.toString(),
                incidentType = incident?.emergencyType,
                lastSeenAt = dispatch.acceptedAt ?: dispatch.dispatchedAt
            )
        }
    }

    private companion object {
        const val ASSIGNMENT_REFRESH_INTERVAL_MS = 15_000L
        val OPERATIONAL_ROLES = setOf("ADMIN", "RESPONDER")
        val TERMINAL_DISPATCH_STATUSES = setOf("COMPLETED", "CANCELLED")
    }
}
