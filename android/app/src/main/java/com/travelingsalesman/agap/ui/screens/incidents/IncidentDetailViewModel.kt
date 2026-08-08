package com.travelingsalesman.agap.ui.screens.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.dao.MessageDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.remote.IncidentApiService
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.data.remote.dto.SendMessageRequestDto
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.domain.usecase.UpdateIncidentStatusUseCase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentDetailUiState(
    val incident: IncidentEntity? = null,
    val dispatches: List<DispatchDto> = emptyList(),
    val messages: List<IncidentMessageEntity> = emptyList(),
    val userRole: String = "CITIZEN",
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
    private val messageDao: MessageDao,
    private val incidentApiService: IncidentApiService,
    private val updateIncidentStatusUseCase: UpdateIncidentStatusUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val incidentId: String = checkNotNull(savedStateHandle["incidentId"])
    private val incidentIdLong: Long? = incidentId.toLongOrNull()

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    init {
        // Observe role
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }

        // Observe messages from Room
        incidentIdLong?.let { id ->
            viewModelScope.launch {
                messageDao.getMessagesForIncident(id).collect { list ->
                    _uiState.update { it.copy(messages = list) }
                }
            }
        }

        // Fetch incident details from local store or remote API
        viewModelScope.launch {
            refresh()
        }
    }

    fun sendMessage(content: String) {
        val id = incidentIdLong ?: return
        if (content.isBlank()) return

        _uiState.update { it.copy(isSendingMessage = true) }

        viewModelScope.launch {
            try {
                val response = incidentApiService.sendMessage(id, SendMessageRequestDto(content))
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    messageDao.insertMessage(
                        IncidentMessageEntity(
                            id = dto.id,
                            incidentId = dto.incidentId,
                            senderId = dto.senderId,
                            senderName = dto.senderName,
                            senderRole = dto.senderRole,
                            messageContent = dto.messageContent,
                            messageType = dto.messageType,
                            createdAt = parseServerTime(dto.createdAt)
                        )
                    )
                } else {
                    _uiState.update { it.copy(feedbackMessage = "Failed to send message: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = "Network error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSendingMessage = false) }
            }
        }
    }

    private fun parseServerTime(time: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(time)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
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
                
                // Also fetch messages
                incidentIdLong?.let { id ->
                    val msgRes = incidentApiService.listMessages(id)
                    if (msgRes.isSuccessful && msgRes.body() != null) {
                        val messages = msgRes.body()!!.map { dto ->
                            IncidentMessageEntity(
                                id = dto.id,
                                incidentId = dto.incidentId,
                                senderId = dto.senderId,
                                senderName = dto.senderName,
                                senderRole = dto.senderRole,
                                messageContent = dto.messageContent,
                                messageType = dto.messageType,
                                createdAt = parseServerTime(dto.createdAt)
                            )
                        }
                        messageDao.insertMessages(messages)
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

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
