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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SentMessagesUiState(
    val sentPackets:
    List<PendingPacketEntity> =
        emptyList(),

    val relayedPackets:
    List<PendingPacketEntity> =
        emptyList(),

    val receivedIncidents:
    List<IncidentEntity> =
        emptyList(),

    val isFlushing:
    Boolean = false,

    val feedbackMessage:
    String? = null
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

    init {
        viewModelScope.launch {
            combine(
                telemetryRepository
                    .getRecentPendingPacketsFlow(
                        MESSAGE_HISTORY_LIMIT
                    )
                    .distinctUntilChanged(),

                incidentRepository
                    .getRecentIncidents(
                        MESSAGE_HISTORY_LIMIT
                    )
                    .distinctUntilChanged()
            ) { pending, incidents ->
                pending to incidents
            }.collect {
                    (pending, incidents) ->

                _uiState.update {
                    it.copy(
                        sentPackets =
                            pending.filter {
                                    packet ->
                                packet.hopCount == 0
                            },

                        relayedPackets =
                            pending.filter {
                                    packet ->
                                packet.hopCount > 0
                            },

                        receivedIncidents =
                            incidents
                    )
                }
            }
        }
    }

    fun flushOutboxNow() {
        if (_uiState.value.isFlushing) {
            return
        }

        _uiState.update {
            it.copy(
                isFlushing = true,
                feedbackMessage = null
            )
        }

        viewModelScope.launch {
            flushPendingPacketsUseCase()

            _uiState.update {
                it.copy(
                    isFlushing = false,
                    feedbackMessage =
                        "Uplink flush completed!"
                )
            }
        }
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