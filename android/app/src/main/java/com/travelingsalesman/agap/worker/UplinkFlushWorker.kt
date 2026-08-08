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
                var reports = reportDao.getPendingReports()

                while (
                    reports.isNotEmpty() &&
                    uploadedThisRun < ThrottleConfig.BATCH_SIZE
                ) {
                    if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
                        BleMeshLog.i(
                            "UPLINK_REPORTS_PAUSED_BLE_ONLY",
                            "uploaded" to uploadedThisRun
                        )
                        break
                    }

                    val report = reports.first()
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
                        originChannel = if (
                            report.syncState == "BLE_ONLY" ||
                            report.syncState == "QUEUED_OFFLINE"
                        ) {
                            "BLE_MESH"
                        } else {
                            "CELLULAR"
                        }
                    )

                    BleMeshLog.i(
                        "QUEUED_REPORT_UPLOAD_START",
                        "token" to BleMeshLog.token(report.transportToken),
                        "state" to report.syncState,
                        "position" to (uploadedThisRun + 1)
                    )

                    val response = uploadRateLimiter.runWithSpacing(
                        label = "report"
                    ) {
                        reportApiService.createReport(dto)
                    }
                    if (!response.isSuccessful || response.body()?.success != true) {
                        BleMeshLog.w(
                            "QUEUED_REPORT_UPLOAD_FAILED",
                            "token" to BleMeshLog.token(report.transportToken),
                            "code" to response.code()
                        )
                        return Result.retry()
                    }

                    reportDao.insertReport(report.copy(syncState = "SYNCED"))
                    relayDao.removeTokenFromQueue(report.transportToken)
                    uploadedThisRun++

                    BleMeshLog.i(
                        "QUEUED_REPORT_UPLOAD_SUCCESS",
                        "token" to BleMeshLog.token(report.transportToken),
                        "uploaded" to uploadedThisRun
                    )

                    reports = reportDao.getPendingReports()
                }
            }

            val gatewayRemaining = flushPendingPacketsUseCase.hasPendingPackets()
            val reportsRemaining = reportDao.getPendingReports().isNotEmpty()

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

        fun cancel(context: Context, reason: String = "unspecified") {
            BleMeshLog.i("UPLINK_WORK_CANCEL", "reason" to reason)
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(WORK_NAME)
        }

        fun enqueue(
            context: Context,
            initialDelayMs: Long = 0L,
            reason: String = "unspecified"
        ) {
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
