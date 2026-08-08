package com.travelingsalesman.agap

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration.Provider
import com.travelingsalesman.agap.core.network.ConnectivityObserver
import com.travelingsalesman.agap.core.network.NetworkConnectivityObserver
import com.travelingsalesman.agap.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
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
    lateinit var assetCatalogImporter: com.travelingsalesman.agap.data.local.AssetCatalogImporter

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Configure OsmDroid Tile Cache & User Agent for offline map persistence
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = BuildConfig.APPLICATION_ID

        val basePath = File(cacheDir, "osmdroid")
        val tileCachePath = File(basePath, "tiles")
        if (!tileCachePath.exists()) {
            tileCachePath.mkdirs()
        }
        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = tileCachePath

        // Allocate 100MB tile disk cache capacity
        osmConfig.tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        osmConfig.tileFileSystemCacheTrimBytes = 80L * 1024 * 1024

        val prefs = getSharedPreferences("osmdroid_cache_prefs", Context.MODE_PRIVATE)
        osmConfig.load(this, prefs)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        applicationScope.launch(Dispatchers.IO) {
            assetCatalogImporter.importAssetsIfNecessary()
        }

        createNotificationChannel()
        observeNetworkForSync()
    }

    private fun observeNetworkForSync() {
        applicationScope.launch {
            connectivityObserver.observe().collectLatest { status ->
                if (status == ConnectivityObserver.Status.Available) {
                    Timber.i("Network available: Triggering event-driven SyncWorker")
                    SyncWorker.enqueueEventDrivenSync(this@AgapApplication)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Emergency SOS Alerts"
            val descriptionText = "Notifications for incoming distress SOS emergency alerts"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("agap_sos_channel", name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}