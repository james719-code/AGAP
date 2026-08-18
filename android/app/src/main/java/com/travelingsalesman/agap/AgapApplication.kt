package com.travelingsalesman.agap

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration.Provider
import com.travelingsalesman.agap.core.network.ConnectivityObserver
import com.travelingsalesman.agap.core.network.NetworkConnectivityObserver
import com.travelingsalesman.agap.worker.SyncWorker
import com.travelingsalesman.agap.worker.UplinkFlushWorker
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import timber.log.Timber
import java.io.File
import dagger.Lazy
import javax.inject.Inject

@HiltAndroidApp
class AgapApplication : Application(), Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var connectivityObserver: Lazy<NetworkConnectivityObserver>

    @Inject
    lateinit var telemetryRepository: Lazy<TelemetryRepository>

    @Inject
    lateinit var reportDao: Lazy<ReportDao>

    @Inject
    lateinit var serverConfigManager: Lazy<ServerConfigManager>

    @Inject
    lateinit var authTokenManager: Lazy<AuthTokenManager>

    @Inject
    lateinit var assetCatalogImporter:
            Lazy<com.travelingsalesman.agap.data.local.AssetCatalogImporter>

    private val applicationExceptionHandler =
        CoroutineExceptionHandler { _, error ->
            Timber.e(error, "Uncaught application background task failure")
        }

    /**
     * Long-lived app work must be supervised so one failed catalog import, sync
     * observer, or cache-maintenance task cannot silently cancel all other jobs.
     */
    private val applicationScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Default +
                    applicationExceptionHandler
        )

    override val workManagerConfiguration:
            androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        configurePermanentOsmdroidCache()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        applicationScope.launch(Dispatchers.IO) {
            // Give the first frame / navigation startup path priority over catalog IO.
            delay(NON_CRITICAL_STARTUP_DELAY_MS)
            assetCatalogImporter.get().importAssetsIfNecessary()
        }

        createNotificationChannels()
        observeNetworkForSync()
        observeQueuedUplinkTriggers()

        applicationScope.launch(Dispatchers.IO) {
            delay(NON_CRITICAL_STARTUP_DELAY_MS)
            SyncWorker.schedulePeriodicSync(this@AgapApplication)
        }
    }

    /**
     * AGAP map strategy:
     *
     * 1. Store map tiles under filesDir, NOT cacheDir.
     *    Android may clear cacheDir automatically; filesDir is retained until
     *    app data is explicitly cleared or the application is uninstalled.
     *
     * 2. Give downloaded tiles a very long expiration period.
     *    This makes osmdroid use an existing tile instead of repeatedly
     *    replacing it from the network.
     *
     * 3. Keep enough disk-cache capacity for a deployment-area map.
     *
     * 4. Migrate any useful tiles from the previous cacheDir/osmdroid location
     *    the first time this version runs.
     */
    private fun configurePermanentOsmdroidCache() {
        /*
         * Do not delete expired tiles at osmdroid startup.
         * Existing AGAP tiles are treated as deployment assets once downloaded.
         */
        SqlTileWriter.setCleanupOnStart(false)

        val osmConfig = Configuration.getInstance()
        val prefs = getSharedPreferences(
            OSM_PREFS_NAME,
            Context.MODE_PRIVATE
        )

        // Let osmdroid initialize its normal settings first.
        // We override the storage paths immediately afterward.
        osmConfig.load(this, prefs)

        val permanentBasePath =
            File(filesDir, "osmdroid_permanent")

        val permanentTilePath =
            File(permanentBasePath, "tiles")

        migrateLegacyMapCacheIfNecessary(
            permanentBasePath = permanentBasePath
        )

        if (!permanentTilePath.exists()) {
            permanentTilePath.mkdirs()
        }

        osmConfig.userAgentValue =
            BuildConfig.APPLICATION_ID

        osmConfig.osmdroidBasePath =
            permanentBasePath

        osmConfig.osmdroidTileCache =
            permanentTilePath

        /*
         * If this installation already has cached tiles from an older build,
         * pin their expiration timestamps forward too. Otherwise old expiry
         * values could make osmdroid refresh them once even after we switch to
         * the new 50-year policy.
         */
        // Updating a potentially large osmdroid SQLite cache can be expensive.
        // Keep it off the main thread; the expiration override below already
        // protects newly downloaded tiles immediately.
        applicationScope.launch(Dispatchers.IO) {
            delay(MAP_CACHE_MAINTENANCE_DELAY_MS)
            pinExistingTileExpirations(
                permanentBasePath = permanentBasePath,
                permanentTilePath = permanentTilePath
            )
        }

        /*
         * Keep already-downloaded tiles effectively permanent for this app.
         *
         * 50 years is intentionally very long, but does not risk Long overflow
         * when osmdroid computes "now + duration".
         *
         * If the user clears app data or uninstalls AGAP, Android will still
         * remove these files, which is expected.
         */
        osmConfig.setExpirationOverrideDuration(
            FIFTY_YEARS_MILLIS
        )

        /*
         * The old project limited tiles to 100 MB. A deployment map can easily
         * exceed that, causing old tiles to be trimmed and later downloaded again.
         *
         * 1 GB gives the deployment area plenty of room while still maintaining
         * a hard upper bound.
         */
        osmConfig.tileFileSystemCacheMaxBytes =
            1024L * 1024L * 1024L

        osmConfig.tileFileSystemCacheTrimBytes =
            950L * 1024L * 1024L

        // Persist our custom path/settings so future launches start consistently.
        osmConfig.save(this, prefs)
    }

    private fun migrateLegacyMapCacheIfNecessary(
        permanentBasePath: File
    ) {
        val migrationMarker =
            File(permanentBasePath, MAP_CACHE_MIGRATION_MARKER)

        // Avoid recursively scanning a potentially very large tile directory on
        // every cold start. Migration is a one-time compatibility task.
        if (migrationMarker.exists()) {
            return
        }

        val legacyBasePath =
            File(cacheDir, "osmdroid")

        runCatching {
            permanentBasePath.mkdirs()
            if (legacyBasePath.exists()) {
                legacyBasePath.copyRecursively(
                    target = permanentBasePath,
                    overwrite = false
                )
            }
            migrationMarker.writeText("done")
        }.onFailure { error ->
            Timber.w(
                error,
                "Could not migrate legacy osmdroid cache"
            )
        }
    }

    private fun pinExistingTileExpirations(
        permanentBasePath: File,
        permanentTilePath: File
    ) {
        val pinMarker =
            File(permanentBasePath, MAP_CACHE_EXPIRY_MARKER)
        if (pinMarker.exists()) {
            return
        }

        val databaseFile =
            File(
                permanentTilePath,
                SqlTileWriter.DATABASE_FILENAME
            )

        if (!databaseFile.exists()) {
            // No legacy rows to repair. Future rows inherit the configured
            // long expiration duration.
            runCatching { pinMarker.writeText("done") }
            return
        }

        runCatching {
            val database =
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )

            database.use { db ->
                val farFuture =
                    System.currentTimeMillis() +
                            FIFTY_YEARS_MILLIS

                /*
                 * osmdroid's SqlTileWriter table is "tiles" and uses an
                 * "expires" column. Extend old rows so they are reused.
                 */
                db.execSQL(
                    """
                    UPDATE tiles
                    SET expires = ?
                    WHERE expires IS NULL
                       OR expires < ?
                    """.trimIndent(),
                    arrayOf(
                        farFuture,
                        farFuture
                    )
                )
            }
            pinMarker.writeText("done")
        }.onFailure { error ->
            Timber.w(
                error,
                "Could not pin existing map tile expirations"
            )
        }
    }

    private fun observeNetworkForSync() {
        applicationScope.launch {
            connectivityObserver.get().observe()
                .collectLatest { status ->
                    if (
                        status ==
                        ConnectivityObserver.Status.Available
                    ) {
                        Timber.i(
                            "Network available: " +
                                    "Triggering event-driven SyncWorker"
                        )

                        UplinkFlushWorker.enqueue(
                            context = this@AgapApplication,
                            reason = "internet_available"
                        )

                        SyncWorker.enqueueEventDrivenSync(
                            this@AgapApplication
                        )
                    }
                }
        }
    }

    /**
     * Triggers the paced emergency uplink lane for all cases that do not produce
     * a connectivity transition: a BLE packet arriving while this phone is
     * already online, a locally queued report, returning from BLE-only mode, or
     * signing in again after queued data was preserved across logout.
     */
    private fun observeQueuedUplinkTriggers() {
        applicationScope.launch {
            telemetryRepository.get().getPendingCount()
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .collectLatest { hasPending ->
                    if (hasPending) {
                        UplinkFlushWorker.enqueue(
                            context = this@AgapApplication,
                            reason = "ble_gateway_packet_queued"
                        )
                    }
                }
        }

        applicationScope.launch {
            reportDao.get().observePendingReportCount()
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .collectLatest { hasPending ->
                    if (hasPending) {
                        UplinkFlushWorker.enqueue(
                            context = this@AgapApplication,
                            reason = "local_report_queued"
                        )
                    }
                }
        }

        applicationScope.launch {
            serverConfigManager.get().transmissionMode
                .distinctUntilChanged()
                .collectLatest { mode ->
                    if (mode == TransmissionMode.FORCE_BLE_MESH) {
                        UplinkFlushWorker.cancel(
                            context = this@AgapApplication,
                            reason = "ble_only_enabled"
                        )
                    } else {
                        UplinkFlushWorker.enqueue(
                            context = this@AgapApplication,
                            reason = "transport_mode_${mode.name.lowercase()}"
                        )
                    }
                }
        }

        applicationScope.launch {
            authTokenManager.get().authToken
                .map { token -> !token.isNullOrBlank() }
                .distinctUntilChanged()
                .collectLatest { loggedIn ->
                    if (loggedIn) {
                        UplinkFlushWorker.enqueue(
                            context = this@AgapApplication,
                            reason = "login_completed"
                        )
                    }
                }
        }
    }

    private fun createNotificationChannels() {
        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            val notificationManager: android.app.NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            // SOS Channel
            val sosName = "Emergency SOS Alerts"
            val sosDescription = "Notifications for incoming distress SOS emergency alerts"
            val sosChannel = android.app.NotificationChannel(
                "agap_sos_channel",
                sosName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = sosDescription
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(sosChannel)

            // Admin Alerts Channel
            val alertName = "Official Broadcast Alerts"
            val alertDescription = "High-priority announcements from emergency management authorities"
            val alertChannel = android.app.NotificationChannel(
                "agap_admin_alerts",
                alertName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = alertDescription
                enableVibration(true)
                // Default sound is typically used, but IMPORTANCE_HIGH ensures it's audible.
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        private const val OSM_PREFS_NAME =
            "osmdroid_cache_prefs"

        private const val FIFTY_YEARS_MILLIS =
            50L * 365L * 24L * 60L * 60L * 1000L

        private const val NON_CRITICAL_STARTUP_DELAY_MS = 750L
        private const val MAP_CACHE_MAINTENANCE_DELAY_MS = 3_000L
        private const val MAP_CACHE_MIGRATION_MARKER = ".legacy_cache_migrated_v1"
        private const val MAP_CACHE_EXPIRY_MARKER = ".expiry_rows_pinned_v1"
    }
}