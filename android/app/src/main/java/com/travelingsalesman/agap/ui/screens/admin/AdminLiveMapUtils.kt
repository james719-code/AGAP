package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import java.util.Locale
import kotlin.math.max

internal fun formatRouteDistance(distanceMeters: Double): String =
    if (distanceMeters < 1000.0) {
        "${distanceMeters.toInt()} m"
    } else {
        "%.1f km".format(Locale.getDefault(), distanceMeters / 1000.0)
    }

internal fun formatRouteEta(durationSeconds: Long): String {
    val totalMinutes = (durationSeconds / 60L).coerceAtLeast(1L)
    return if (totalMinutes < 60L) {
        "~$totalMinutes min"
    } else {
        "~${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    }
}

internal fun distanceText(
    userLocation: Location?,
    incident: IncidentEntity
): String? {
    val user = userLocation ?: return null

    val result = FloatArray(1)
    Location.distanceBetween(
        user.latitude,
        user.longitude,
        incident.latitude,
        incident.longitude,
        result
    )

    val meters = max(0f, result[0])
    return if (meters < 1000f) {
        "${meters.toInt()} m away"
    } else {
        "%.1f km away".format(
            Locale.getDefault(),
            meters / 1000f
        )
    }
}

internal fun formatMapAge(
    timestamp: Long
): String {
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L

    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 1_440L -> "${minutes / 60L} hr ago"
        else -> "${minutes / 1_440L} d ago"
    }
}

internal fun mapPriority(
    status: String
): Int {
    return when (status.uppercase()) {
        "RECEIVED" -> 0
        "ACKNOWLEDGED" -> 1
        "DISPATCHED" -> 2
        "EN_ROUTE" -> 3
        "ON_SCENE" -> 4
        "RESOLVED" -> 5
        else -> 6
    }
}

internal fun ResponderUnitDto.toMapEntity() =
    ResponderUnitEntity(
        id = id,
        agencyId = agencyId,
        targetId = targetId,
        unitCallsign = unitCallsign,
        deviceId = deviceId,
        status = status,
        currentLat = currentLat,
        currentLon = currentLon,
        lastSeenAt = lastSeenAt
    )
