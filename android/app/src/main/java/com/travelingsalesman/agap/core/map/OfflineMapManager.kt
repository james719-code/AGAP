package com.travelingsalesman.agap.core.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AGAP map policy.
 *
 * Priority:
 *
 * 1. If a REAL deployment.mbtiles exists and contains tiles:
 *      use it permanently and never contact the tile network.
 *
 * 2. Otherwise use osmdroid's persistent filesystem/SQLite cache:
 *      - existing tile -> render cached tile
 *      - missing tile + internet -> download it once and save it
 *      - missing tile + offline -> leave that specific tile unavailable
 *
 * Existing downloaded tiles receive a 50-year expiration in AgapApplication,
 * so normal map refreshes do not keep replacing them.
 */
@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val mapPackageManager:
    MapPackageManager
) {

    fun configureOfflineMapView(
        mapView: MapView,
        isOnline: Boolean
    ) {
        configureMapView(
            mapView = mapView,
            networkAvailable = isOnline
        )
    }

    fun configureMapView(
        mapView: MapView,
        networkAvailable: Boolean
    ) {
        val mbtilesFile =
            mapPackageManager
                .getDeploymentMapFile()

        if (
            mapPackageManager
                .isUsableMbtiles(
                    mbtilesFile
                )
        ) {
            configureRealMbtiles(
                mapView = mapView,
                mbtilesFile = mbtilesFile!!,
                networkAvailable = networkAvailable
            )

            return
        }

        /*
         * No full MBTiles archive is installed.
         *
         * Keep the standard MAPNIK provider so osmdroid can read its persistent
         * local tile cache. Network access is enabled only when Android says the
         * device is online; that lets osmdroid fetch ONLY tiles that are missing.
         *
         * Because cached tiles have a 50-year expiry, already-cached tiles should
         * be reused instead of replaced during normal operation.
         */
        mapView.setTileSource(
            TileSourceFactory.MAPNIK
        )

        mapView.setUseDataConnection(
            networkAvailable
        )

        mapView.invalidate()
    }

    private fun configureRealMbtiles(
        mapView: MapView,
        mbtilesFile: java.io.File,
        networkAvailable: Boolean
    ) {
        try {
            val registerReceiver =
                SimpleRegisterReceiver(
                    context
                )

            val offlineProvider =
                OfflineTileProvider(
                    registerReceiver,
                    arrayOf(mbtilesFile)
                )

            mapView.tileProvider =
                offlineProvider

            val sourceName =
                offlineProvider.archives
                    .firstOrNull()
                    ?.tileSources
                    ?.firstOrNull()

            val offlineTileSource =
                sourceName?.let { name ->
                    runCatching {
                        FileBasedTileSource
                            .getSource(name)
                    }.getOrNull()
                } ?: TileSourceFactory
                    .DEFAULT_TILE_SOURCE

            mapView.setTileSource(
                offlineTileSource
            )

            // A real MBTiles package is authoritative for AGAP.
            mapView.setUseDataConnection(
                false
            )

            mapView.invalidate()
        } catch (_: Exception) {
            /*
             * If the MBTiles provider unexpectedly fails, do not leave a blank
             * provider installed. Fall back to the permanent cache-first path.
             */
            mapView.setTileSource(
                TileSourceFactory.MAPNIK
            )

            mapView.setUseDataConnection(
                networkAvailable
            )

            mapView.invalidate()
        }
    }
}