package com.travelingsalesman.agap.ui.screens.incidents

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
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.AssignmentVerificationDeniedException
import com.travelingsalesman.agap.data.repository.IncidentMessagingRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.domain.usecase.UpdateIncidentStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class IncidentDetailUiState(
    val incident: IncidentEntity? = null,
    val dispatches: List<DispatchDto> = emptyList(),
    /**
     * Dispatches returned by the authenticated responder's `/me/dispatches`
     * endpoint.  Keeping this separate from [dispatches] prevents a responder
     * from treating another unit's assignment as their own when opening a
     * shared incident timeline.
     */
    val responderDispatchIds: Set<Long> = emptySet(),
    val responderIncidentIds: Set<Long> = emptySet(),
    val availableResponders: List<com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto> = emptyList(),
    val messages: List<IncidentMessageEntity> = emptyList(),
    /** True only for command or an actively assigned responder. */
    val canAccessMessages: Boolean = false,
    val replyChoices: List<PredefinedMessageEntity> = emptyList(),
    val userRole: String = "CITIZEN",
    val userId: Long? = null,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val isUpdatingStatus: Boolean = false,
    val isDispatchingUnit: Boolean = false,
    val isLoadingDispatches: Boolean = false,
    val isSendingMessage: Boolean = false,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val incidentMessagingRepository: IncidentMessagingRepository,
    private val updateIncidentStatusUseCase: UpdateIncidentStatusUseCase,
    private val authRepository: AuthRepository,
    private val responderRepository: ResponderRepository,
    private val serverConfigManager: ServerConfigManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])
    // A reconciled mesh incident keeps its UUID as the local Room key while
    // navigation/notifications use the numeric server ID. Switch the observed
    // timeline to the resolved local key once the incident loads.
    private val messageIncidentKey = MutableStateFlow(incidentId)
    private val messageAccessAllowed = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()
    private var autoSyncJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                val normalizedRole = role?.uppercase() ?: "CITIZEN"
                _uiState.update {
                    it.copy(
                        userRole = normalizedRole,
                        responderDispatchIds = if (normalizedRole == "RESPONDER") {
                            it.responderDispatchIds
                        } else {
                            emptySet()
                        },
                        responderIncidentIds = if (normalizedRole == "RESPONDER") {
                            it.responderIncidentIds
                        } else {
                            emptySet()
                        }
                    )
                }
                if (normalizedRole == "RESPONDER") {
                    refreshResponderAssignments()
                } else {
                    updateMessageAccess()
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

        // Conversation lookup follows the stable local Room key after a server
        // incident resolves to a mesh-origin placeholder.
        viewModelScope.launch {
            combine(messageIncidentKey, messageAccessAllowed) { key, allowed -> key to allowed }
                .flatMapLatest { (key, allowed) ->
                    if (allowed) incidentMessagingRepository.observeMessages(key) else flowOf(emptyList())
                }
                .collect { list ->
                    _uiState.update { it.copy(messages = list) }
                }
        }

        // Fixed choices are always read from the Room cache. SyncWorker and the
        // best-effort refresh below replace that cache from the server when online.
        viewModelScope.launch {
            incidentMessagingRepository.observeReplyChoices().collect { choices ->
                _uiState.update { it.copy(replyChoices = choices) }
            }
        }

        viewModelScope.launch { refresh() }
        startAutoSync()
    }

    fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15_000L)
                val entity = _uiState.value.incident ?: continue
                runCatching {
                    if (_uiState.value.userRole == "RESPONDER") {
                        refreshResponderAssignments()
                    } else {
                        updateMessageAccess()
                    }
                    loadDispatches(entity.serverIncidentId ?: entity.incidentId.toLongOrNull())
                    if (
                        _uiState.value.canAccessMessages &&
                        _uiState.value.transmissionMode != TransmissionMode.FORCE_BLE_MESH
                    ) {
                        incidentMessagingRepository.refreshOnlineMessages(entity)
                    }
                }
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    fun sendReply(choice: PredefinedMessageEntity) {
        val incident = _uiState.value.incident ?: return
        val role = _uiState.value.userRole.uppercase()
        if (role != "ADMIN" && role != "RESPONDER") {
            _uiState.update { it.copy(feedbackMessage = "Only responders or administrators can send incident replies") }
            return
        }
        if (role == "RESPONDER" && !isResponderAssignedTo(incident)) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "You can only send operational updates for an incident assigned to your unit"
                )
            }
            return
        }
        if (_uiState.value.isSendingMessage) return

        _uiState.update { it.copy(isSendingMessage = true, feedbackMessage = null) }
        viewModelScope.launch {
            when (val result = incidentMessagingRepository.sendPredefinedReply(incident, choice, role)) {
                is Result.Success -> _uiState.update {
                    it.copy(isSendingMessage = false, feedbackMessage = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSendingMessage = false, feedbackMessage = result.message ?: "Failed to send reply")
                }
                Result.Loading -> Unit
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            try {
                // Resolve Room first only to recover a reconciled incident's
                // numeric backend ID. Whenever Internet is available, the
                // repository fetches the authoritative server copy before the
                // cached snapshot is shown as current data.
                var entity = incidentRepository.getIncidentById(incidentId)
                val serverIncidentId = entity?.serverIncidentId ?: incidentId.toLongOrNull()
                if (serverIncidentId != null) {
                    when (val result = incidentRepository.fetchIncidentById(serverIncidentId)) {
                        is Result.Success -> entity = result.data
                        is Result.Error -> if (networkMonitor.isOnlineNow()) {
                            entity = null
                            _uiState.update {
                                it.copy(feedbackMessage = result.message ?: "Unable to load this incident")
                            }
                        }
                        Result.Loading -> Unit
                    }
                }

                // Refresh the authoritative reply catalog first when the current
                // transmission policy permits Internet. Online message history can
                // then resolve predefined IDs/protocol codes against the latest cache.
                // Offline/BLE-only operation keeps using the last Room snapshot.
                incidentMessagingRepository.refreshCatalog()

                if (entity != null) {
                    messageIncidentKey.value = entity.incidentId
                    _uiState.update { it.copy(incident = entity) }
                    loadDispatches(entity.serverIncidentId ?: entity.incidentId.toLongOrNull())
                    if (_uiState.value.userRole == "RESPONDER") {
                        refreshResponderAssignments()
                    } else {
                        updateMessageAccess()
                    }
                    if (_uiState.value.canAccessMessages) {
                        incidentMessagingRepository.refreshOnlineMessages(entity)
                    }
                } else if (networkMonitor.isOnlineNow()) {
                    messageAccessAllowed.value = false
                    _uiState.update {
                        it.copy(incident = null, messages = emptyList())
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
                is Result.Success -> _uiState.update {
                    it.copy(isLoadingDispatches = false, dispatches = result.data)
                }
                is Result.Error -> _uiState.update { state ->
                    // The in-memory dispatch snapshot is the available cache
                    // while offline. Do not replace it with a recurring error
                    // every 15 seconds; surface real API failures when online.
                    state.copy(
                        isLoadingDispatches = false,
                        feedbackMessage = if (networkMonitor.isOnlineNow()) {
                            "Dispatches unavailable: ${result.message}"
                        } else {
                            state.feedbackMessage
                        }
                    )
                }
                else -> _uiState.update { it.copy(isLoadingDispatches = false) }
            }
        }
    }

    fun updateDispatchStatus(dispatchId: Long, newStatus: String) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.serverIncidentId ?: incident.incidentId.toLongOrNull() ?: return
        val role = _uiState.value.userRole.uppercase()
        if (role != "ADMIN" && role != "RESPONDER") {
            _uiState.update { it.copy(feedbackMessage = "Only command or the assigned responder can update a dispatch") }
            return
        }
        if (role == "RESPONDER" && dispatchId !in _uiState.value.responderDispatchIds) {
            _uiState.update {
                it.copy(feedbackMessage = "This dispatch is assigned to another unit")
            }
            return
        }
        _uiState.update { it.copy(isUpdatingStatus = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.updateDispatchStatus(idLong, dispatchId, newStatus)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        val updatedDispatches = state.dispatches.map { dispatch ->
                            if (dispatch.id == dispatchId) dispatch.copy(status = newStatus) else dispatch
                        }
                        val hasActiveDispatch = updatedDispatches.any { dispatch ->
                            dispatch.status.uppercase() !in TERMINAL_DISPATCH_STATUSES
                        }
                        val incidentStatus = when (newStatus.uppercase()) {
                            "EN_ROUTE", "ON_SCENE" -> newStatus.uppercase()
                            "COMPLETED" -> if (hasActiveDispatch) state.incident?.status else "RESOLVED"
                            "CANCELLED", "DECLINED", "CANCEL" -> if (hasActiveDispatch) state.incident?.status else "RECEIVED"
                            else -> state.incident?.status
                        } ?: incident.status

                        state.copy(
                            isUpdatingStatus = false,
                            incident = state.incident?.copy(status = incidentStatus),
                            dispatches = updatedDispatches,
                            feedbackMessage = "Dispatch updated to $newStatus"
                        )
                    }
                    if (role == "RESPONDER") {
                        refreshResponderAssignments()
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isUpdatingStatus = false, feedbackMessage = "Failed: ${result.message}")
                }
                else -> Unit
            }
        }
    }

    fun updateStatus(newStatus: String) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.serverIncidentId ?: incident.incidentId.toLongOrNull() ?: return
        if (_uiState.value.userRole.uppercase() != "ADMIN") {
            _uiState.update { it.copy(feedbackMessage = "Only command can update the overall incident status") }
            return
        }
        _uiState.update { it.copy(isUpdatingStatus = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = updateIncidentStatusUseCase(idLong, newStatus)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isUpdatingStatus = false,
                        incident = it.incident?.copy(status = newStatus),
                        feedbackMessage = "Status updated to $newStatus"
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isUpdatingStatus = false, feedbackMessage = "Failed: ${result.message}")
                }
                else -> Unit
            }
        }
    }

    fun fetchAvailableResponders() {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.serverIncidentId ?: incident.incidentId.toLongOrNull() ?: return
        viewModelScope.launch {
            when (val result = incidentRepository.fetchAvailableResponders(idLong)) {
                is Result.Success -> _uiState.update { it.copy(availableResponders = result.data) }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun dispatchUnit(unitId: Long) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.serverIncidentId ?: incident.incidentId.toLongOrNull() ?: return
        _uiState.update { it.copy(isDispatchingUnit = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.dispatchIncident(idLong, unitId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isDispatchingUnit = false,
                            incident = it.incident?.copy(status = "DISPATCHED"),
                            feedbackMessage = "Unit dispatched successfully"
                        )
                    }
                    loadDispatches(idLong)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isDispatchingUnit = false, feedbackMessage = "Failed: ${result.message}")
                }
                else -> Unit
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private suspend fun refreshResponderAssignments() {
        if (_uiState.value.userRole != "RESPONDER") {
            updateMessageAccess()
            return
        }

        when (val result = responderRepository.getMyDispatches()) {
            is Result.Success -> _uiState.update {
                val activeDispatches = result.data.filter { dispatch ->
                    dispatch.status.uppercase() in ACTIVE_DISPATCH_STATUSES
                }
                it.copy(
                    responderDispatchIds = activeDispatches.mapTo(linkedSetOf()) { dispatch -> dispatch.id },
                    responderIncidentIds = activeDispatches.mapTo(linkedSetOf()) { dispatch -> dispatch.incidentId }
                )
            }
            is Result.Error -> _uiState.update { state ->
                if (result.exception is AssignmentVerificationDeniedException) {
                    // Only an explicit 401/403 from the assignment endpoint is
                    // authoritative enough to revoke cached responder access.
                    state.copy(
                        responderDispatchIds = emptySet(),
                        responderIncidentIds = emptySet(),
                        feedbackMessage = state.feedbackMessage ?: "This unit is no longer assigned to this incident"
                    )
                } else {
                    // Offline and transient online failures retain the last
                    // successful assignment snapshot for cache/mesh use.
                    state
                }
            }
            Result.Loading -> Unit
        }
        updateMessageAccess()
    }

    private fun updateMessageAccess() {
        val state = _uiState.value
        val incident = state.incident
        val allowed = when (state.userRole.uppercase()) {
            "ADMIN" -> incident != null
            "RESPONDER" -> incident != null && isResponderAssignedTo(state, incident)
            else -> false
        }
        messageAccessAllowed.value = allowed
        _uiState.update { current ->
            current.copy(
                canAccessMessages = allowed,
                messages = if (allowed) current.messages else emptyList()
            )
        }
    }

    private fun isResponderAssignedTo(incident: IncidentEntity): Boolean =
        isResponderAssignedTo(_uiState.value, incident)

    private fun isResponderAssignedTo(state: IncidentDetailUiState, incident: IncidentEntity): Boolean {
        val serverId = incident.serverIncidentId ?: incident.incidentId.toLongOrNull()
        return serverId != null && serverId in state.responderIncidentIds
    }

    private companion object {
        val ACTIVE_DISPATCH_STATUSES = setOf("ASSIGNED", "ACCEPTED", "EN_ROUTE", "ON_SCENE")
        val TERMINAL_DISPATCH_STATUSES = setOf("COMPLETED", "CANCELLED")
    }
}
