package com.travelingsalesman.agap.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.dao.AdminAlertDao
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.NotificationDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.remote.NotificationApiService
import com.travelingsalesman.agap.data.remote.dto.AlertCategory
import com.travelingsalesman.agap.data.remote.dto.AlertSeverity
import com.travelingsalesman.agap.data.repository.NotificationRepository
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.repository.PredefinedMessageRepository
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentReportRepository
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

import timber.log.Timber

import kotlinx.coroutines.flow.firstOrNull

data class NotificationsUiState(
    val announcements: List<NotificationEntity> = emptyList(),
    val adminAlerts: List<AdminAlertEntity> = emptyList(),
    val broadcastChoices: List<PredefinedMessageEntity> = emptyList(),
    val personalNotifications: List<NotificationEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val pendingPackets: List<PendingPacketEntity> = emptyList(),
    val sentPackets: List<PendingPacketEntity> = emptyList(),
    val relayedPackets: List<PendingPacketEntity> = emptyList(),
    val meshQueue: List<RelayQueueEntity> = emptyList(),
    val relayedPacketHashes: List<String> = emptyList(),
    val mySosHistory: List<LocalIncidentReportEntity> = emptyList(),
    val userRole: String = "CITIZEN",
    val totalRelayCount: Int = 0,
    val pendingDeliveryCount: Int = 0,
    val relayedCount: Int = 0,
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val adminAlertDao: AdminAlertDao,
    private val pendingPacketDao: PendingPacketDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao,
    private val apiService: NotificationApiService,
    private val notificationRepository: NotificationRepository,
    private val predefinedMessageRepository: PredefinedMessageRepository,
    private val telemetryRepository: TelemetryRepository,
    private val authRepository: AuthRepository,
    private val incidentReportRepository: IncidentReportRepository,
    private val bleMeshManager: BleMeshManager,
    private val relayScheduler: BleRelayScheduler,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var autoSyncJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(userRole = role ?: "CITIZEN") }
            }
        }

        viewModelScope.launch {
            incidentReportRepository.getRecentLocalSosHistoryFlow(200).collect { history ->
                _uiState.update { it.copy(mySosHistory = history) }
            }
        }

        viewModelScope.launch {
            notificationDao.getRecentNotifications(NOTIFICATION_WORKING_SET).collect { list ->
                _uiState.update { 
                    it.copy(
                        notifications = list,
                        personalNotifications = list
                    ) 
                }
            }
        }

        viewModelScope.launch {
            adminAlertDao.getAllAlerts().collect { alerts ->
                Timber.d("Admin alerts updated: ${alerts.size} alerts")
                _uiState.update { it.copy(adminAlerts = alerts) }
            }
        }

        viewModelScope.launch {
            predefinedMessageRepository.getMessagesFlow().collect { choices ->
                Timber.d("Broadcast choices updated: ${choices.size} choices")
                _uiState.update { it.copy(broadcastChoices = choices) }
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

                kotlinx.coroutines.coroutineScope {
                    val nJob = launch { notificationRepository.refreshNotifications() }
                    val aJob = launch { notificationRepository.refreshAlerts() }
                    nJob.join()
                    aJob.join()
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
                notificationRepository.markAsRead(id)
            } catch (_: Exception) {}
        }
    }

    fun markAlertAsRead(id: Long) {
        viewModelScope.launch {
            try {
                notificationRepository.markAlertAsRead(id)
            } catch (_: Exception) {}
        }
    }

    fun createAlert(
        title: String,
        body: String,
        category: AlertCategory,
        severity: AlertSeverity,
        isPinned: Boolean,
        predefinedMessageId: Long? = null
    ) {
        Timber.d("Attempting to broadcast alert: $title, $severity")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = notificationRepository.createAlert(title, body, category, severity, isPinned, predefinedMessageId)
            
            if (result is Result.Success) {
                Timber.i("Alert created on server: ${result.data.uuid}")
                val dto = result.data
                val pubAt = parseServerTime(dto.publishedAt)
                val expAt = if (dto.expiresAt != null) parseServerTime(dto.expiresAt) else null
                
                val entity = AdminAlertEntity(
                    id = dto.id,
                    uuid = dto.uuid,
                    authorId = dto.authorId,
                    authorName = dto.authorName,
                    title = dto.title,
                    body = dto.body,
                    category = dto.category.name,
                    severity = dto.severity.name,
                    isPinned = dto.isPinned,
                    isActive = dto.isActive,
                    publishedAt = pubAt,
                    expiresAt = expAt,
                    createdAt = parseServerTime(dto.createdAt),
                    isRead = dto.isRead,
                    isPlaceholder = false
                )
                // Start BLE broadcast
                bleMeshManager.startAdvertisingAdminAlert(entity)
                _uiState.update { it.copy(isLoading = false, feedbackMessage = "Alert broadcasted via Cloud & Mesh") }
            } else {
                // FALLBACK: Network failed or backend error occurred.
                // Broadcast via BLE Mesh and save a local placeholder.
                Timber.w("Cloud broadcast failed. Falling back to BLE Mesh Relay.")
                
                val localId = System.currentTimeMillis() * -1 // Unique negative ID
                val localUuid = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                
                // Get current user info for local record if possible
                val currentUserId = authRepository.userId.firstOrNull() ?: 0L
                val currentUserName = authRepository.username.firstOrNull() ?: "Admin (Offline)"

                val fallbackEntity = AdminAlertEntity(
                    id = localId,
                    uuid = localUuid,
                    authorId = currentUserId,
                    authorName = currentUserName,
                    title = title,
                    body = body,
                    category = category.name,
                    severity = severity.name,
                    isPinned = isPinned,
                    isActive = true,
                    publishedAt = now,
                    expiresAt = now + 4 * 3600_000,
                    createdAt = now,
                    isRead = true,
                    isPlaceholder = true
                )
                
                // Save locally so it appears in the timeline
                adminAlertDao.insertAlerts(listOf(fallbackEntity))
                
                // Broadcast via BLE
                bleMeshManager.startAdvertisingAdminAlert(fallbackEntity)
                
                val errorMsg = (result as? Result.Error)?.message ?: "Network error"
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        feedbackMessage = "Cloud sync failed ($errorMsg). Alert sent via BLE Mesh Relay."
                    ) 
                }
            }
        }
    }

    fun broadcastPredefined(message: PredefinedMessageEntity, isEmergency: Boolean) {
        createAlert(
            title = "OFFICIAL COMMAND BROADCAST",
            body = message.messageText,
            category = AlertCategory.GENERAL,
            severity = if (isEmergency) AlertSeverity.CRITICAL else AlertSeverity.INFO,
            isPinned = isEmergency,
            predefinedMessageId = message.id.toLong()
        )
    }

    fun stopBroadcast(alert: AdminAlertEntity) {
        viewModelScope.launch {
            try {
                // 1. Mark as inactive locally
                adminAlertDao.insertAlerts(listOf(alert.copy(isActive = false)))
                
                // 2. Remove from local relay queue
                relayScheduler.removeTokenFromQueue(alert.uuid)
                
                // 3. Send "CANCELLED" status via mesh to tell neighbors to stop relaying
                val tokenBytes = tokenBytesFromIdentifier(alert.uuid)
                bleMeshManager.startAdvertisingStatusByToken(tokenBytes, 7) // 7 = INCIDENT_CANCELLED
                
                _uiState.update { it.copy(feedbackMessage = "Broadcast termination signal sent to mesh.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackMessage = "Failed to stop broadcast.") }
            }
        }
    }

    private fun tokenBytesFromIdentifier(identifier: String): ByteArray {
        val clean = identifier.trim().lowercase()
        if (clean.matches(Regex("^[0-9a-f]{12}$"))) {
            return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(identifier.toByteArray(Charsets.UTF_8)).copyOfRange(0, 6)
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

