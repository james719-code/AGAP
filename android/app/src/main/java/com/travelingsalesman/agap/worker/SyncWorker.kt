package com.travelingsalesman.agap.worker

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.network.RealtimeSyncCoordinator
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
import com.travelingsalesman.agap.data.repository.IncidentMessagingRepository
import com.travelingsalesman.agap.data.repository.PredefinedMessageRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.data.local.SyncStateManager
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
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
    private val incidentMessagingRepository: IncidentMessagingRepository,
    private val agencyRepository: AgencyRepository,
    private val emergencyTypeRepository: EmergencyTypeRepository,
    private val predefinedMessageRepository: PredefinedMessageRepository,
    private val syncStateManager: SyncStateManager,
    private val responderRepository: ResponderRepository,
    private val serverConfigManager: ServerConfigManager,
    private val authTokenManager: AuthTokenManager,
    private val realtimeSyncCoordinator: RealtimeSyncCoordinator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return Result.success()
        if (authTokenManager.getAuthToken().isNullOrBlank()) {
            BleMeshLog.i("SYNC_WORKER_SKIPPED_SIGNED_OUT")
            return Result.success()
        }
        val syncUserId = authTokenManager.userId.firstOrNull() ?: return Result.success()

        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("SYNC_WORKER_SKIPPED_BLE_ONLY")
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

            // Pull authoritative downlink data before resolving queued replies so
            // packetHash -> server incident mappings and catalog IDs are current.
            val changesResult = telemetryRepository.getSyncChanges(
                syncStateManager.lastChangesSync.firstOrNull() ?: java.time.Instant.EPOCH.toString()
            )
            if (authTokenManager.userId.firstOrNull() != syncUserId) return Result.success()
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
                syncStateManager.setLastChangesSync(changes.serverTime, syncUserId)
                realtimeSyncCoordinator.onHostedSync(true)
                incidentResult = changesResult
                agencyResult = changesResult
                emergencyTypeResult = changesResult
                messageResult = changesResult
            } else {
                realtimeSyncCoordinator.onHostedSync(false)
                incidentResult = incidentRepository.fetchRemoteIncidents()
                agencyResult = agencyRepository.getAgencies()
                emergencyTypeResult = emergencyTypeRepository.refreshEmergencyTypes()
                messageResult = predefinedMessageRepository.syncCatalog()
            }
            val responderResult = responderRepository.getResponders()

            // Resolve/upload locally-authored offline incident replies only after
            // downlink has refreshed packetHash -> server incident mappings.
            val uploadedReplies = incidentMessagingRepository.flushPendingReplies()

            Timber.i(
                "SyncWorker finished downlink pull: Incidents=${incidentResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Agencies=${agencyResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "EmergencyTypes=${emergencyTypeResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Messages=${messageResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Responders=${responderResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "OfflineRepliesUploaded=$uploadedReplies"
            )

            Result.success()
        } catch (e: Exception) {
            realtimeSyncCoordinator.onHostedSync(false)
            Timber.e(e, "SyncWorker encountered an error during sync")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "AgapEventDrivenSyncWork"
        private const val PERIODIC_WORK_NAME = "AgapPeriodicSyncWork"

        fun enqueueEventDrivenSync(context: Context) {
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
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
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
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
