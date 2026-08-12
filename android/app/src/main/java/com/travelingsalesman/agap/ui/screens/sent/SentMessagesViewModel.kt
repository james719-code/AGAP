package com.travelingsalesman.agap.ui.screens.sent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.repository.IncidentReportRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.NotificationRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.worker.UplinkFlushWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.travelingsalesman.agap.data.repository.AuthRepository

data class SentMessagesUiState(
    val mySosHistory: List<LocalIncidentReportEntity> = emptyList(),
    val sentPackets: List<PendingPacketEntity> = emptyList(),
    val relayedEvents: List<MeshDeliveryEventEntity> = emptyList(),
    val relayedPackets: List<PendingPacketEntity> = emptyList(),
    val receivedIncidents: List<IncidentEntity> = emptyList(),
    val meshQueue: List<RelayQueueEntity> = emptyList(),
    val announcements: List<NotificationEntity> = emptyList(),
    val userRole: String = "CITIZEN",

    /** Distinct logical messages that still have neither cloud nor peer delivery. */
    val pendingDeliveryCount: Int = 0,

    /** Distinct packets with a confirmed outbound GATT delivery event. */
    val relayedCount: Int = 0,
    val unreadNotificationCount: Int = 0,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

private data class SentSnapshot(
    val localReports: List<LocalIncidentReportEntity>,
    val pending: List<PendingPacketEntity>,
    val incidents: List<IncidentEntity>,
    val meshQueue: List<RelayQueueEntity>,
    val relayedEvents: List<MeshDeliveryEventEntity>,
    val announcements: List<NotificationEntity>,
    val pendingDeliveryCount: Int
)

@HiltViewModel
class SentMessagesViewModel @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val incidentReportRepository: IncidentReportRepository,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SentMessagesUiState())
    val uiState: StateFlow<SentMessagesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
        viewModelScope.launch {
            val historyFlow = combine(
                incidentReportRepository.getRecentLocalSosHistoryFlow(MESSAGE_HISTORY_LIMIT).distinctUntilChanged(),
                telemetryRepository.getRecentPendingPacketsFlow(MESSAGE_HISTORY_LIMIT).distinctUntilChanged(),
                incidentRepository.getRecentIncidents(MESSAGE_HISTORY_LIMIT).distinctUntilChanged(),
                relayScheduler.observeQueuedRelays(MESSAGE_HISTORY_LIMIT).distinctUntilChanged(),
                telemetryRepository.getSuccessfullyRelayedEventsFlow(MESSAGE_HISTORY_LIMIT).distinctUntilChanged()
            ) { a, b, c, d, e ->
                // Using a list here because combine(f1,f2,f3,f4,f5) is a standard signature.
                listOf(a, b, c, d, e)
            }

            combine(
                historyFlow,
                notificationRepository.getRecentNotifications(MESSAGE_HISTORY_LIMIT).distinctUntilChanged(),
                relayScheduler.observeUndeliveredMessageCount().distinctUntilChanged()
            ) { historyList, notifications, undeliveredCount ->
                @Suppress("UNCHECKED_CAST")
                SentSnapshot(
                    localReports = historyList[0] as List<LocalIncidentReportEntity>,
                    pending = historyList[1] as List<PendingPacketEntity>,
                    incidents = historyList[2] as List<IncidentEntity>,
                    meshQueue = historyList[3] as List<RelayQueueEntity>,
                    relayedEvents = historyList[4] as List<MeshDeliveryEventEntity>,
                    announcements = notifications.filter { it.type == "ALERT" },
                    pendingDeliveryCount = undeliveredCount
                )
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        mySosHistory = snapshot.localReports,
                        sentPackets = snapshot.pending.filter { packet -> packet.hopCount == 0 },
                        relayedEvents = snapshot.relayedEvents,
                        relayedPackets = snapshot.pending.filter { packet -> packet.hopCount > 0 },
                        receivedIncidents = snapshot.incidents,
                        meshQueue = snapshot.meshQueue,
                        announcements = snapshot.announcements,
                        pendingDeliveryCount = snapshot.pendingDeliveryCount,
                        relayedCount = bleMeshManager.meshState.value.relayedPacketCount
                    )
                }
            }
        }
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
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
        viewModelScope.launch {
            try {
                // 1. Flush local outbox via worker
                UplinkFlushWorker.enqueue(
                    context = appContext,
                    reason = "manual_refresh_flush"
                )

                // 2. Fetch remote incidents to update the counter and feed
                incidentRepository.fetchRemoteIncidents()

                // 3. Sync notifications to get latest announcements
                notificationRepository.refreshNotifications()

                _uiState.update {
                    it.copy(feedbackMessage = "Data refreshed successfully.")
                }
            } catch (e: Exception) {
                _uiState.update {
                        it.copy(feedbackMessage = UserMessageResolver.network("Refresh telemetry", e))
                }
            } finally {
                _uiState.update {
                    it.copy(isRefreshing = false)
                }
            }
        }
    }

    fun deletePendingPacket(
        hash: String
    ) {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
        viewModelScope.launch {
            telemetryRepository
                .deletePendingPacket(
                    hash
                )
        }
    }

    fun retryViaMesh(
        packet: PendingPacketEntity
    ) {
        bleMeshManager
            .startAdvertisingSosPacket(
                packet
            )

        _uiState.update {
            it.copy(
                feedbackMessage =
                    "Queued for GATT relay: " +
                            "${packet.packetHash.take(12)}..."
            )
        }
    }

    fun retryMeshQueueNow(queueId: Long) {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadNotificationCount = count) }
            }
        }
        viewModelScope.launch {
            relayScheduler.makeRelayEligibleNow(queueId)
            bleMeshManager.requestImmediateRelayPulse()
            _uiState.update {
                it.copy(feedbackMessage = "BLE GATT relay retry requested.")
            }
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
