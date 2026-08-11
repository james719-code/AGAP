package com.travelingsalesman.agap.ui.screens.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentMessagingRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OperationalChatUiState(
    val selectedContactId: String? = null,
    val selectedIncidentId: String? = null,
    val contacts: List<OperationalContactItem> = emptyList(),
    val selectedContact: OperationalContactItem? = null,
    val activeIncident: IncidentEntity? = null,
    val responderDispatchIds: Set<Long> = emptySet(),
    val responderIncidentIds: Set<Long> = emptySet(),
    val messages: List<IncidentMessageEntity> = emptyList(),
    val replyChoices: List<PredefinedMessageEntity> = emptyList(),
    val userRole: String = "CITIZEN",
    val userId: Long? = null,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val isSendingMessage: Boolean = false,
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OperationalChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val incidentMessagingRepository: IncidentMessagingRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val navIncidentId: String? = savedStateHandle["incidentId"]
    private val navContactId: String? = savedStateHandle["contactId"]

    private val _uiState = MutableStateFlow(OperationalChatUiState())
    val uiState: StateFlow<OperationalChatUiState> = _uiState.asStateFlow()

    private val activeIncidentKey = MutableStateFlow<String?>(null)
    private var autoSyncJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                val normalizedRole = role?.uppercase() ?: "CITIZEN"
                _uiState.update { it.copy(userRole = normalizedRole) }
                if (normalizedRole == "RESPONDER") {
                    refreshResponderAssignments()
                }
            }
        }

        viewModelScope.launch {
            authRepository.userId.collect { userId ->
                _uiState.update { it.copy(userId = userId) }
            }
        }

        viewModelScope.launch {
            serverConfigManager.transmissionMode.collect { mode ->
                _uiState.update { it.copy(transmissionMode = mode) }
            }
        }

        viewModelScope.launch {
            incidentMessagingRepository.observeReplyChoices().collect { choices ->
                _uiState.update { it.copy(replyChoices = choices) }
            }
        }

        viewModelScope.launch {
            combine(
                responderRepository.getRespondersFlow(),
                incidentRepository.getRecentIncidents(100).distinctUntilChanged()
            ) { responders, incidents ->
                val role = _uiState.value.userRole
                val contacts = if (role.uppercase() == "ADMIN") {
                    responders.map { responder ->
                        val callsign = responder.unitCallsign.ifBlank { "Unit #${responder.id}" }
                        val status = responder.status.uppercase()
                        val assignedIncident = incidents.find { it.targetId == responder.targetId }
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
                } else {
                    _uiState.value.contacts
                }

                _uiState.update { state ->
                    val resolvedContactId = state.selectedContactId ?: navContactId ?: contacts.firstOrNull()?.id
                    val selected = contacts.find { it.id == resolvedContactId } ?: contacts.firstOrNull()
                    val resolvedIncidentId = selected?.incidentId ?: navIncidentId
                    val activeIncident = incidents.find { it.incidentId == resolvedIncidentId }

                    if (resolvedIncidentId != activeIncidentKey.value) {
                        activeIncidentKey.value = resolvedIncidentId
                    }

                    state.copy(
                        contacts = contacts,
                        selectedContactId = selected?.id,
                        selectedContact = selected,
                        selectedIncidentId = resolvedIncidentId,
                        activeIncident = activeIncident
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            activeIncidentKey.flatMapLatest { key ->
                if (key.isNullOrBlank()) flowOf(emptyList())
                else incidentMessagingRepository.observeMessages(key)
            }.collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }

        startAutoSync()
    }

    fun selectContact(contact: OperationalContactItem) {
        _uiState.update { state ->
            val resolvedIncidentId = contact.incidentId
            val activeIncident = state.activeIncidentsFind(resolvedIncidentId)

            if (resolvedIncidentId != activeIncidentKey.value) {
                activeIncidentKey.value = resolvedIncidentId
            }

            state.copy(
                selectedContactId = contact.id,
                selectedContact = contact,
                selectedIncidentId = resolvedIncidentId,
                activeIncident = activeIncident
            )
        }
        refreshCurrentMessages()
    }

    private fun OperationalChatUiState.activeIncidentsFind(incidentId: String?): IncidentEntity? {
        if (incidentId == null) return null
        return activeIncident
    }

    fun sendReply(choice: PredefinedMessageEntity) {
        val activeInc = _uiState.value.activeIncident
        if (activeInc == null) {
            _uiState.update { it.copy(feedbackMessage = "Cannot send message: no incident assigned to this responder channel yet.") }
            return
        }
        if (_uiState.value.isSendingMessage) return

        _uiState.update { it.copy(isSendingMessage = true) }
        viewModelScope.launch {
            try {
                when (val result = incidentMessagingRepository.sendPredefinedReply(activeInc, choice, _uiState.value.userRole)) {
                    is Result.Success -> {
                        _uiState.update { it.copy(feedbackMessage = "Operational update transmitted") }
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(feedbackMessage = result.message) }
                    }
                    Result.Loading -> Unit
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = "Failed to send update: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSendingMessage = false) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun refreshCurrentMessages() {
        viewModelScope.launch {
            val incident = _uiState.value.activeIncident ?: return@launch
            runCatching {
                incidentMessagingRepository.refreshOnlineMessages(incident)
            }
        }
    }

    private fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val entity = _uiState.value.activeIncident ?: continue
                if (_uiState.value.transmissionMode == TransmissionMode.FORCE_BLE_MESH) continue
                runCatching {
                    incidentMessagingRepository.refreshOnlineMessages(entity)
                }
            }
        }
    }

    private suspend fun refreshResponderAssignments() {
        when (val dispatches = responderRepository.getMyDispatches()) {
            is Result.Success -> {
                val assignedIncIds = dispatches.data.map { it.incidentId }.toSet()
                _uiState.update {
                    it.copy(
                        responderDispatchIds = dispatches.data.map { d -> d.id }.toSet(),
                        responderIncidentIds = assignedIncIds
                    )
                }
            }
            else -> Unit
        }
    }
}
