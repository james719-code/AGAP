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

@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapPackageManager: MapPackageManager
) {

    fun configureOfflineMapView(
        mapView: MapView,
        isOnline: Boolean
    ) {
        val mbtilesFile = mapPackageManager.getDeploymentMapFile()

        /*
         * OFFLINE MODE
         */
        if (!isOnline) {
            if (
                mbtilesFile == null ||
                !mbtilesFile.exists() ||
                mbtilesFile.length() == 0L
            ) {
                configureWithoutOfflineMap(mapView)
                return
            }

            try {
                val registerReceiver = SimpleRegisterReceiver(context)

                val offlineProvider = OfflineTileProvider(
                    registerReceiver,
                    arrayOf(mbtilesFile)
                )

                // Replace the normal online provider with the MBTiles-only provider.
                mapView.tileProvider = offlineProvider

                /*
                 * MBTiles archives contain a tile-source NAME.
                 *
                 * archive.tileSources -> Set<String>
                 *
                 * MapView.setTileSource() needs ITileSource,
                 * therefore convert the String through FileBasedTileSource.
                 */
                val sourceName: String? = offlineProvider
                    .archives
                    .firstOrNull()
                    ?.tileSources
                    ?.firstOrNull()

                val offlineTileSource = sourceName
                    ?.let { name ->
                        runCatching {
                            FileBasedTileSource.getSource(name)
                        }.getOrNull()
                    }
                    ?: TileSourceFactory.DEFAULT_TILE_SOURCE

                mapView.setTileSource(offlineTileSource)

                // Absolutely no tile downloads in offline mode.
                mapView.setUseDataConnection(false)

                mapView.invalidate()

            } catch (e: Exception) {
                e.printStackTrace()

                configureWithoutOfflineMap(mapView)
            }

            return
        }

        /*
         * ONLINE MODE
         */
        mapView.setUseDataConnection(true)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.invalidate()
    }

    /**
     * The app can still show markers/coordinates even when
     * no usable offline MBTiles package exists.
     *
     * We deliberately disable network access here.
     */
    private fun configureWithoutOfflineMap(
        mapView: MapView
    ) {
        mapView.setUseDataConnection(false)
        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        mapView.invalidate()
    }
}