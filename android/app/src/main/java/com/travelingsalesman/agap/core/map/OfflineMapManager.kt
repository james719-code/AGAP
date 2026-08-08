package com.travelingsalesman.agap.core.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapPackageManager: MapPackageManager
) {
    fun configureOfflineMapView(mapView: MapView, isOnline: Boolean) {
        mapView.setUseDataConnection(isOnline)
        val mbtilesFile = mapPackageManager.getDeploymentMapFile()
        if (mbtilesFile != null && mbtilesFile.exists()) {
            mapView.setUseDataConnection(false)
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
        }
    }
}
