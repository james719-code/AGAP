package com.travelingsalesman.agap.ui.components

import androidx.compose.ui.geometry.Offset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun orderEmergencyTypes(
    source: List<EmergencyTypeOption>
): List<EmergencyTypeOption> {
    val order = listOf(
        "MEDICAL",
        "FIRE",
        "ACCIDENT",
        "DISASTER",
        "CRIME",
        "POLICE"
    )

    return source.sortedWith(
        compareBy<EmergencyTypeOption> {
            val index = order.indexOf(it.code.uppercase(Locale.ROOT))
            if (index == -1) {
                Int.MAX_VALUE
            } else {
                index
            }
        }.thenBy {
            it.displayName
        }
    )
}

internal fun clampOffset(
    offset: Offset,
    maxRadius: Float
): Offset {
    val magnitude = offsetMagnitude(offset)

    if (magnitude <= maxRadius || magnitude == 0f) {
        return offset
    }

    val scale = maxRadius / magnitude

    return Offset(
        x = offset.x * scale,
        y = offset.y * scale
    )
}

internal fun offsetMagnitude(
    offset: Offset
): Float {
    return sqrt(
        (offset.x * offset.x) + (offset.y * offset.y)
    )
}

internal fun nearestRadialIndex(
    offset: Offset,
    count: Int
): Int {
    if (count <= 1) {
        return 0
    }

    var bestIndex = 0
    var bestDot = Float.NEGATIVE_INFINITY

    val magnitude = offsetMagnitude(offset).coerceAtLeast(0.0001f)

    val nx = offset.x / magnitude
    val ny = offset.y / magnitude

    val step = (2.0 * PI) / count

    repeat(count) { index ->
        val angle = (-PI / 2.0) + (index * step)

        val tx = cos(angle).toFloat()
        val ty = sin(angle).toFloat()

        val dot = (nx * tx) + (ny * ty)

        if (dot > bestDot) {
            bestDot = dot
            bestIndex = index
        }
    }

    return bestIndex
}
