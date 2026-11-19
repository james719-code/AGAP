package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeReadiness(
    isOnline: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    hasLocationFix: Boolean,
    meshPeers: Int
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        ReadinessPill(
            if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            if (isOnline) "Online" else "No internet",
            if (isOnline) AgapStatus.success else AgapStatus.warning
        )
        ReadinessPill(Icons.Outlined.LocationOn, when {
            !hasLocationPermission -> "Location needed"
            !gpsEnabled -> "GPS off"
            hasLocationFix -> "Location ready"
            else -> "Finding location"
        }, when {
            !hasLocationPermission || !gpsEnabled -> MaterialTheme.colorScheme.error
            hasLocationFix -> AgapStatus.success
            else -> AgapStatus.warning
        })
        val relayLabel = when (meshPeers) {
            0 -> "No nearby devices"
            1 -> "1 nearby device"
            else -> "$meshPeers nearby devices"
        }
        ReadinessPill(
            Icons.Outlined.Bluetooth, relayLabel,
            if (meshPeers > 0) AgapStatus.info else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadinessPill(icon: ImageVector, label: String, accent: Color) {
    Surface(
        shape = AgapShape.pill,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Box(
                Modifier.size(20.dp).background(accent.copy(alpha = 0.12f), AgapShape.circle),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(AgapSpacing.lg))
            }
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun DetailedReportAction(
    onClick: () -> Unit,
    isOnline: Boolean,
    transmissionMode: TransmissionMode,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val routeMessage = when {
        isPreview -> "Sign in to send a report"
        com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST -> "Saved locally in test mode"
        isOnline && transmissionMode != TransmissionMode.FORCE_BLE_MESH -> "Share details with responders"
        transmissionMode == TransmissionMode.FORCE_INTERNET -> "Saved until internet is available"
        else -> "Can queue for nearby relay"
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(AgapSpacing.lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, AgapShape.md),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(AgapSpacing.section))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Text("Report an incident", style = MaterialTheme.typography.titleMedium)
                Text(routeMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(AgapSpacing.xl))
        }
    }
}
