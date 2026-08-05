package com.travelingsalesman.agap

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class AgapApplication : Application() {
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
    }
}