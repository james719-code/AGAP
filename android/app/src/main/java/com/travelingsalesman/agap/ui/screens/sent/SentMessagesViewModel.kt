package com.travelingsalesman.agap.ui.screens.sent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SentMessagesUiState(
    val sentPackets: List<PendingPacketEntity> = emptyList(),
    val relayedPackets: List<PendingPacketEntity> = emptyList(),
    val receivedIncidents: List<IncidentEntity> = emptyList(),
    
    val filteredSentPackets: List<PendingPacketEntity> = emptyList(),
    val filteredRelayedPackets: List<PendingPacketEntity> = emptyList(),
    val filteredIncidents: List<IncidentEntity> = emptyList(),

    val searchQuery: String = "",
    val selectedStatusFilter: String? = null,
    val selectedTypeFilter: String? = null,
    
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class SentMessagesViewModel @Inject constructor(
    private val telemetryRepository:
    TelemetryRepository,

    private val incidentRepository:
    IncidentRepository,

    private val flushPendingPacketsUseCase:
    FlushPendingPacketsUseCase,

    private val bleMeshManager:
    BleMeshManager
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            SentMessagesUiState()
        )

    val uiState:
            StateFlow<SentMessagesUiState> =
        _uiState.asStateFlow()

    private var lastIncidents: List<IncidentEntity> = emptyList()
    private var lastPending: List<PendingPacketEntity> = emptyList()

    init {
        viewModelScope.launch {
            telemetryRepository.getRecentPendingPacketsFlow(MESSAGE_HISTORY_LIMIT)
                .distinctUntilChanged()
                .collect { pending ->
                    lastPending = pending
                    applyFilters()
                }
        }

        viewModelScope.launch {
            incidentRepository.getRecentIncidents(MESSAGE_HISTORY_LIMIT)
                .distinctUntilChanged()
                .collect { incidents ->
                    lastIncidents = incidents
                    applyFilters()
                }
        }

        // Initial fetch
        refresh()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery
        val status = state.selectedStatusFilter
        val type = state.selectedTypeFilter

        // Filter Incidents (Community Feed)
        val filteredIncidents = lastIncidents.filter { incident ->
            val matchesQuery = query.isBlank() || incident.matchesQuery(query)
            val matchesStatus = status == null || incident.status.equals(status, ignoreCase = true)
            val matchesType = type == null || incident.emergencyType.equals(type, ignoreCase = true)
            matchesQuery && matchesStatus && matchesType
        }

        // Filter Sent Packets (My SOS)
        val filteredSent = lastPending
            .filter { it.hopCount == 0 }
            .filter { packet ->
                val matchesQuery = query.isBlank() || packet.matchesQuery(query)
                val matchesType = type == null || packet.emergencyType.equals(type, ignoreCase = true)
                // Sent packets don't have "Operational Status" in the same way, but we can match type
                matchesQuery && matchesType
            }

        // Filter Relayed Packets
        val filteredRelayed = lastPending
            .filter { it.hopCount > 0 }
            .filter { packet ->
                val matchesQuery = query.isBlank() || packet.matchesQuery(query)
                val matchesType = type == null || packet.emergencyType.equals(type, ignoreCase = true)
                matchesQuery && matchesType
            }

        _uiState.update {
            it.copy(
                sentPackets = lastPending.filter { p -> p.hopCount == 0 },
                relayedPackets = lastPending.filter { p -> p.hopCount > 0 },
                receivedIncidents = lastIncidents,
                filteredIncidents = filteredIncidents,
                filteredSentPackets = filteredSent,
                filteredRelayedPackets = filteredRelayed
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setStatusFilter(status: String?) {
        _uiState.update { it.copy(selectedStatusFilter = status) }
        applyFilters()
    }

    fun setTypeFilter(type: String?) {
        _uiState.update { it.copy(selectedTypeFilter = type) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update { it.copy(searchQuery = "", selectedStatusFilter = null, selectedTypeFilter = null) }
        applyFilters()
    }

    private fun IncidentEntity.matchesQuery(query: String): Boolean {
        return incidentId.contains(query, ignoreCase = true) ||
                emergencyType.contains(query, ignoreCase = true) ||
                status.contains(query, ignoreCase = true) ||
                description.orEmpty().contains(query, ignoreCase = true)
    }

    private fun PendingPacketEntity.matchesQuery(query: String): Boolean {
        return packetHash.contains(query, ignoreCase = true) ||
                emergencyType.contains(query, ignoreCase = true) ||
                targetId.contains(query, ignoreCase = true)
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) {
            return
        }

        _uiState.update {
            it.copy(
                isRefreshing = true,
                feedbackMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // 1. Flush outgoing telemetry (Updates SENT/RELAYED)
                flushPendingPacketsUseCase()
                
                // 2. Fetch latest incidents (Updates RECEIVED)
                incidentRepository.fetchRemoteIncidents()

                _uiState.update {
                    it.copy(
                        feedbackMessage = "Telemetry and alerts synchronized!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        feedbackMessage = "Sync partially failed: ${e.message}"
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(isRefreshing = false)
                }
            }
        }
    }

    fun flushOutboxNow() {
        refresh()
    }

    fun deletePendingPacket(
        hash: String
    ) {
        viewModelScope.launch {
            telemetryRepository
                .deletePendingPacket(
                    hash
                )
        }
    }

    fun broadcastViaMesh(
        packet: PendingPacketEntity
    ) {
        bleMeshManager
            .startAdvertisingSosPacket(
                packet
            )

        _uiState.update {
            it.copy(
                feedbackMessage =
                    "Broadcasting P2P: " +
                            "${packet.packetHash.take(12)}..."
            )
        }
    }

    fun clearFeedback() {
        _uiState.update {
            it.copy(
                feedbackMessage = null
            )
        }
    }

    companion object {
        private const val MESSAGE_HISTORY_LIMIT =
            200
    }
}