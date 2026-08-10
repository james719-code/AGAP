package com.travelingsalesman.agap.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
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

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }

        viewModelScope.launch {
            combine(
                responderRepository.getRespondersFlow(),
                incidentRepository.getRecentIncidents(100).distinctUntilChanged()
            ) { responders, incidents ->
                val role = _uiState.value.userRole.uppercase()
                val contacts = if (role == "ADMIN") {
                    buildAdminContacts(responders, incidents)
                } else {
                    buildResponderContacts(incidents)
                }
                _uiState.update {
                    it.copy(
                        responderUnits = responders,
                        activeIncidents = incidents,
                        contacts = contacts
                    )
                }
            }.collect {}
        }

        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, feedbackMessage = null) }
        viewModelScope.launch {
            try {
                responderRepository.getResponders()
                incidentRepository.fetchRemoteIncidents()
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

    private fun buildAdminContacts(
        responders: List<ResponderUnitEntity>,
        incidents: List<IncidentEntity>
    ): List<OperationalContactItem> {
        val incidentMap = incidents.associateBy { it.targetId }

        return responders.map { responder ->
            val callsign = responder.unitCallsign.ifBlank { "Unit #${responder.id}" }
            val status = responder.status.uppercase()
            val assignedIncident = incidentMap[responder.targetId]

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
        incidents: List<IncidentEntity>
    ): List<OperationalContactItem> {
        val active = incidents.filter { it.status.uppercase() !in setOf("RESOLVED", "CANCELLED") }

        if (active.isEmpty()) {
            return listOf(
                OperationalContactItem(
                    id = "command_central",
                    title = "Central Command Center",
                    subtitle = "Disaster Risk Reduction Management Office",
                    role = "COMMAND",
                    status = "STANDBY",
                    incidentId = null,
                    incidentType = null
                )
            )
        }

        return active.map { incident ->
            OperationalContactItem(
                id = "command_${incident.incidentId}",
                title = "Command Center (${incident.emergencyType.uppercase()})",
                subtitle = "Active Dispatch Incident #${incident.incidentId.take(12)}",
                role = "COMMAND",
                status = incident.status.uppercase(),
                incidentId = incident.incidentId,
                incidentType = incident.emergencyType
            )
        }
    }
}
