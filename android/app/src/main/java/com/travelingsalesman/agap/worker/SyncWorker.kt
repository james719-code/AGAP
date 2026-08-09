package com.travelingsalesman.agap.worker

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.travelingsalesman.agap.data.repository.AgencyRepository
import com.travelingsalesman.agap.data.repository.EmergencyTypeRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.PredefinedMessageRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.SyncStateManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import com.travelingsalesman.agap.data.local.dao.PendingSyncDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase,
    private val telemetryRepository: TelemetryRepository,
    private val incidentRepository: IncidentRepository,
    private val agencyRepository: AgencyRepository,
    private val emergencyTypeRepository: EmergencyTypeRepository,
    private val predefinedMessageRepository: PredefinedMessageRepository,
    private val syncStateManager: SyncStateManager,
    private val responderRepository: ResponderRepository,
    private val pendingSyncDao: PendingSyncDao,
    private val serverConfigManager: ServerConfigManager,
    private val authTokenManager: AuthTokenManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("SYNC_WORKER_SKIPPED_BLE_ONLY")
            return Result.success()
        }

        if (authTokenManager.authToken.firstOrNull().isNullOrBlank()) {
            BleMeshLog.i("SYNC_WORKER_DEFERRED_LOGGED_OUT")
            return Result.success()
        }

        BleMeshLog.i("SYNC_WORKER_STARTED")
        return try {
            // Queued emergency payloads are intentionally NOT uploaded here.
            // UplinkFlushWorker owns that lane so BLE gateway packets and local
            // reports keep the required 30-second inter-upload gap.
            UplinkFlushWorker.enqueue(
                context = applicationContext,
                reason = "sync_worker_online"
            )

            val meshEventResult = flushPendingPacketsUseCase.flushMeshDeliveryEvents()
            Timber.i("SyncWorker: Mesh delivery event upload success=${meshEventResult is com.travelingsalesman.agap.core.util.Result.Success}.")

            // Step 1: Process non-emergency pending sync queue items.
            val pendingQueue = pendingSyncDao.getPendingSyncItems("PENDING")
            for (item in pendingQueue) {
                try {
                    pendingSyncDao.deleteSyncItem(item.syncId)
                } catch (e: Exception) {
                    pendingSyncDao.updateSyncStatus(item.syncId, "FAILED")
                }
            }

            // Step 2: Sync downlink data into local Room cache using the server change cursor.
            val changesResult = telemetryRepository.getSyncChanges(
                syncStateManager.lastChangesSync.firstOrNull() ?: java.time.Instant.EPOCH.toString()
            )
            val incidentResult: com.travelingsalesman.agap.core.util.Result<*>
            val agencyResult: com.travelingsalesman.agap.core.util.Result<*>
            val emergencyTypeResult: com.travelingsalesman.agap.core.util.Result<*>
            val messageResult: com.travelingsalesman.agap.core.util.Result<*>

            if (changesResult is com.travelingsalesman.agap.core.util.Result.Success) {
                val changes = changesResult.data
                incidentRepository.cacheIncidents(changes.incidents)
                agencyRepository.cacheAgencies(changes.agencies)
                emergencyTypeRepository.syncEmergencyTypes(changes.emergencyTypes)
                predefinedMessageRepository.syncMessages(changes.predefinedMessages)
                syncStateManager.setLastChangesSync(changes.serverTime)
                incidentResult = changesResult
                agencyResult = changesResult
                emergencyTypeResult = changesResult
                messageResult = changesResult
            } else {
                incidentResult = incidentRepository.fetchRemoteIncidents()
                agencyResult = agencyRepository.getAgencies()
                emergencyTypeResult = emergencyTypeRepository.refreshEmergencyTypes()
                messageResult = predefinedMessageRepository.syncCatalog()
            }
            val responderResult = responderRepository.getResponders()

            Timber.i(
                "SyncWorker finished downlink pull: Incidents=${incidentResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Agencies=${agencyResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "EmergencyTypes=${emergencyTypeResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Messages=${messageResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Responders=${responderResult is com.travelingsalesman.agap.core.util.Result.Success}"
            )

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker encountered an error during sync")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "AgapEventDrivenSyncWork"
        private const val PERIODIC_WORK_NAME = "AgapPeriodicSyncWork"

        fun enqueueEventDrivenSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicSyncRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncRequest
            )
        }
    }
}
