package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RelayTransferControlTest {
    @Test fun simultaneousStartsReserveOneRadioOperationUntilStopped() {
        val gate = RadioStartGate()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        try {
            val requests = (1..32).map { executor.submit { start.await(); if (gate.tryStart()) winners.incrementAndGet() } }
            start.countDown()
            requests.forEach { it.get(3, TimeUnit.SECONDS) }
            assertEquals(1, winners.get())
            gate.markRunning()
            assertFalse(gate.tryStart())
            gate.stopped()
            assertTrue(gate.tryStart())
        } finally { executor.shutdownNow() }
    }

    @Test fun acknowledgedPacketCannotBeSentAgainWhileDatabaseWriteIsPending() {
        val gate = RelayTransferGate()
        val sending = gate.tryAcquire()!!
        assertTrue(gate.beginPersistence(sending))
        assertNull(gate.tryAcquire())
        // Service stop/resume must not reopen the queue before persistence finishes.
        gate.cancelSending()
        assertNull(gate.tryAcquire())
        gate.release(sending)
        assertNotNull(gate.tryAcquire())
    }

    @Test fun lateCompletionCannotReleaseANewerAttemptForTheSameQueueEntry() {
        val gate = RelayTransferGate()
        val old = gate.tryAcquire()!!
        gate.cancelSending()
        val replacement = gate.tryAcquire()!!
        assertFalse(gate.beginPersistence(old))
        gate.release(old)
        assertTrue(gate.isCurrent(replacement))
        assertNull(gate.tryAcquire())
        gate.release(replacement)
        assertNotNull(gate.tryAcquire())
    }

    @Test fun duplicateAcknowledgementCannotStartASecondDatabaseCommit() {
        val gate = RelayTransferGate()
        val lease = gate.tryAcquire()!!
        assertTrue(gate.beginPersistence(lease))
        assertFalse(gate.beginPersistence(lease))
    }

    @Test fun announcementsExcludeSourceAndEveryPreviouslyDeliveredPeer() {
        val excluded = setOf("origin", "delivered-a", "delivered-b")
        assertFalse(isRelayPeerEligible("origin", excluded, 0, 1000))
        assertFalse(isRelayPeerEligible("delivered-a", excluded, 0, 1000))
        assertFalse(isRelayPeerEligible("delivered-b", excluded, 0, 1000))
        assertTrue(isRelayPeerEligible("new-peer", excluded, 0, 1000))
        assertFalse(isRelayPeerEligible("new-peer", excluded, 1001, 1000))
        assertTrue(isRelayPeerEligible("new-peer", excluded, 1000, 1000))
    }

    @Test fun compactResponsesSkipMtuExchangeButLargerFramesKeepIt() {
        assertFalse(needsGattMtuExchange(BleMeshPacketCodec.RESPONSE_PACKET_LENGTH))
        assertFalse(needsGattMtuExchange(20))
        assertTrue(needsGattMtuExchange(BleMeshPacketCodec.EMERGENCY_PACKET_LENGTH))
        assertTrue(needsGattMtuExchange(54))
    }

    @Test fun traceReportsEachStageAndTotalTimeUsingAMonotonicClock() {
        var time = 1000L
        val trace = RelayTransferTrace { time }
        time += 30
        assertEquals(RelayTransferTrace.Timing("CONNECTING", "SELECTING", 30, 30), trace.advance("CONNECTING"))
        time += 120
        assertEquals(RelayTransferTrace.Timing("DISCOVERING", "CONNECTING", 150, 120), trace.advance("DISCOVERING"))
        time += 40
        assertEquals(190L, trace.advance("ACKNOWLEDGED").elapsedMs)
    }
}
