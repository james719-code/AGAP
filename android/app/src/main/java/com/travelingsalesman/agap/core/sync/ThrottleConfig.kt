package com.travelingsalesman.agap.core.sync

object ThrottleConfig {
    /**
     * Optional random delay before a reconnect flush. Emergency queue draining no
     * longer uses this by default; the fixed inter-upload delay is the primary
     * protection against reconnect storms.
     */
    const val MAX_RECONNECT_JITTER_MS = 60_000L

    /**
     * Required gap between consecutive INTERNET/HTTP emergency uploads.
     *
     * IMPORTANT: this constant must never be used by BLE scanning, peer discovery,
     * GATT connection scheduling, or GATT relay retries. A packet received through
     * BLE may later be uploaded by a gateway phone, but the 30-second limit applies
     * only to that HTTP upload step.
     */
    const val INTERNET_UPLOAD_INTERVAL_MS = 30_000L

    /**
     * Initial base delay in ms for exponential backoff on retryable network errors.
     */
    const val RETRY_BASE_DELAY_MS = 1_000L

    /**
     * Maximum retry attempts per packet during a single flush run.
     */
    const val MAX_RETRY_ATTEMPTS = 3

    /**
     * Maximum emergency queue items handled in one WorkManager run. Ten items at
     * a 30-second gap stays comfortably below WorkManager's normal execution cap.
     */
    const val BATCH_SIZE = 10
}
