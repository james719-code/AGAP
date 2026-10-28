package com.travelingsalesman.agap.core.sync

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticatedWorkTest {
    @Test fun signedOutStartupDoesNotCreateObservers() = runBlocking {
        var created = 0
        collectAuthenticatedWork(flowOf(false, false)) { created++ }
        assertEquals(0, created)
    }

    @Test fun loginStartsOnceAndLogoutDisposesObserversBeforeNextSession() = runBlocking {
        val sessions = MutableSharedFlow<Boolean>()
        val events = Channel<String>(Channel.UNLIMITED)
        var active = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collectAuthenticatedWork(sessions) {
                launch {
                    active++
                    events.send("started")
                    try { awaitCancellation() }
                    finally { active--; events.trySend("stopped") }
                }
            }
        }
        try {
            withTimeout(3_000) { sessions.subscriptionCount.first { it > 0 } }
            sessions.emit(false)
            sessions.emit(true)
            assertEquals("started", withTimeout(3_000) { events.receive() })
            sessions.emit(true)
            sessions.emit(false)
            assertEquals("stopped", withTimeout(3_000) { events.receive() })
            assertEquals(0, active)
            sessions.emit(true)
            assertEquals("started", withTimeout(3_000) { events.receive() })
            assertEquals(1, active)
        } finally { job.cancelAndJoin() }
        assertEquals("stopped", withTimeout(3_000) { events.receive() })
        assertEquals(0, active)
    }

    @Test fun oneFailedObserverDoesNotStopTheOtherSessionObservers() = runBlocking {
        val sessions = MutableSharedFlow<Boolean>()
        val events = Channel<String>(Channel.UNLIMITED)
        val handler = CoroutineExceptionHandler { _, _ -> events.trySend("failure handled") }
        val job = launch(handler, start = CoroutineStart.UNDISPATCHED) {
            collectAuthenticatedWork(sessions) {
                launch { throw IllegalStateException("simulated observer failure") }
                launch {
                    events.send("sibling active")
                    try { awaitCancellation() }
                    finally { events.trySend("sibling stopped") }
                }
            }
        }
        try {
            withTimeout(3_000) { sessions.subscriptionCount.first { it > 0 } }
            sessions.emit(true)
            val initial = withTimeout(3_000) { setOf(events.receive(), events.receive()) }
            assertEquals(setOf("failure handled", "sibling active"), initial)
            sessions.emit(false)
            assertEquals("sibling stopped", withTimeout(3_000) { events.receive() })
        } finally { job.cancelAndJoin() }
    }
}
