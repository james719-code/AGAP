package com.travelingsalesman.agap.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosCountdownTest {

    @Test
    fun `nothing is due before the five second window ends`() {
        val countdown = SosCountdown()
        countdown.start("MEDICAL", nowMs = 1_000L)

        assertNull(countdown.dueCode(nowMs = 1_000L))
        assertNull(countdown.dueCode(nowMs = 5_999L))
        assertTrue(countdown.isPending)
    }

    @Test
    fun `the SOS becomes due exactly once at the deadline`() {
        val countdown = SosCountdown()
        countdown.start("FIRE", nowMs = 0L)

        assertEquals("FIRE", countdown.dueCode(nowMs = 5_000L))
        assertNull(countdown.dueCode(nowMs = 5_100L))
        assertFalse(countdown.isPending)
    }

    @Test
    fun `cancelling means the SOS is never sent`() {
        val countdown = SosCountdown()
        countdown.start("CRIME", nowMs = 0L)

        assertEquals("CRIME", countdown.cancel())
        assertNull(countdown.dueCode(nowMs = 10_000L))
        assertNull(countdown.sendNow())
    }

    @Test
    fun `send now skips the wait and cannot fire a second time`() {
        val countdown = SosCountdown()
        countdown.start("ACCIDENT", nowMs = 0L)

        assertEquals("ACCIDENT", countdown.sendNow())
        assertNull(countdown.dueCode(nowMs = 10_000L))
    }

    @Test
    fun `a second release while counting down does not restart the timer`() {
        val countdown = SosCountdown()
        assertTrue(countdown.start("MEDICAL", nowMs = 0L))
        assertFalse(countdown.start("FIRE", nowMs = 3_000L))

        assertEquals("MEDICAL", countdown.dueCode(nowMs = 5_000L))
    }

    @Test
    fun `seconds count down from five to one`() {
        val countdown = SosCountdown()
        countdown.start("MEDICAL", nowMs = 0L)

        assertEquals(5, countdown.secondsRemaining(nowMs = 0L))
        assertEquals(5, countdown.secondsRemaining(nowMs = 1L))
        assertEquals(4, countdown.secondsRemaining(nowMs = 1_000L))
        assertEquals(1, countdown.secondsRemaining(nowMs = 4_999L))
        assertEquals(1, countdown.secondsRemaining(nowMs = 5_000L))
    }

    @Test
    fun `progress runs from zero to one`() {
        val countdown = SosCountdown()
        countdown.start("MEDICAL", nowMs = 0L)

        assertEquals(0f, countdown.progress(nowMs = 0L), 0.001f)
        assertEquals(0.5f, countdown.progress(nowMs = 2_500L), 0.001f)
        assertEquals(1f, countdown.progress(nowMs = 9_000L), 0.001f)
    }
}
