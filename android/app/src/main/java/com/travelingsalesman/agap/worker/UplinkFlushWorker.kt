package com.travelingsalesman.agap.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.sync.ThrottleConfig
import com.travelingsalesman.agap.core.sync.QueuedUploadRateLimiter
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.remote.IncidentReportApiService
import com.travelingsalesman.agap.data.remote.dto.CreateReportRequestDto
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Single paced uplink lane for emergency data that was previously queued.
 *
 * BLE gateway packets and local incident reports share this worker so there is
 * never a burst upload when connectivity returns. A maximum of BATCH_SIZE items
 * is handled in one run and every consecutive queued upload has a 30-second gap.
 */
@HiltWorker
class UplinkFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase,
    private val reportDao: ReportDao,
    private val relayDao: RelayDao,
    private val reportApiService: IncidentReportApiService,
    private val serverConfigManager: ServerConfigManager,
    private val authTokenManager: AuthTokenManager,
    private val uploadRateLimiter: QueuedUploadRateLimiter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return Result.success()
        BleMeshLog.i("UPLINK_WORKER_STARTED", "runAttempt" to runAttemptCount)

        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("UPLINK_WORKER_DEFERRED_BLE_ONLY")
            return Result.success()
        }

        if (authTokenManager.authToken.firstOrNull().isNullOrBlank()) {
            // Keep queued emergency data intact while logged out. The application
            // schedules this worker again as soon as a new auth token is saved.
            BleMeshLog.i("UPLINK_WORKER_DEFERRED_LOGGED_OUT")
            return Result.success()
        }

        return try {
            var uploadedThisRun = 0

            // Gateway packets have priority because another phone may be depending
            // on this device to bridge an offline BLE path to the server.
            val gatewayResult = flushPendingPacketsUseCase(
                jitterOnStart = false,
                maxItems = ThrottleConfig.BATCH_SIZE
            )

            if (gatewayResult < 0) {
                BleMeshLog.w("UPLINK_WORKER_GATEWAY_RETRY")
                return Result.retry()
            }
            uploadedThisRun += gatewayResult

            if (uploadedThisRun < ThrottleConfig.BATCH_SIZE) {
                var report = reportDao.getNextPendingReport()

                while (
                    report != null &&
                    uploadedThisRun < ThrottleConfig.BATCH_SIZE
                ) {
                    if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
                        BleMeshLog.i(
                            "UPLINK_REPORTS_PAUSED_BLE_ONLY",
                            "uploaded" to uploadedThisRun
                        )
                        break
                    }

                    val currentReport = report ?: break
                    val dto = CreateReportRequestDto(
                        reportUuid = currentReport.reportUuid,
                        transportToken = currentReport.transportToken,
                        submissionType = currentReport.submissionType,
                        subjectRelation = currentReport.subjectRelation,
                        emergencyType = currentReport.emergencyType,
                        subtype = currentReport.subtype,
                        description = currentReport.description,
                        peopleAffected = currentReport.peopleAffected,
                        immediateDanger = currentReport.immediateDanger,
                        locationSource = currentReport.locationSource,
                        selectedBarangayCode = currentReport.selectedBarangayCode,
                        latitude = currentReport.latitude,
                        longitude = currentReport.longitude,
                        gpsAccuracyMeters = currentReport.gpsAccuracyMeters,
                        originChannel = if (
                            currentReport.syncState == "BLE_ONLY" ||
                            currentReport.syncState == "QUEUED_OFFLINE"
                        ) {
                            "BLE_MESH"
                        } else {
                            "CELLULAR"
                        }
                    )

                    BleMeshLog.i(
                        "QUEUED_REPORT_UPLOAD_START",
                        "token" to BleMeshLog.token(currentReport.transportToken),
                        "state" to currentReport.syncState,
                        "position" to (uploadedThisRun + 1)
                    )

                    val response = uploadRateLimiter.runWithSpacing(
                        label = "report"
                    ) {
                        reportApiService.createReport(dto)
                    }
                    if (response.code() == 409) {
                        reportDao.insertReport(
                            currentReport.copy(
                                syncState = "SYNCED",
                                serverIncidentId = response.body()?.report?.incidentId,
                                logicalId = currentReport.transportToken
                            )
                        )
                        relayDao.removeTokenFromQueue(currentReport.transportToken)
                        uploadedThisRun++
                        report = reportDao.getNextPendingReport()
                        continue
                    }
                    if (!response.isSuccessful || response.body()?.success != true) {
                        BleMeshLog.w(
                            "QUEUED_REPORT_UPLOAD_FAILED",
                            "token" to BleMeshLog.token(currentReport.transportToken),
                            "code" to response.code()
                        )
                        return Result.retry()
                    }

                    reportDao.insertReport(
                        currentReport.copy(
                            syncState = "SYNCED",
                            serverIncidentId = response.body()?.report?.incidentId,
                            logicalId = currentReport.transportToken
                        )
                    )
                    relayDao.removeTokenFromQueue(currentReport.transportToken)
                    uploadedThisRun++

                    BleMeshLog.i(
                        "QUEUED_REPORT_UPLOAD_SUCCESS",
                        "token" to BleMeshLog.token(currentReport.transportToken),
                        "uploaded" to uploadedThisRun
                    )

                    report = reportDao.getNextPendingReport()
                }
            }

            val gatewayRemaining = flushPendingPacketsUseCase.hasPendingPackets()
            val reportsRemaining = reportDao.hasPendingReports()

            if (
                serverConfigManager.getTransmissionMode() != TransmissionMode.FORCE_BLE_MESH &&
                (gatewayRemaining || reportsRemaining)
            ) {
                BleMeshLog.i(
                    "UPLINK_WORKER_MORE_QUEUED",
                    "gatewayRemaining" to gatewayRemaining,
                    "reportsRemaining" to reportsRemaining,
                    "nextDelayMs" to ThrottleConfig.INTERNET_UPLOAD_INTERVAL_MS
                )
                enqueue(
                    context = applicationContext,
                    initialDelayMs = ThrottleConfig.INTERNET_UPLOAD_INTERVAL_MS,
                    reason = "queue_remaining"
                )
            }

            BleMeshLog.i(
                "UPLINK_WORKER_DONE",
                "uploaded" to uploadedThisRun,
                "gatewayRemaining" to gatewayRemaining,
                "reportsRemaining" to reportsRemaining
            )
            Result.success()
        } catch (e: Exception) {
            BleMeshLog.e("UPLINK_WORKER_FAILED", error = e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "AgapQueuedEmergencyUplink"
        private const val ENQUEUE_COALESCE_MS = 1_500L
        private val lastImmediateEnqueueAtMs = AtomicLong(0L)

        fun cancel(context: Context, reason: String = "unspecified") {
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
            BleMeshLog.i("UPLINK_WORK_CANCEL", "reason" to reason)
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(WORK_NAME)
        }

        fun enqueue(
            context: Context,
            initialDelayMs: Long = 0L,
            reason: String = "unspecified"
        ) {
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
            // Network restoration, login, mode changes, and two queue Flows can all
            // fire within the same second. Collapse that trigger storm before it
            // creates a long APPEND_OR_REPLACE WorkManager chain. Delayed self-
            // continuation is never coalesced.
            if (initialDelayMs <= 0L) {
                val now = System.currentTimeMillis()
                while (true) {
                    val previous = lastImmediateEnqueueAtMs.get()
                    if (now - previous < ENQUEUE_COALESCE_MS) {
                        BleMeshLog.d(
                            "UPLINK_WORK_COALESCED",
                            "reason" to reason,
                            "sinceLastMs" to (now - previous)
                        )
                        return
                    }
                    if (lastImmediateEnqueueAtMs.compareAndSet(previous, now)) break
                }
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<UplinkFlushWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    ThrottleConfig.INTERNET_UPLOAD_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )

            if (initialDelayMs > 0L) {
                requestBuilder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            }

            BleMeshLog.d(
                "UPLINK_WORK_ENQUEUE",
                "reason" to reason,
                "initialDelayMs" to initialDelayMs
            )

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                requestBuilder.build()
            )
        }
    }
}
