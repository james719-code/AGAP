package com.travelingsalesman.agap.core.sync

object ThrottleConfig {
    /**
     * Max random delay in ms applied before flushing queued packets on reconnect.
     * Set to 60s for large-scale disaster deployment (hundreds of devices reconnecting at once).
     */
    const val MAX_RECONNECT_JITTER_MS = 60_000L

    /**
     * Minimum delay in ms between sending consecutive packets from the same device.
     */
    const val INTER_PACKET_DELAY_MS = 500L

    /**
     * Initial base delay in ms for exponential backoff on retryable network errors.
     */
    const val RETRY_BASE_DELAY_MS = 1_000L

    /**
     * Maximum retry attempts per packet during a single flush run.
     */
    const val MAX_RETRY_ATTEMPTS = 3

    /**
     * Maximum number of packets to process in one flush cycle.
     */
    const val BATCH_SIZE = 15
}
