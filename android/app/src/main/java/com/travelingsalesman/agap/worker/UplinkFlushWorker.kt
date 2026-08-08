package com.travelingsalesman.agap.worker

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.travelingsalesman.agap.domain.usecase.FlushPendingPacketsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UplinkFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flushPendingPacketsUseCase: FlushPendingPacketsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        BleMeshLog.d("UPLINK_WORKER_STARTED")
        return try {
            val flushedCount = flushPendingPacketsUseCase()
            if (flushedCount >= 0) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            BleMeshLog.e("UPLINK_WORKER_FAILED", error = e)
            Result.failure()
        }
    }
}
