package com.travelingsalesman.agap.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.travelingsalesman.agap.core.util.DeviceIdProvider
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.remote.dto.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import java.io.File
import java.util.UUID
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import com.travelingsalesman.agap.core.network.AgapWebSocketClient
import com.travelingsalesman.agap.data.local.ServerConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class GuestSessionTest {
    // A unique store prevents the application's singleton delegate from sharing account state.
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var storeFile: File
    private lateinit var storeJob: kotlinx.coroutines.CompletableJob
    private lateinit var manager: AuthTokenManager
    private lateinit var reopened: AuthTokenManager

    @Before fun createIsolatedStore() {
        storeFile = File(context.cacheDir, "guest-session-${UUID.randomUUID()}.preferences_pb")
        storeJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(storeJob + Dispatchers.IO),
            produceFile = { storeFile }
        )
        manager = AuthTokenManager(store, DeviceIdProvider(context))
        reopened = AuthTokenManager(store, DeviceIdProvider(context))
    }

    @After fun removeIsolatedStore() = runBlocking {
        storeJob.cancelAndJoin()
        storeFile.delete()
        Unit
    }

    @Test fun guestChoicePersistsWithoutCreatingAnIdentityAndCanBeExited() = runBlocking {
        manager.clearAuthSession()
        manager.setDarkMode(false)
        manager.enterGuestMode()
        assertTrue(reopened.appSession.first().isGuest)
        assertFalse(reopened.appSession.first().isAuthenticated)
        assertNull(reopened.getAuthToken())
        assertNull(reopened.userId.first())
        assertNull(reopened.userRole.first())
        reopened.leaveGuestMode()
        assertFalse(manager.appSession.first().isGuest)
        assertFalse(manager.isDarkMode.first())
    }

    @Test fun authenticatedSessionReplacesGuestAndKeepsItsRealRole() = runBlocking {
        manager.clearAuthSession()
        manager.enterGuestMode()
        manager.saveAuthSession("test-only-token", UserDto(42, "test-responder", "RESPONDER"))
        manager.enterGuestMode()
        val session = manager.appSession.first()
        assertTrue(session.isAuthenticated)
        assertFalse(session.isGuest)
        assertEquals("RESPONDER", session.role)
        manager.clearAuthSession()
        assertFalse(manager.appSession.first().isGuest)
    }

    @Test fun guestDoesNotConnectRealtimeBeforeSignIn() = runBlocking {
        org.junit.Assume.assumeFalse(com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST)
        manager.enterGuestMode()
        val attempts = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor {
            attempts.incrementAndGet()
            // Stop before DNS or a real connection, even after the synthetic sign-in.
            throw IOException("Intercepted test connection")
        }.build()
        val client = AgapWebSocketClient(http, Json, ServerConfigManager(context), manager)
        val collector = launch { runCatching { client.connect().collect {} } }
        try {
            delay(300)
            assertEquals(0, attempts.get())
            manager.saveAuthSession("test-only-token", UserDto(42, "test-citizen", "CITIZEN"))
            withTimeout(3_000) { while (attempts.get() == 0) delay(10) }
            assertEquals(1, attempts.get())
        } finally {
            collector.cancelAndJoin()
            http.dispatcher.executorService.shutdown()
        }
    }
}
