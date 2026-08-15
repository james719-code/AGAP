package com.travelingsalesman.agap.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.util.LruCache
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.core.routing.RouteCoordinate
import com.travelingsalesman.agap.ui.theme.EmergencyType
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Commands used by full-screen map controls without exposing MapView outside this adapter.
 */
enum class MapCameraAction {
    ZOOM_IN,
    ZOOM_OUT,
    RECENTER,
    FOCUS_TARGET,
    FOCUS_POINT
}

data class MapCameraCommand(
    val id: Long,
    val action: MapCameraAction,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val zoom: Double? = null
)

@Composable
fun OsmdroidMapView(
    modifier: Modifier = Modifier,
    userLocation: Location? = null,
    incidents: List<IncidentEntity> = emptyList(),
    responders: List<ResponderUnitEntity> = emptyList(),
    targetIncident: IncidentEntity? = null,
    routePoints: List<RouteCoordinate> = emptyList(),
    routeIsRoadRoute: Boolean = true,
    routeResponderId: Long? = null,
    autoFitRoute: Boolean = false,
    pickerPoint: GeoPoint? = null,
    onMapClick: ((GeoPoint) -> Unit)? = null,
    onIncidentClick: ((IncidentEntity) -> Unit)? = null,
    cameraCommand: MapCameraCommand? = null,
    showIncidents: Boolean = true,
    showResponders: Boolean = true,
    zoomLevel: Double = 15.0,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isOnline: Boolean = true
) {
    val context = LocalContext.current
    val effectiveOnline = isOnline && !ServerConfigManager.isBleOnlyActive

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setUseDataConnection(effectiveOnline)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(zoomLevel)

            // Camera fallback only. Never used as incident/responder coordinates.
            controller.setCenter(
                GeoPoint(13.1391, 123.7438)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    val offlineMapManager = remember {
        com.travelingsalesman.agap.core.map.OfflineMapManager(
            context,
            com.travelingsalesman.agap.core.map.MapPackageManager(context)
        )
    }

    // Non-Compose holders prevent expensive map-provider/overlay work on unrelated recompositions.
    val configuredOnlineMode = remember {
        arrayOfNulls<Boolean>(1)
    }
    val hasInitialCameraCenter = remember {
        booleanArrayOf(false)
    }
    val lastTargetIncidentId = remember {
        arrayOfNulls<String>(1)
    }
    val lastOverlaySignature = remember {
        intArrayOf(Int.MIN_VALUE)
    }
    val lastCameraCommandId = remember {
        longArrayOf(Long.MIN_VALUE)
    }
    val lastRouteFitSignature = remember {
        intArrayOf(Int.MIN_VALUE)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            /*
             * Cache-first base map:
             * - real MBTiles -> always local
             * - persistent osmdroid cache -> reused first
             * - only missing tiles may use network when isOnline == true
             */
            if (configuredOnlineMode[0] != effectiveOnline) {
                offlineMapManager.configureMapView(
                    mapView = map,
                    networkAvailable = effectiveOnline
                )
                configuredOnlineMode[0] = effectiveOnline
            }

            /*
             * Marker overlays are more expensive than ordinary Compose rows.
             * Keep a defensive upper bound even if a caller accidentally passes a huge list.
             */
            val visibleIncidents =
                incidents
                    .asSequence()
                    .filter {
                        it.latitude != 0.0 ||
                                it.longitude != 0.0
                    }
                    .take(MAX_INCIDENT_MARKERS)
                    .toList()

            val visibleResponders =
                responders
                    .asSequence()
                    .filter {
                        it.currentLat != 0.0 &&
                                it.currentLon != 0.0
                    }
                    .take(MAX_RESPONDER_MARKERS)
                    .toList()

            val overlaySignature =
                buildOverlaySignature(
                    userLocation = userLocation,
                    incidents = visibleIncidents,
                    responders = visibleResponders,
                    targetIncident = targetIncident,
                    routePoints = routePoints,
                    routeIsRoadRoute = routeIsRoadRoute,
                    routeResponderId = routeResponderId,
                    pickerPoint = pickerPoint,
                    hasMapClick = onMapClick != null,
                    hasIncidentClick = onIncidentClick != null,
                    showIncidents = showIncidents,
                    showResponders = showResponders
                )

            if (isDarkTheme) {
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.75f, 0f, 0f, 0f, 200f,
                        0f, -0.75f, 0f, 0f, 200f,
                        0f, 0f, -0.75f, 0f, 200f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                map.overlayManager.tilesOverlay.setColorFilter(
                    ColorMatrixColorFilter(matrix)
                )
            } else {
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }

            val overlaysChanged =
                lastOverlaySignature[0] != overlaySignature

            if (overlaysChanged) {
                map.overlays.clear()

                // Picker / generic map-tap interaction.
                if (onMapClick != null) {
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(
                            p: GeoPoint?
                        ): Boolean {
                            p?.let(onMapClick)
                            return true
                        }

                        override fun longPressHelper(
                            p: GeoPoint?
                        ): Boolean {
                            p?.let(onMapClick)
                            return true
                        }
                    }

                    map.overlays.add(
                        MapEventsOverlay(receiver)
                    )
                }

                // Draw route geometry before markers so incident/responder pins
                // remain readable above the navigation line.
                // A two-point line is only permitted when OfflineRouteEngine
                // explicitly marks it as a direct fallback.
                if (routePoints.size >= 2) {
                    val route = Polyline(map).apply {
                        routePoints.forEach { point ->
                            addPoint(GeoPoint(point.latitude, point.longitude))
                        }
                        outlinePaint.color = if (routeIsRoadRoute) {
                            Color.parseColor("#F2A540")
                        } else {
                            Color.parseColor("#C97B63")
                        }
                        outlinePaint.strokeWidth = if (routeIsRoadRoute) 9f else 6f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.pathEffect = if (routeIsRoadRoute) {
                            null
                        } else {
                            DashPathEffect(floatArrayOf(18f, 12f), 0f)
                        }
                    }
                    map.overlays.add(route)
                }

                pickerPoint?.let { point ->
                    val marker = Marker(map).apply {
                        position = point
                        title = "Selected Incident Pin"
                        snippet =
                            "Coordinates: %.6f, %.6f".format(
                                point.latitude,
                                point.longitude
                            )
                        icon = createPremiumPinDrawable(
                            context = context,
                            fillColor = Color.parseColor("#E9573F"),
                            label = "PIN",
                            focused = true
                        )
                        setAnchor(
                            Marker.ANCHOR_CENTER,
                            Marker.ANCHOR_BOTTOM
                        )
                    }
                    map.overlays.add(marker)
                }

                userLocation?.let { location ->
                    val userPoint = GeoPoint(
                        location.latitude,
                        location.longitude
                    )

                    val userMarker = Marker(map).apply {
                        position = userPoint
                        title = "Command device"
                        snippet =
                            "GPS: %.5f, %.5f".format(
                                location.latitude,
                                location.longitude
                            )
                        icon = createPremiumPinDrawable(
                            context = context,
                            fillColor = Color.parseColor("#2DBE7F"),
                            label = "ME",
                            focused = false
                        )
                        setAnchor(
                            Marker.ANCHOR_CENTER,
                            Marker.ANCHOR_BOTTOM
                        )
                    }

                    map.overlays.add(userMarker)

                    if (
                        !hasInitialCameraCenter[0] &&
                        targetIncident == null &&
                        pickerPoint == null
                    ) {
                        map.controller.setCenter(userPoint)
                        hasInitialCameraCenter[0] = true
                    }
                }

                if (showIncidents) {
                    visibleIncidents.forEach { incident ->
                        val isP2p =
                            incident.channel.equals(
                                "BLE_MESH",
                                ignoreCase = true
                            ) ||
                                    incident.deliveryPath.equals(
                                        "BLE_CLOUD_SHORTCUT",
                                        ignoreCase = true
                                    )

                        val sourceTag =
                            if (isP2p) {
                                "MESH"
                            } else {
                                "ONLINE"
                            }

                        val isFocused =
                            targetIncident?.incidentId ==
                                    incident.incidentId

                        val incidentMarker = Marker(map).apply {
                            position = GeoPoint(
                                incident.latitude,
                                incident.longitude
                            )
                            title =
                                "${incident.emergencyType} • Incident #${incident.incidentId}"
                            snippet =
                                "${incident.status} • $sourceTag"
                            icon = createPremiumPinDrawable(
                                context = context,
                                fillColor = incidentPinColor(incident),
                                label = emergencyShortLabel(
                                    incident.emergencyType
                                ),
                                focused = isFocused
                            )
                            setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_BOTTOM
                            )
                        }

                        if (onIncidentClick != null) {
                            incidentMarker.setOnMarkerClickListener(
                                Marker.OnMarkerClickListener { _, _ ->
                                    onIncidentClick(incident)
                                    true
                                }
                            )
                        }

                        map.overlays.add(incidentMarker)
                    }
                }

                if (showResponders) {
                    visibleResponders.forEach { responder ->
                        val responderMarker = Marker(map).apply {
                            position = GeoPoint(
                                responder.currentLat,
                                responder.currentLon
                            )
                            title =
                                "${responder.unitCallsign} • ${responder.status}"
                            snippet =
                                "Responder Unit • Agency #${responder.agencyId}"
                            icon = createPremiumPinDrawable(
                                context = context,
                                fillColor = responderPinColor(
                                    responder.status
                                ),
                                label = "R",
                                focused = routeResponderId == responder.id
                            )
                            setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_BOTTOM
                            )
                        }

                        map.overlays.add(responderMarker)
                    }
                }

                if (!hasInitialCameraCenter[0]) {
                    val initialCenterPoint = when {
                        targetIncident != null && (targetIncident.latitude != 0.0 || targetIncident.longitude != 0.0) -> {
                            lastTargetIncidentId[0] = targetIncident.incidentId
                            GeoPoint(targetIncident.latitude, targetIncident.longitude)
                        }
                        userLocation != null && (userLocation.latitude != 0.0 || userLocation.longitude != 0.0) -> {
                            GeoPoint(userLocation.latitude, userLocation.longitude)
                        }
                        visibleIncidents.isNotEmpty() -> {
                            GeoPoint(visibleIncidents.first().latitude, visibleIncidents.first().longitude)
                        }
                        visibleResponders.isNotEmpty() -> {
                            GeoPoint(visibleResponders.first().currentLat, visibleResponders.first().currentLon)
                        }
                        else -> null
                    }

                    if (initialCenterPoint != null) {
                        map.controller.setCenter(initialCenterPoint)
                        if (targetIncident != null) {
                            map.controller.setZoom(zoomLevel.coerceAtLeast(15.5))
                        } else {
                            map.controller.setZoom(zoomLevel)
                        }
                        hasInitialCameraCenter[0] = true
                    }
                } else if (targetIncident != null && (targetIncident.latitude != 0.0 || targetIncident.longitude != 0.0)) {
                    if (routePoints.size < 2 && lastTargetIncidentId[0] != targetIncident.incidentId) {
                        map.controller.setCenter(GeoPoint(targetIncident.latitude, targetIncident.longitude))
                        map.controller.setZoom(zoomLevel.coerceAtLeast(15.5))
                    }
                    lastTargetIncidentId[0] = targetIncident.incidentId
                }

                lastOverlaySignature[0] =
                    overlaySignature

                val routeFitSignature = routePoints.hashCode()
                if (
                    autoFitRoute &&
                    routePoints.size >= 2 &&
                    lastRouteFitSignature[0] != routeFitSignature
                ) {
                    val geoPoints = routePoints.map {
                        GeoPoint(it.latitude, it.longitude)
                    }
                    lastRouteFitSignature[0] = routeFitSignature
                    map.post {
                        runCatching {
                            map.zoomToBoundingBox(
                                BoundingBox.fromGeoPoints(geoPoints),
                                true,
                                96
                            )
                        }
                    }
                }

                map.invalidate()
            }

            // Full-screen control commands are processed even if marker data did not change.
            cameraCommand?.let { command ->
                if (
                    lastCameraCommandId[0] !=
                    command.id
                ) {
                    when (command.action) {
                        MapCameraAction.ZOOM_IN ->
                            map.controller.zoomIn()

                        MapCameraAction.ZOOM_OUT ->
                            map.controller.zoomOut()

                        MapCameraAction.RECENTER -> {
                            val point = when {
                                userLocation != null ->
                                    GeoPoint(
                                        userLocation.latitude,
                                        userLocation.longitude
                                    )

                                targetIncident != null ->
                                    GeoPoint(
                                        targetIncident.latitude,
                                        targetIncident.longitude
                                    )

                                visibleIncidents.isNotEmpty() ->
                                    GeoPoint(
                                        visibleIncidents.first().latitude,
                                        visibleIncidents.first().longitude
                                    )

                                else ->
                                    GeoPoint(13.1391, 123.7438)
                            }

                            map.controller.setCenter(point)
                        }

                        MapCameraAction.FOCUS_TARGET -> {
                            targetIncident?.let { target ->
                                map.controller.setCenter(
                                    GeoPoint(
                                        target.latitude,
                                        target.longitude
                                    )
                                )
                                map.controller.setZoom(
                                    command.zoom ?: 16.0
                                )
                            }
                        }

                        MapCameraAction.FOCUS_POINT -> {
                            val lat = command.latitude
                            val lon = command.longitude

                            if (lat != null && lon != null) {
                                map.controller.setCenter(
                                    GeoPoint(lat, lon)
                                )
                                map.controller.setZoom(
                                    command.zoom ?: 16.0
                                )
                            }
                        }
                    }

                    lastCameraCommandId[0] =
                        command.id
                    map.invalidate()
                }
            }
        }
    )
}

private fun buildOverlaySignature(
    userLocation: Location?,
    incidents: List<IncidentEntity>,
    responders: List<ResponderUnitEntity>,
    targetIncident: IncidentEntity?,
    routePoints: List<RouteCoordinate>,
    routeIsRoadRoute: Boolean,
    routeResponderId: Long?,
    pickerPoint: GeoPoint?,
    hasMapClick: Boolean,
    hasIncidentClick: Boolean,
    showIncidents: Boolean,
    showResponders: Boolean
): Int {
    var result = 17

    result =
        31 * result +
                (userLocation?.latitude?.hashCode() ?: 0)
    result =
        31 * result +
                (userLocation?.longitude?.hashCode() ?: 0)
    result =
        31 * result +
                hasMapClick.hashCode()
    result =
        31 * result +
                hasIncidentClick.hashCode()
    result =
        31 * result +
                showIncidents.hashCode()
    result =
        31 * result +
                showResponders.hashCode()
    result =
        31 * result +
                (targetIncident?.incidentId?.hashCode() ?: 0)
    result =
        31 * result + routePoints.hashCode()
    result =
        31 * result + routeIsRoadRoute.hashCode()
    result =
        31 * result + (routeResponderId?.hashCode() ?: 0)
    result =
        31 * result +
                (targetIncident?.latitude?.hashCode() ?: 0)
    result =
        31 * result +
                (targetIncident?.longitude?.hashCode() ?: 0)
    result =
        31 * result +
                (pickerPoint?.latitude?.hashCode() ?: 0)
    result =
        31 * result +
                (pickerPoint?.longitude?.hashCode() ?: 0)

    incidents.forEach { incident ->
        result =
            31 * result +
                    incident.incidentId.hashCode()
        result =
            31 * result +
                    incident.status.hashCode()
        result =
            31 * result +
                    incident.emergencyType.hashCode()
        result =
            31 * result +
                    incident.latitude.hashCode()
        result =
            31 * result +
                    incident.longitude.hashCode()
    }

    responders.forEach { responder ->
        result =
            31 * result +
                    responder.id.hashCode()
        result =
            31 * result +
                    responder.status.hashCode()
        result =
            31 * result +
                    responder.currentLat.hashCode()
        result =
            31 * result +
                    responder.currentLon.hashCode()
    }

    return result
}

private fun incidentPinColor(
    incident: IncidentEntity
): Int {
    if (
        incident.status.equals(
            "RESOLVED",
            ignoreCase = true
        )
    ) {
        return Color.parseColor("#2DBE7F")
    }

    return EmergencyType.fromCode(incident.emergencyType).color.toArgb()
}

private fun responderPinColor(
    status: String
): Int {
    return when (status.uppercase()) {
        "DISPATCHED",
        "EN_ROUTE" ->
            Color.parseColor("#F0A132")

        "ON_SCENE" ->
            Color.parseColor("#2DBE7F")

        "OFFLINE" ->
            Color.parseColor("#747B86")

        else ->
            Color.parseColor("#20A7C8")
    }
}

private fun emergencyShortLabel(
    type: String
): String = EmergencyType.fromCode(type).label.take(1)

private data class PinCacheKey(
    val density: Float,
    val fillColor: Int,
    val label: String,
    val focused: Boolean
)

private val pinDrawableCache = LruCache<PinCacheKey, Drawable>(96)

/**
 * Small custom map pin. This replaces osmdroid's generic green marker so the
 * command map can distinguish emergency/responder categories at a glance.
 * Uses an in-memory LruCache to prevent continuous Bitmap allocations on low-end devices.
 */
private fun createPremiumPinDrawable(
    context: Context,
    fillColor: Int,
    label: String,
    focused: Boolean
): Drawable {
    val density =
        context.resources.displayMetrics.density

    val key = PinCacheKey(
        density = density,
        fillColor = fillColor,
        label = label,
        focused = focused
    )

    pinDrawableCache.get(key)?.let { return it }

    fun dp(value: Float): Float =
        value * density

    val width =
        dp(if (focused) 48f else 42f).toInt()
    val height =
        dp(if (focused) 58f else 52f).toInt()

    val bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    val centerX = width / 2f
    val circleY =
        dp(if (focused) 20f else 18f)
    val radius =
        dp(if (focused) 16f else 14f)

    val fill = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = fillColor
        style = Paint.Style.FILL
        setShadowLayer(
            dp(4f),
            0f,
            dp(2f),
            Color.argb(110, 0, 0, 0)
        )
    }

    canvas.drawCircle(
        centerX,
        circleY,
        radius,
        fill
    )

    val tail = Path().apply {
        moveTo(
            centerX - dp(7f),
            circleY + dp(9f)
        )
        lineTo(
            centerX,
            height - dp(2f)
        )
        lineTo(
            centerX + dp(7f),
            circleY + dp(9f)
        )
        close()
    }
    canvas.drawPath(tail, fill)

    val border = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color =
            if (focused) {
                Color.WHITE
            } else {
                Color.argb(220, 245, 245, 245)
            }
        style = Paint.Style.STROKE
        strokeWidth =
            dp(if (focused) 2.5f else 1.5f)
    }

    canvas.drawCircle(
        centerX,
        circleY,
        radius,
        border
    )

    val textPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize =
            dp(
                when {
                    label.length >= 3 -> 8f
                    label.length == 2 -> 9f
                    else -> 12f
                }
            )
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
    }

    val metrics = textPaint.fontMetrics
    val baseline =
        circleY -
                (metrics.ascent + metrics.descent) /
                2f

    canvas.drawText(
        label,
        centerX,
        baseline,
        textPaint
    )

    val drawable = BitmapDrawable(
        context.resources,
        bitmap
    )
    pinDrawableCache.put(key, drawable)
    return drawable
}

private const val MAX_INCIDENT_MARKERS = 60
private const val MAX_RESPONDER_MARKERS = 60

