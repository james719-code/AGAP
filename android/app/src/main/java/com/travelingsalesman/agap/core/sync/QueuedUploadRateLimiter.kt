package com.travelingsalesman.agap.core.sync

import android.content.Context
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide and restart-safe throttle for queued emergency HTTP uploads.
 *
 * This class belongs to the INTERNET path only. BLE GATT relaying must never call
 * this limiter.
 *
 * Every gateway packet/report upload attempt goes through this gate. The mutex
 * prevents two in-process callers from overlapping, while SharedPreferences
 * preserves the timestamp if Android recreates the app or WorkManager process.
 */
@Singleton
class QueuedUploadRateLimiter @Inject constructor(
    @ApplicationContext context: Context
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun <T> runWithSpacing(
        label: String,
        block: suspend () -> T
    ): T = mutex.withLock {
        val now = System.currentTimeMillis()
        val lastAttemptMs = prefs.getLong(KEY_LAST_ATTEMPT_MS, 0L)
        val remainingMs = (lastAttemptMs + ThrottleConfig.INTERNET_UPLOAD_INTERVAL_MS - now)
            .coerceAtLeast(0L)

        if (remainingMs > 0L) {
            BleMeshLog.d(
                "UPLINK_RATE_LIMIT_WAIT",
                "label" to label,
                "delayMs" to remainingMs
            )
            delay(remainingMs)
        }

        try {
            block()
        } finally {
            // INTERNET ONLY: start the next 30-second window after this HTTP attempt finishes.
            prefs.edit()
                .putLong(KEY_LAST_ATTEMPT_MS, System.currentTimeMillis())
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "agap_queued_uplink_throttle"
        private const val KEY_LAST_ATTEMPT_MS = "last_attempt_ms"
    }
}
