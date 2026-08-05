package com.travelingsalesman.agap.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmdroidMapView(
    modifier: Modifier = Modifier,
    userLocation: Location? = null,
    incidents: List<IncidentEntity> = emptyList(),
    zoomLevel: Double = 15.0,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setUseDataConnection(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(zoomLevel)
            
            val defaultCenter = GeoPoint(13.1391, 123.7438)
            controller.setCenter(defaultCenter)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.setTileSource(TileSourceFactory.MAPNIK)

            if (isDarkTheme) {
                // Invert and darken map tiles to produce dark mode map matching design mockups
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.75f, 0f, 0f, 0f, 200f,
                        0f, -0.75f, 0f, 0f, 200f,
                        0f, 0f, -0.75f, 0f, 200f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
            } else {
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }


            map.overlays.clear()

            // Draw User Location Marker
            userLocation?.let { loc ->
                val userPoint = GeoPoint(loc.latitude, loc.longitude)
                val userMarker = Marker(map).apply {
                    position = userPoint
                    title = "Your Location"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(userMarker)
                map.controller.setCenter(userPoint)
            }

            // Draw Incident Markers
            incidents.forEach { incident ->
                val incidentPoint = GeoPoint(incident.latitude, incident.longitude)
                val incidentMarker = Marker(map).apply {
                    position = incidentPoint
                    title = "${incident.emergencyType} - ${incident.status}"
                    snippet = "Target: ${incident.targetId}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(incidentMarker)
            }

            map.invalidate()
        }
    )
}

