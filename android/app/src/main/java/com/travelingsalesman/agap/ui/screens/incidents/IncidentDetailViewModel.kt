package com.travelingsalesman.agap.ui.screens.incidents

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
import com.travelingsalesman.agap.domain.usecase.UpdateIncidentStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val incident: IncidentEntity? = null,
    val dispatches: List<DispatchDto> = emptyList(),
    val messages: List<IncidentMessageEntity> = emptyList(),
    val replyChoices: List<PredefinedMessageEntity> = emptyList(),
    val userRole: String = "CITIZEN",
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val isUpdatingStatus: Boolean = false,
    val isLoadingDispatches: Boolean = false,
    val isSendingMessage: Boolean = false,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val incidentRepository: IncidentRepository,
    private val incidentMessagingRepository: IncidentMessagingRepository,
    private val updateIncidentStatusUseCase: UpdateIncidentStatusUseCase,
    private val authRepository: AuthRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()
    private var autoSyncJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }

        viewModelScope.launch {
            serverConfigManager.transmissionMode.collect { mode ->
                _uiState.update { it.copy(transmissionMode = mode) }
            }
        }

        // Conversation lookup works for numeric cloud IDs and local mesh UUIDs.
        viewModelScope.launch {
            incidentMessagingRepository.observeMessages(incidentId).collect { list ->
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
                if (_uiState.value.transmissionMode == TransmissionMode.FORCE_BLE_MESH) continue
                runCatching {
                    incidentMessagingRepository.refreshOnlineMessages(entity)
                    loadDispatches(entity.serverIncidentId ?: entity.incidentId.toLongOrNull())
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
                var entity = incidentRepository.getIncidentById(incidentId)
                if (entity == null) {
                    val idLong = incidentId.toLongOrNull()
                    if (idLong != null) {
                        val result = incidentRepository.fetchIncidentById(idLong)
                        if (result is Result.Success) entity = result.data
                    }
                }

                // Refresh the authoritative reply catalog first when the current
                // transmission policy permits Internet. Online message history can
                // then resolve predefined IDs/protocol codes against the latest cache.
                // Offline/BLE-only operation keeps using the last Room snapshot.
                incidentMessagingRepository.refreshCatalog()

                if (entity != null) {
                    _uiState.update { it.copy(incident = entity) }
                    loadDispatches(entity.serverIncidentId ?: entity.incidentId.toLongOrNull())
                    incidentMessagingRepository.refreshOnlineMessages(entity)
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
                is Result.Error -> _uiState.update {
                    it.copy(
                        isLoadingDispatches = false,
                        feedbackMessage = "Dispatches unavailable: ${result.message}"
                    )
                }
                else -> _uiState.update { it.copy(isLoadingDispatches = false) }
            }
        }
    }

    fun updateDispatchStatus(dispatchId: Long, newStatus: String) {
        val incident = _uiState.value.incident ?: return
        val idLong = incident.serverIncidentId ?: incident.incidentId.toLongOrNull() ?: return
        _uiState.update { it.copy(isUpdatingStatus = true, feedbackMessage = null) }

        viewModelScope.launch {
            when (val result = incidentRepository.updateDispatchStatus(idLong, dispatchId, newStatus)) {
                is Result.Success -> {
                    val incidentStatus = when (newStatus.uppercase()) {
                        "EN_ROUTE", "ON_SCENE" -> newStatus.uppercase()
                        "COMPLETED" -> "RESOLVED"
                        "CANCELLED", "DECLINED", "CANCEL" -> "RECEIVED"
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

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
