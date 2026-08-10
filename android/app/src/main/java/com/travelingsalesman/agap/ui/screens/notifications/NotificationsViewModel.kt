package com.travelingsalesman.agap.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val pendingPackets: List<PendingPacketEntity> = emptyList(),
    val sentPackets: List<PendingPacketEntity> = emptyList(),
    val relayedPackets: List<PendingPacketEntity> = emptyList(),
    val meshQueue: List<RelayQueueEntity> = emptyList(),
    val relayedPacketHashes: List<String> = emptyList(),
    val totalRelayCount: Int = 0,
    val pendingDeliveryCount: Int = 0,
    val relayedCount: Int = 0,
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val pendingPacketDao: PendingPacketDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao,
    private val apiService: NotificationApiService,
    private val telemetryRepository: TelemetryRepository,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var autoSyncJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            notificationDao.getRecentNotifications(NOTIFICATION_WORKING_SET).collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }

        viewModelScope.launch {
            pendingPacketDao.getAllPendingPacketsFlow().collect { list ->
                _uiState.update { it.copy(pendingPackets = list) }
            }
        }

        viewModelScope.launch {
            meshDeliveryEventDao.observeSuccessfulRelayCount().collect { count ->
                _uiState.update { it.copy(totalRelayCount = count) }
            }
        }

        viewModelScope.launch {
            meshDeliveryEventDao.observeRecentSuccessfullyRelayedPacketHashes(10).collect { list ->
                _uiState.update { it.copy(relayedPacketHashes = list) }
            }
        }

        viewModelScope.launch {
            combine(
                telemetryRepository.getRecentPendingPacketsFlow(NOTIFICATION_WORKING_SET).distinctUntilChanged(),
                relayScheduler.observeQueuedRelays(NOTIFICATION_WORKING_SET).distinctUntilChanged(),
                telemetryRepository.getRecentSuccessfullyRelayedPacketHashesFlow(NOTIFICATION_WORKING_SET * 2).distinctUntilChanged()
            ) { pending, meshQueue, relayedHashes ->
                Triple(pending, meshQueue, relayedHashes.toSet())
            }.combine(
                relayScheduler.observeUndeliveredMessageCount().distinctUntilChanged()
            ) { (pending, meshQueue, successfullyRelayed), undeliveredCount ->
                _uiState.update { current ->
                    current.copy(
                        sentPackets = pending.filter { it.hopCount == 0 },
                        relayedPackets = pending.filter { it.hopCount > 0 && it.packetHash in successfullyRelayed },
                        meshQueue = meshQueue,
                        pendingDeliveryCount = undeliveredCount,
                        relayedCount = bleMeshManager.meshState.value.relayedPacketCount
                    )
                }
            }.collect {}
        }

        refresh()
        startAutoSync()
    }

    fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(NOTIFICATION_SYNC_INTERVAL_MS)
                refresh()
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                UplinkFlushWorker.enqueue(
                    context = appContext,
                    reason = "notifications_manual_refresh"
                )

                val response = apiService.listNotifications()
                if (response.isSuccessful && response.body() != null) {
                    val entities = response.body()!!.map { dto ->
                        NotificationEntity(
                            id = dto.id,
                            userId = dto.userId,
                            title = dto.title,
                            message = dto.message,
                            type = dto.type,
                            isRead = dto.isRead,
                            referenceId = dto.referenceId,
                            createdAt = parseServerTime(dto.createdAt)
                        )
                    }
                    entities.chunked(NOTIFICATION_WRITE_CHUNK).forEach { batch ->
                        notificationDao.insertNotifications(batch)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(feedbackMessage = UserMessageResolver.network("Refresh notifications", e))
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            try {
                apiService.markAsRead(id)
                notificationDao.markAsRead(id)
            } catch (_: Exception) {}
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            notificationDao.clearAll()
        }
    }

    fun deletePendingPacket(hash: String) {
        viewModelScope.launch {
            pendingPacketDao.deleteByHash(hash)
            telemetryRepository.deletePendingPacket(hash)
            relayScheduler.removeQueueEntry(hash.toLongOrNull() ?: -1L)
        }
    }

    fun deleteMeshQueueItem(queueId: Long) {
        viewModelScope.launch {
            relayScheduler.removeQueueEntry(queueId)
            _uiState.update { it.copy(feedbackMessage = "Removed queue item #$queueId") }
        }
    }

    fun clearMeshQueue() {
        viewModelScope.launch {
            pendingPacketDao.clearAll()
            relayScheduler.clearAll()
            _uiState.update { it.copy(feedbackMessage = "Cleared all mesh queue packets.") }
        }
    }

    fun retryViaMesh(packet: PendingPacketEntity) {
        bleMeshManager.startAdvertisingSosPacket(packet)
        _uiState.update {
            it.copy(feedbackMessage = "Queued for GATT relay: ${packet.packetHash.take(12)}...")
        }
    }

    fun retryMeshQueueNow(queueId: Long) {
        viewModelScope.launch {
            relayScheduler.makeRelayEligibleNow(queueId)
            bleMeshManager.requestImmediateRelayPulse()
            _uiState.update {
                it.copy(feedbackMessage = "BLE GATT relay retry requested.")
            }
        }
    }

    private fun parseServerTime(time: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(time)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    companion object {
        private const val NOTIFICATION_WORKING_SET = 250
        private const val NOTIFICATION_WRITE_CHUNK = 200
        private const val NOTIFICATION_SYNC_INTERVAL_MS = 30_000L
    }
}

