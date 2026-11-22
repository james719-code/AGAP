package com.travelingsalesman.agap.ui.screens.home

import kotlin.math.ceil

/**
 * Cancel window between releasing the SOS control and actually sending.
 *
 * Modelled on Android Emergency SOS, which gives about five seconds to cancel
 * an accidental trigger. Pure and clock-driven so it can be unit-tested without
 * coroutines: the caller supplies the current time on every call.
 */
class SosCountdown(val durationMs: Long = DEFAULT_DURATION_MS) {

    private var pendingCode: String? = null
    private var deadlineMs: Long = 0L

    val isPending: Boolean get() = pendingCode != null

    /** Starts a countdown. Ignored while one is already running (no restarts by double release). */
    fun start(emergencyCode: String, nowMs: Long): Boolean {
        if (pendingCode != null) return false
        pendingCode = emergencyCode
        deadlineMs = nowMs + durationMs
        return true
    }

    /** Cancels the pending SOS. Returns the code that will no longer be sent, if any. */
    fun cancel(): String? = pendingCode.also { pendingCode = null }

    /** Skips the remaining wait. Returns the code to send now, or null if nothing was pending. */
    fun sendNow(): String? = pendingCode.also { pendingCode = null }

    /**
     * Returns the code to send once the deadline has passed (exactly once),
     * otherwise null.
     */
    fun dueCode(nowMs: Long): String? {
        val code = pendingCode ?: return null
        if (nowMs < deadlineMs) return null
        pendingCode = null
        return code
    }

    /** Whole seconds left, counting 5,4,3,2,1 — never 0 while still pending. */
    fun secondsRemaining(nowMs: Long): Int {
        if (pendingCode == null) return 0
        val remaining = (deadlineMs - nowMs).coerceAtLeast(0L)
        return ceil(remaining / 1000.0).toInt().coerceAtLeast(1)
    }

    /** Elapsed fraction 0..1 for the progress ring. */
    fun progress(nowMs: Long): Float {
        if (pendingCode == null) return 0f
        val elapsed = durationMs - (deadlineMs - nowMs)
        return (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_DURATION_MS = 5_000L
    }
}

/** What the countdown overlay renders. */
data class SosCountdownUiState(
    val emergencyCode: String,
    val emergencyLabel: String,
    val secondsRemaining: Int,
    val progress: Float
)
