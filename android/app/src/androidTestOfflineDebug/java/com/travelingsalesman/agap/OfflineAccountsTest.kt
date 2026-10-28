package com.travelingsalesman.agap

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.network.NetworkModule
import com.travelingsalesman.agap.core.util.DeviceIdProvider
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.AgapDatabase
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.mock.OfflineTestDataSource
import com.travelingsalesman.agap.data.mock.VariantOfflineTest
import com.travelingsalesman.agap.data.remote.*
import com.travelingsalesman.agap.data.remote.dto.*
import com.travelingsalesman.agap.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OfflineAccountsTest {
    private lateinit var context: Context
    private lateinit var database: AgapDatabase
    private lateinit var tokenManager: AuthTokenManager
    private lateinit var source: OfflineTestDataSource
    private lateinit var client: OkHttpClient
    private lateinit var auth: AuthRepository
    private lateinit var storeJob: CompletableJob
    private lateinit var storeFile: File
    private lateinit var preferencePrefix: String
    private val socketAttempts = AtomicInteger()
    private val json = NetworkModule.provideJson()

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        preferencePrefix = "isolated-offline-test-${UUID.randomUUID()}-"
        context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferencePrefix + name, mode)
        }
        storeJob = SupervisorJob()
        storeFile = File(base.cacheDir, "$preferencePrefix.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(storeJob + Dispatchers.IO), produceFile = { storeFile })
        tokenManager = AuthTokenManager(store, DeviceIdProvider(context))
        source = VariantOfflineTest.create(context, json)
        database = Room.inMemoryDatabaseBuilder(base, AgapDatabase::class.java).build()
        client = NetworkModule.provideOkHttpClient(context, tokenManager, ServerConfigManager(context), source)
            .newBuilder().eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { socketAttempts.incrementAndGet() }
                override fun dnsStart(call: Call, domainName: String) { socketAttempts.incrementAndGet() }
            }).build()
        val retrofit = NetworkModule.provideRetrofit(client, json)
        auth = AuthRepositoryImpl(retrofit.create(AuthApiService::class.java), tokenManager,
            ServerConfigManager(context), UserProfileRepository(database.profileDao()), source, database)
    }

    @After fun tearDown() = runBlocking {
        assertEquals("Offline test must never resolve a host or open a socket", 0, socketAttempts.get())
        client.dispatcher.executorService.shutdown()
        database.close()
        storeJob.cancelAndJoin()
        storeFile.delete()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(preferencePrefix + "offline_test_data_v1")
        Unit
    }

    @Test fun allThreeAccountsSignInLocallyAndKeepTheirRoles() = runBlocking {
        assertEquals(3, source.accounts.size)
        for (account in source.accounts) {
            val result = auth.login(account.username, account.password, "offline-test-device")
            assertTrue(result.toString(), result is Result.Success)
            assertEquals(account.role, tokenManager.appSession.first().role)
            assertTrue(tokenManager.getAuthToken()!!.startsWith("offline-test:"))
            assertEquals(account.username, auth.getProfile().let { (it as Result.Success).data.username })
            tokenManager.clearAuthSession()
        }
        assertEquals(3, database.incidentDao().getAllIncidentsFlow().first().size)
    }

    @Test fun wrongPasswordDoesNotCreateASession() = runBlocking {
        assertTrue(auth.login("test.admin", "wrong", null) is Result.Error)
        assertNull(tokenManager.getAuthToken())
    }

    @Test fun offlineSosIsSavedAndQueuedWithoutTransmission() = runBlocking {
        auth.login("test.citizen", "offline123", null)
        val api = NetworkModule.provideRetrofit(client, json).create(IncidentReportApiService::class.java)
        val reports = IncidentReportRepository(database.reportDao(), api, BleRelayScheduler(database.relayDao()), tokenManager)
        val result = reports.submitReport(SubmitReportParams("MEDICAL", latitude = 13.1391,
            longitude = 123.7438, isOnline = false, submissionType = "QUICK_SOS"))
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.message.contains("No emergency alert was transmitted"))
        val saved = database.reportDao().getAllReportsFlow().first().single()
        assertEquals("QUEUED_OFFLINE", saved.syncState)
        assertEquals("QUICK_SOS", saved.submissionType)
        assertNotNull(database.relayDao().getNextRelayPacket())
        source.prepareDatabase(database)
        assertEquals(saved.reportUuid, database.reportDao().getAllReportsFlow().first().single().reportUuid)
    }

    @Test fun responderAssignmentsAndStatusChangesWorkLocally() = runBlocking {
        auth.login("test.responder", "offline123", null)
        val retrofit = NetworkModule.provideRetrofit(client, json)
        val responderApi = retrofit.create(ResponderApiService::class.java)
        assertEquals("TEST RESCUE 1", responderApi.getMyResponderUnit().body()!!.unitCallsign)
        val dispatch = responderApi.getMyDispatches().body()!!.single()
        val incidentApi = retrofit.create(IncidentApiService::class.java)
        assertTrue(incidentApi.updateDispatchStatus(dispatch.incidentId, dispatch.id, UpdateDispatchStatusRequestDto("ACCEPTED")).isSuccessful)
        assertEquals("ACCEPTED", responderApi.getMyDispatches().body()!!.single().status)
    }

    @Test fun profileEditsPersistInTheLocalFixtureStore() = runBlocking {
        auth.login("test.citizen", "offline123", null)
        assertTrue(auth.updateProfile("Edited Test Citizen", "edited@example.test", "00000000000", "Test information") is Result.Success)
        val reopened = VariantOfflineTest.create(context, json)
        val request = okhttp3.Request.Builder().url("https://never-contact.invalid/api/v1/users/me").build()
        val profile = reopened.respond(request, tokenManager.getAuthToken()).use {
            json.decodeFromString<UserDto>(it.body!!.string())
        }
        assertEquals("Edited Test Citizen", profile.name)
        assertEquals("Test information", profile.medicalInfo)
    }

    @Test fun adminManagementAndAdvisoriesAreStoredLocally() = runBlocking {
        auth.login("test.admin", "offline123", null)
        val updated = auth.updateUserByID(91001L, "Local Citizen Edit", null, null, null)
        assertTrue(updated is Result.Success)
        assertEquals("Local Citizen Edit", (auth.getUserById(91001L) as Result.Success).data.name)
        val retrofit = NetworkModule.provideRetrofit(client, json)
        val agencies = retrofit.create(AgencyApiService::class.java)
        assertTrue(agencies.createAgency(AgencyDto(code = "LOCAL-2", name = "Second Local Agency", type = "MDRRMO")).isSuccessful)
        assertEquals(2, agencies.listAgencies().body()!!.size)
        val alerts = retrofit.create(NotificationApiService::class.java)
        val advisory = alerts.createAlert(CreateAlertRequestDto("Local practice advisory", "Test only", "GENERAL", "INFO"))
        assertTrue(advisory.isSuccessful)
        assertEquals("Local practice advisory", advisory.body()!!.title)
        assertEquals(2, alerts.listAlerts().body()!!.alerts.size)
    }

    @Test fun offlineBuildCannotAccessInternetOrBluetooth() {
        assertTrue(BuildConfig.OFFLINE_TEST)
        assertTrue(BuildConfig.DEBUG)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        val maps = com.travelingsalesman.agap.core.map.MapPackageManager(context)
        assertTrue("Offline test APK must include usable map tiles", maps.isUsableMbtiles(maps.getDeploymentMapFile()))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN))
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE))
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    @Test fun offlineBuildDoesNotScheduleNetworkWorkers() {
        val manager = androidx.work.WorkManager.getInstance(context)
        val names = listOf("AgapEventDrivenSyncWork", "AgapPeriodicSyncWork", "AgapQueuedEmergencyUplink")
        fun scheduledIds() = names.associateWith { name ->
            manager.getWorkInfosForUniqueWork(name).get(5, java.util.concurrent.TimeUnit.SECONDS).map { it.id }.toSet()
        }
        val before = scheduledIds()
        com.travelingsalesman.agap.worker.SyncWorker.enqueueEventDrivenSync(context)
        com.travelingsalesman.agap.worker.SyncWorker.schedulePeriodicSync(context)
        com.travelingsalesman.agap.worker.UplinkFlushWorker.enqueue(context, reason = "offline_regression")
        assertEquals(before, scheduledIds())
    }
}
