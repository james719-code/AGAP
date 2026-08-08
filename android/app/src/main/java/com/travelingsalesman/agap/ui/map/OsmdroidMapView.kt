package com.travelingsalesman.agap.ui.map

import android.graphics.Color
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
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmdroidMapView(
    modifier: Modifier = Modifier,
    userLocation: Location? = null,
    incidents: List<IncidentEntity> = emptyList(),
    responders: List<ResponderUnitEntity> = emptyList(),
    targetIncident: IncidentEntity? = null,
    pickerPoint: GeoPoint? = null,
    onMapClick: ((GeoPoint) -> Unit)? = null,
    showIncidents: Boolean = true,
    showResponders: Boolean = true,
    zoomLevel: Double = 15.0,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isOnline: Boolean = true
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setUseDataConnection(isOnline)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(zoomLevel)
            
            // Initial camera default center for opening map (never used as incident/responder location)
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
            map.setUseDataConnection(isOnline)
            if (isOnline) {
                map.setTileSource(TileSourceFactory.MAPNIK)
            } else {
                map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
            }

            if (isDarkTheme) {
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

            // Handle Tap / Long-press for location picker
            if (onMapClick != null) {
                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let { onMapClick(it) }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { onMapClick(it) }
                        return true
                    }
                }
                map.overlays.add(MapEventsOverlay(eventsReceiver))
            }

            // Draw Picker Marker
            pickerPoint?.let { pt ->
                val pickerMarker = Marker(map).apply {
                    position = pt
                    title = "Selected Incident Pin"
                    snippet = "Coordinates: %.6f, %.6f".format(pt.latitude, pt.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(pickerMarker)
            }

            // Draw User Location Marker
            userLocation?.let { loc ->
                val userPoint = GeoPoint(loc.latitude, loc.longitude)
                val userMarker = Marker(map).apply {
                    position = userPoint
                    title = "Your Current Position"
                    snippet = "GPS: %.4f, %.4f".format(loc.latitude, loc.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(userMarker)
                if (targetIncident == null && pickerPoint == null) {
                    map.controller.setCenter(userPoint)
                }
            }

            // Draw Incident Markers (Never plot (0,0) or unknown coordinates)
            if (showIncidents) {
                incidents.forEach { incident ->
                    if (incident.latitude != 0.0 || incident.longitude != 0.0) {
                        val isP2p = incident.channel == "BLE_MESH" || incident.deliveryPath == "BLE_CLOUD_SHORTCUT"
                        val sourceTag = if (isP2p) {
                            if (incident.status == "QUEUED") "[P2P QUEUED]" else "[P2P RECEIVED]"
                        } else "[ONLINE DB]"
                        val incidentPoint = GeoPoint(incident.latitude, incident.longitude)
                        val incidentMarker = Marker(map).apply {
                            position = incidentPoint
                            title = "$sourceTag ${incident.emergencyType} (#${incident.incidentId})"
                            snippet = "Status: ${incident.status}\nSource: ${if (isP2p) "P2P BLE Relay" else "Database Online"}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(incidentMarker)
                    }
                }
            }

            // Draw Responder Unit Markers
            if (showResponders) {
                responders.forEach { responder ->
                    if (responder.currentLat != 0.0 && responder.currentLon != 0.0) {
                        val respPoint = GeoPoint(responder.currentLat, responder.currentLon)
                        val respMarker = Marker(map).apply {
                            position = respPoint
                            title = "\uD83D\uDE92 ${responder.unitCallsign} (${responder.status})"
                            snippet = "Target: ${responder.targetId}\nAgency ID: #${responder.agencyId}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(respMarker)
                    }
                }
            }

            // Navigation Vector & En Route Camera Target Tracking
            targetIncident?.let { target ->
                if (target.latitude != 0.0 && target.longitude != 0.0) {
                    val targetPoint = GeoPoint(target.latitude, target.longitude)
                    
                    // Only draw polyline if actual user position is available; do not use fake Legazpi center fallback
                    if (userLocation != null) {
                        val startPoint = GeoPoint(userLocation.latitude, userLocation.longitude)
                        val line = Polyline(map).apply {
                            addPoint(startPoint)
                            addPoint(targetPoint)
                            outlinePaint.color = Color.parseColor("#F2A540")
                            outlinePaint.strokeWidth = 8f
                        }
                        map.overlays.add(line)
                    }

                    map.controller.setCenter(targetPoint)
                    map.controller.setZoom(15.5)
                }
            }

            map.invalidate()
        }
    )
}
