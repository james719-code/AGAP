package com.travelingsalesman.agap.worker

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
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase,
    private val incidentRepository: IncidentRepository,
    private val agencyRepository: AgencyRepository,
    private val responderRepository: ResponderRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("SyncWorker started: Draining pending packets and pulling fresh backend state...")
        return try {
            // Step 1: Flushes pending packets with random jitter (0-60s) to avoid thundering herd on server DB
            val flushedCount = flushPendingPacketsUseCase(jitterOnStart = true)
            Timber.i("SyncWorker: Flushed $flushedCount uplink packets.")

            // Step 2: Sync downlink data (Incidents, Agencies, Responders) into local Room cache
            val incidentResult = incidentRepository.fetchRemoteIncidents()
            val agencyResult = agencyRepository.getAgencies()
            val responderResult = responderRepository.getResponders()

            Timber.i(
                "SyncWorker finished downlink pull: Incidents=${incidentResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
                        "Agencies=${agencyResult is com.travelingsalesman.agap.core.util.Result.Success}, " +
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
