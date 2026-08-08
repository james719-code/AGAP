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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class AgapApplication : Application(), Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var connectivityObserver: NetworkConnectivityObserver

    @Inject
    lateinit var telemetryRepository: TelemetryRepository

    @Inject
    lateinit var reportDao: ReportDao

    @Inject
    lateinit var serverConfigManager: ServerConfigManager

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    @Inject
    lateinit var assetCatalogImporter:
            com.travelingsalesman.agap.data.local.AssetCatalogImporter

    private val applicationScope =
        CoroutineScope(Dispatchers.Default)

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
            assetCatalogImporter.importAssetsIfNecessary()
        }

        createNotificationChannel()
        observeNetworkForSync()
        observeQueuedUplinkTriggers()
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
        pinExistingTileExpirations(
            permanentTilePath = permanentTilePath
        )

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
        if (
            permanentBasePath.exists() &&
            permanentBasePath.walkTopDown()
                .any { it.isFile && it.length() > 0L }
        ) {
            return
        }

        val legacyBasePath =
            File(cacheDir, "osmdroid")

        if (!legacyBasePath.exists()) {
            return
        }

        runCatching {
            legacyBasePath.copyRecursively(
                target = permanentBasePath,
                overwrite = false
            )
        }.onFailure { error ->
            Timber.w(
                error,
                "Could not migrate legacy osmdroid cache"
            )
        }
    }

    private fun pinExistingTileExpirations(
        permanentTilePath: File
    ) {
        val databaseFile =
            File(
                permanentTilePath,
                SqlTileWriter.DATABASE_FILENAME
            )

        if (!databaseFile.exists()) {
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
        }.onFailure { error ->
            Timber.w(
                error,
                "Could not pin existing map tile expirations"
            )
        }
    }

    private fun observeNetworkForSync() {
        applicationScope.launch {
            connectivityObserver.observe()
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
            telemetryRepository.getPendingCount()
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
            reportDao.observePendingReportCount()
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
            serverConfigManager.transmissionMode
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
            authTokenManager.authToken
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

    private fun createNotificationChannel() {
        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            val name =
                "Emergency SOS Alerts"

            val descriptionText =
                "Notifications for incoming " +
                        "distress SOS emergency alerts"

            val importance =
                android.app.NotificationManager
                    .IMPORTANCE_HIGH

            val channel =
                android.app.NotificationChannel(
                    "agap_sos_channel",
                    name,
                    importance
                ).apply {
                    description = descriptionText
                    enableVibration(true)
                }

            val notificationManager:
                    android.app.NotificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as android.app.NotificationManager

            notificationManager
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val OSM_PREFS_NAME =
            "osmdroid_cache_prefs"

        private const val FIFTY_YEARS_MILLIS =
            50L * 365L * 24L * 60L * 60L * 1000L
    }
}