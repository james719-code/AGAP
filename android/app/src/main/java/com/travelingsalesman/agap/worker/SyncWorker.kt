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
import com.travelingsalesman.agap.data.repository.EmergencyTypeRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.PredefinedMessageRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.data.local.SyncStateManager
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import com.travelingsalesman.agap.data.local.dao.PendingSyncDao
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.remote.IncidentReportApiService
import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
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
    private val reportDao: ReportDao,
    private val pendingSyncDao: PendingSyncDao,
    private val reportApiService: IncidentReportApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("SyncWorker started: Draining pending packets, detailed reports, and pulling fresh backend state...")
        return try {
            // Step 1: Flushes pending packets with random jitter (0-60s) to avoid thundering herd on server DB
            val flushedCount = flushPendingPacketsUseCase(jitterOnStart = true)
            Timber.i("SyncWorker: Flushed $flushedCount uplink packets.")
            val meshEventResult = flushPendingPacketsUseCase.flushMeshDeliveryEvents()
            Timber.i("SyncWorker: Mesh delivery event upload success=${meshEventResult is com.travelingsalesman.agap.core.util.Result.Success}.")

            // Step 1b: Flush pending local incident reports to /api/v1/incident-reports
            val pendingReports = reportDao.getPendingReports()
            for (report in pendingReports) {
                try {
                    val dto = CreateReportRequestDto(
                        reportUuid = report.reportUuid,
                        transportToken = report.transportToken,
                        submissionType = report.submissionType,
                        subjectRelation = report.subjectRelation,
                        emergencyType = report.emergencyType,
                        subtype = report.subtype,
                        description = report.description,
                        peopleAffected = report.peopleAffected,
                        immediateDanger = report.immediateDanger,
                        locationSource = report.locationSource,
                        selectedBarangayCode = report.selectedBarangayCode,
                        latitude = report.latitude,
                        longitude = report.longitude,
                        gpsAccuracyMeters = report.gpsAccuracyMeters,
                        originChannel = "CELLULAR"
                    )
                    val response = reportApiService.createReport(dto)
                    if (response.isSuccessful && response.body()?.success == true) {
                        reportDao.insertReport(report.copy(syncState = "SYNCED"))
                        Timber.i("SyncWorker: Successfully flushed pending report UUID ${report.reportUuid}")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "SyncWorker: Failed to flush pending report UUID ${report.reportUuid}")
                }
            }

            // Step 1c: Process pending sync queue items
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
