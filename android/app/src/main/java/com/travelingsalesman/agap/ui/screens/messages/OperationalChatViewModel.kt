package com.travelingsalesman.agap.ui.screens.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.AssignmentVerificationDeniedException
import com.travelingsalesman.agap.data.repository.IncidentMessagingRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

data class OperationalChatUiState(
    val selectedContactId: String? = null,
    val selectedIncidentId: String? = null,
    val contacts: List<OperationalContactItem> = emptyList(),
    val selectedContact: OperationalContactItem? = null,
    val activeIncident: IncidentEntity? = null,
    /** Local incident cache used to resolve the exact contact -> incident mapping. */
    val activeIncidents: List<IncidentEntity> = emptyList(),
    val responderUnits: List<ResponderUnitEntity> = emptyList(),
    /** Command-side authoritative dispatches, keyed by backend incident id. */
    val adminDispatchesByIncident: Map<Long, List<DispatchDto>> = emptyMap(),
    /** Field-unit assignments returned by /responders/me/dispatches. */
    val responderDispatches: List<DispatchDto> = emptyList(),
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
    private val serverConfigManager: ServerConfigManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val navIncidentId: String? = savedStateHandle["incidentId"]
    private val navContactId: String? = savedStateHandle["contactId"]

    private val _uiState = MutableStateFlow(OperationalChatUiState())
    val uiState: StateFlow<OperationalChatUiState> = _uiState.asStateFlow()

    private val activeIncidentKey = MutableStateFlow<String?>(null)
    private var autoSyncJob: Job? = null
    private var lastAdminIncidentIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                val normalizedRole = role?.uppercase() ?: "CITIZEN"
                _uiState.update { state ->
                    val cleared = if (normalizedRole == "RESPONDER") {
                        state.copy(userRole = normalizedRole)
                    } else {
                        state.copy(
                            userRole = normalizedRole,
                            responderDispatches = emptyList(),
                            responderDispatchIds = emptySet(),
                            responderIncidentIds = emptySet()
                        )
                    }
                    rebuildContactsAndSelection(cleared)
                }

                when (normalizedRole) {
                    "ADMIN" -> {
                        refreshCatalog()
                        refreshAdminAssignments()
                    }
                    "RESPONDER" -> {
                        refreshCatalog()
                        refreshResponderAssignments()
                    }
                    else -> clearConversationSelection()
                }
            }
        }

        // On a validated Internet transition, replace the cached catalog and
        // conversation with the backend snapshot instead of waiting for the
        // next polling tick. While offline, Room remains the visible cache.
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && _uiState.value.userRole in OPERATIONAL_ROLES) {
                    refreshCatalog()
                    when (_uiState.value.userRole) {
                        "ADMIN" -> refreshAdminAssignments()
                        "RESPONDER" -> refreshResponderAssignments()
                    }
                    ensureSelectedIncidentHydrated()
                    refreshCurrentMessagesNow()
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
            ) { responders, incidents -> responders to incidents }
                .collect { (responders, incidents) ->
                    _uiState.update { state ->
                        rebuildContactsAndSelection(
                            state.copy(
                                responderUnits = responders,
                                activeIncidents = incidents
                            )
                        )
                    }

                    if (_uiState.value.userRole == "ADMIN") {
                        val ids = currentAdminIncidentIds()
                        if (ids != lastAdminIncidentIds) {
                            lastAdminIncidentIds = ids
                            refreshAdminAssignments()
                        }
                    }
                    // A successful hydration writes Room, which re-emits this
                    // collector. Only resolve a selected incident that is still
                    // missing locally; explicit selection, reconnection, and the
                    // periodic sync below perform the subsequent online refresh.
                    if (_uiState.value.selectedIncidentId != null && _uiState.value.activeIncident == null) {
                        ensureSelectedIncidentHydrated()
                    }
                }
        }

        viewModelScope.launch {
            activeIncidentKey.flatMapLatest { key ->
                if (key.isNullOrBlank() || !canReadSelectedConversation(_uiState.value)) {
                    flowOf(emptyList())
                } else {
                    incidentMessagingRepository.observeMessages(key)
                }
            }.collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }

        startAutoSync()
    }

    fun selectContact(contact: OperationalContactItem) {
        _uiState.update { state ->
            val resolvedIncidentId = contact.incidentId
            val incident = findIncident(state.activeIncidents, resolvedIncidentId)
            updateObservedIncidentKey(incident?.incidentId ?: resolvedIncidentId)
            state.copy(
                selectedContactId = contact.id,
                selectedContact = contact,
                selectedIncidentId = resolvedIncidentId,
                activeIncident = incident,
                messages = if (resolvedIncidentId == null) emptyList() else state.messages
            )
        }
        viewModelScope.launch {
            ensureSelectedIncidentHydrated()
            refreshCurrentMessagesNow()
        }
    }

    fun sendReply(choice: PredefinedMessageEntity) {
        if (_uiState.value.isSendingMessage) return
        val role = _uiState.value.userRole.uppercase()
        if (role !in OPERATIONAL_ROLES) {
            _uiState.update { it.copy(feedbackMessage = "Only command or assigned responders can send incident updates") }
            return
        }

        _uiState.update { it.copy(isSendingMessage = true, feedbackMessage = null) }
        viewModelScope.launch {
            try {
                if (role == "RESPONDER") {
                    // Revalidate immediately before sending; do not rely on the
                    // 15-second screen refresh when an assignment was just closed.
                    refreshResponderAssignments()
                }
                ensureSelectedIncidentHydrated()
                val state = _uiState.value
                val incident = state.activeIncident
                if (incident == null) {
                    _uiState.update {
                        it.copy(feedbackMessage = "Cannot send: this responder has no active incident channel")
                    }
                    return@launch
                }
                if (role == "RESPONDER" && !isResponderAssignedTo(state, incident)) {
                    _uiState.update {
                        it.copy(feedbackMessage = "This incident is no longer assigned to your unit")
                    }
                    return@launch
                }

                when (val result = incidentMessagingRepository.sendPredefinedReply(incident, choice, role)) {
                    is Result.Success -> _uiState.update {
                        it.copy(feedbackMessage = result.data)
                    }
                    is Result.Error -> _uiState.update {
                        it.copy(feedbackMessage = result.message)
                    }
                    Result.Loading -> Unit
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(feedbackMessage = "Failed to send update: ${e.message ?: "unknown error"}")
                }
            } finally {
                _uiState.update { it.copy(isSendingMessage = false) }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_SYNC_INTERVAL_MS)
                when (_uiState.value.userRole) {
                    "ADMIN" -> refreshAdminAssignments()
                    "RESPONDER" -> refreshResponderAssignments()
                }
                ensureSelectedIncidentHydrated()
                if (_uiState.value.transmissionMode != TransmissionMode.FORCE_BLE_MESH) {
                    refreshCurrentMessagesNow()
                }
            }
        }
    }

    private suspend fun refreshCurrentMessagesNow() {
        if (!networkMonitor.isOnlineNow()) return
        val state = _uiState.value
        val incident = state.activeIncident ?: return
        if (!canReadSelectedConversation(state)) return
        runCatching { incidentMessagingRepository.refreshOnlineMessages(incident) }
    }

    private suspend fun refreshCatalog() {
        // The repository itself serves the Room cache when offline. The chat
        // does not need to surface a transient catalog failure over a usable
        // existing conversation, so leave its current choices intact on Error.
        incidentMessagingRepository.refreshCatalog()
    }

    private suspend fun refreshAdminAssignments() {
        if (_uiState.value.userRole != "ADMIN") return
        val ids = currentAdminIncidentIds().toList()
        when (val result = incidentRepository.fetchDispatchesBulk(ids)) {
            is Result.Success -> _uiState.update { state ->
                rebuildContactsAndSelection(state.copy(adminDispatchesByIncident = result.data))
            }
            is Result.Error -> Unit // retain last verified mapping during transient outages
            Result.Loading -> Unit
        }
    }

    private suspend fun refreshResponderAssignments() {
        if (_uiState.value.userRole != "RESPONDER") return
        when (val result = responderRepository.getMyDispatches()) {
            is Result.Success -> {
                val active = result.data.filter { it.status.uppercase() in ACTIVE_DISPATCH_STATUSES }
                _uiState.update { state ->
                    rebuildContactsAndSelection(
                        state.copy(
                            responderDispatches = active,
                            responderDispatchIds = active.mapTo(linkedSetOf()) { it.id },
                            responderIncidentIds = active.mapTo(linkedSetOf()) { it.incidentId }
                        )
                    )
                }
            }
            is Result.Error -> {
                if (result.exception is AssignmentVerificationDeniedException) {
                    // Only a 401/403 is an authoritative revocation. Keep the
                    // last verified Room/in-memory snapshot for offline and
                    // transient server failures.
                    _uiState.update { state ->
                        rebuildContactsAndSelection(
                            state.copy(
                                responderDispatches = emptyList(),
                                responderDispatchIds = emptySet(),
                                responderIncidentIds = emptySet()
                            )
                        )
                    }
                }
            }
            Result.Loading -> Unit
        }
    }

    private suspend fun ensureSelectedIncidentHydrated() {
        val state = _uiState.value
        val selectedId = state.selectedIncidentId ?: return
        val serverId = state.activeIncident?.serverIncidentId
            ?: state.activeIncident?.incidentId?.toLongOrNull()
            ?: selectedId.toLongOrNull()
            ?: return
        if (state.userRole == "RESPONDER" && serverId !in state.responderIncidentIds) return

        when (val result = incidentRepository.fetchIncidentById(serverId)) {
            is Result.Success -> _uiState.update { current ->
                val incidents = (current.activeIncidents + result.data).distinctBy { it.incidentId }
                rebuildContactsAndSelection(current.copy(activeIncidents = incidents))
            }
            else -> Unit
        }
    }

    private fun rebuildContactsAndSelection(state: OperationalChatUiState): OperationalChatUiState {
        val contacts = when (state.userRole.uppercase()) {
            "ADMIN" -> buildAdminContacts(state.responderUnits, state.activeIncidents, state.adminDispatchesByIncident)
            "RESPONDER" -> buildResponderContacts(state.activeIncidents, state.responderDispatches)
            else -> emptyList()
        }

        val preferredId = state.selectedContactId ?: navContactId
        val selected = contacts.firstOrNull { it.id == preferredId }
            ?: contacts.firstOrNull { it.incidentId != null && it.incidentId == navIncidentId }
            ?: contacts.firstOrNull()

        val directNavIncident = navIncidentId?.takeIf { navId ->
            state.userRole == "ADMIN" ||
                (navId.toLongOrNull()?.let { it in state.responderIncidentIds } == true)
        }
        val resolvedIncidentId = if (selected != null) selected.incidentId else directNavIncident
        val incident = findIncident(state.activeIncidents, resolvedIncidentId)
        val readable = when (state.userRole.uppercase()) {
            "ADMIN" -> resolvedIncidentId != null
            "RESPONDER" -> {
                val serverId = incident?.serverIncidentId ?: resolvedIncidentId?.toLongOrNull()
                serverId != null && serverId in state.responderIncidentIds
            }
            else -> false
        }

        updateObservedIncidentKey(if (readable) incident?.incidentId ?: resolvedIncidentId else null)

        return state.copy(
            contacts = contacts,
            selectedContactId = selected?.id,
            selectedContact = selected,
            selectedIncidentId = if (readable) resolvedIncidentId else null,
            activeIncident = if (readable) incident else null,
            messages = if (readable) state.messages else emptyList()
        )
    }

    private fun buildAdminContacts(
        responders: List<ResponderUnitEntity>,
        incidents: List<IncidentEntity>,
        dispatchesByIncident: Map<Long, List<DispatchDto>>
    ): List<OperationalContactItem> {
        val incidentsByServerId = incidents.mapNotNull { incident ->
            (incident.serverIncidentId ?: incident.incidentId.toLongOrNull())?.let { it to incident }
        }.toMap()
        val activeDispatches = dispatchesByIncident.values.flatten()
            .filter { it.status.uppercase() in ACTIVE_DISPATCH_STATUSES }

        return responders.map { responder ->
            val dispatch = activeDispatches
                .filter { it.unitId == responder.id }
                .maxByOrNull { it.dispatchedAt.orEmpty() }
            val incident = dispatch?.let { incidentsByServerId[it.incidentId] }
            val unitStatus = responder.status.uppercase()
            OperationalContactItem(
                id = responder.id.toString(),
                title = responder.unitCallsign.ifBlank { "Unit #${responder.id}" },
                subtitle = if (dispatch != null) {
                    "Incident #${dispatch.incidentId} • ${dispatch.status.uppercase()}"
                } else {
                    "No active incident • Unit: $unitStatus"
                },
                role = "RESPONDER",
                status = dispatch?.status?.uppercase() ?: unitStatus,
                incidentId = incident?.incidentId ?: dispatch?.incidentId?.toString(),
                incidentType = incident?.emergencyType,
                lastSeenAt = responder.lastSeenAt
            )
        }
    }

    private fun buildResponderContacts(
        incidents: List<IncidentEntity>,
        dispatches: List<DispatchDto>
    ): List<OperationalContactItem> {
        val incidentsByServerId = incidents.mapNotNull { incident ->
            (incident.serverIncidentId ?: incident.incidentId.toLongOrNull())?.let { it to incident }
        }.toMap()
        return dispatches
            .filter { it.status.uppercase() in ACTIVE_DISPATCH_STATUSES }
            .sortedByDescending { it.dispatchedAt.orEmpty() }
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
                    incidentId = incident?.incidentId ?: dispatch.incidentId.toString(),
                    incidentType = incident?.emergencyType,
                    lastSeenAt = dispatch.acceptedAt ?: dispatch.dispatchedAt
                )
            }
    }

    private fun findIncident(incidents: List<IncidentEntity>, id: String?): IncidentEntity? {
        if (id.isNullOrBlank()) return null
        return incidents.firstOrNull { incident ->
            incident.incidentId == id ||
                incident.serverIncidentId?.toString() == id ||
                (incident.serverIncidentId == null && incident.incidentId.toLongOrNull()?.toString() == id)
        }
    }

    private fun canReadSelectedConversation(state: OperationalChatUiState): Boolean {
        val incident = state.activeIncident ?: return false
        return when (state.userRole.uppercase()) {
            "ADMIN" -> true
            "RESPONDER" -> isResponderAssignedTo(state, incident)
            else -> false
        }
    }

    private fun isResponderAssignedTo(state: OperationalChatUiState, incident: IncidentEntity): Boolean {
        val serverId = incident.serverIncidentId ?: incident.incidentId.toLongOrNull()
        return serverId != null && serverId in state.responderIncidentIds
    }

    private fun currentAdminIncidentIds(): Set<Long> {
        val ids = _uiState.value.activeIncidents.mapNotNullTo(linkedSetOf()) { incident ->
            incident.serverIncidentId ?: incident.incidentId.toLongOrNull()
        }
        navIncidentId?.toLongOrNull()?.let(ids::add)
        return ids
    }

    private fun updateObservedIncidentKey(key: String?) {
        if (activeIncidentKey.value != key) activeIncidentKey.value = key
    }

    private fun clearConversationSelection() {
        updateObservedIncidentKey(null)
        _uiState.update {
            it.copy(
                contacts = emptyList(),
                selectedContactId = null,
                selectedContact = null,
                selectedIncidentId = null,
                activeIncident = null,
                messages = emptyList()
            )
        }
    }

    private companion object {
        const val AUTO_SYNC_INTERVAL_MS = 15_000L
        val OPERATIONAL_ROLES = setOf("ADMIN", "RESPONDER")
        val ACTIVE_DISPATCH_STATUSES = setOf("ASSIGNED", "ACCEPTED", "EN_ROUTE", "ON_SCENE")
    }
}
