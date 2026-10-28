package com.travelingsalesman.agap.core.bluetooth

import java.util.concurrent.atomic.AtomicBoolean

/** One native start request, including the interval before its success callback. */
internal class RadioStartGate {
    private val requested = AtomicBoolean(false)
    fun tryStart(): Boolean = requested.compareAndSet(false, true)
    fun markRunning() { requested.set(true) }
    fun stopped() { requested.set(false) }
}

/** Keeps queue ownership through durable ACK bookkeeping, including service restarts. */
internal class RelayTransferGate {
    class Lease internal constructor(val attemptId: Long) {
        val trace = RelayTransferTrace()
    }
    private var nextId = 0L
    private var current: Lease? = null
    private var persisting = false

    @Synchronized fun tryAcquire(): Lease? {
        if (current != null) return null
        return Lease(++nextId).also { current = it }
    }

    @Synchronized fun isCurrent(lease: Lease): Boolean = current === lease

    @Synchronized fun beginPersistence(lease: Lease): Boolean {
        if (current !== lease || persisting) return false
        persisting = true
        return true
    }

    @Synchronized fun release(lease: Lease) {
        if (current !== lease) return
        current = null
        persisting = false
    }

    @Synchronized fun cancelSending() {
        // A remote ACK is not finished until its delivery event and queue update commit.
        if (!persisting) current = null
    }
}

internal fun isRelayPeerEligible(peerId: String, excludedPeerIds: Set<String>, cooldownUntilMs: Long, nowMs: Long): Boolean =
    peerId !in excludedPeerIds && cooldownUntilMs <= nowMs

internal fun needsGattMtuExchange(payloadBytes: Int): Boolean = payloadBytes > 20

internal class RelayTransferTrace(private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    data class Timing(val stage: String, val previousStage: String, val elapsedMs: Long, val previousStageMs: Long)
    private val startedAt = clockMs()
    private var stage = "SELECTING"
    private var stageStartedAt = startedAt

    @Synchronized fun advance(nextStage: String): Timing {
        val now = clockMs()
        val timing = Timing(nextStage, stage, (now - startedAt).coerceAtLeast(0), (now - stageStartedAt).coerceAtLeast(0))
        stage = nextStage
        stageStartedAt = now
        return timing
    }
}
