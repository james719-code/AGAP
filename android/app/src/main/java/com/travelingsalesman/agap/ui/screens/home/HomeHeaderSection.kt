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
internal fun CitizenCommandHeader(
    username: String?,
    userDisplayId: String?,
    isOnline: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    hasLocationFix: Boolean,
    meshPeers: Int
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Text(
            username?.takeIf { it.isNotBlank() }?.let { "Hello, $it" } ?: "Here for you, always",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Help is within reach.", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            ReadinessPill(if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                if (isOnline) "Online" else "Offline",
                if (isOnline) AgapStatus.success else AgapStatus.warning)
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
            ReadinessPill(Icons.Outlined.Bluetooth, "$meshPeers nearby",
                if (meshPeers > 0) AgapStatus.info else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReadinessPill(icon: ImageVector, label: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
        Text(label, color = accent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun DetailedReportAction(
    onClick: () -> Unit,
    isOnline: Boolean,
    transmissionMode: TransmissionMode,
    modifier: Modifier = Modifier
) {
    val routeMessage = when {
        isOnline && transmissionMode != TransmissionMode.FORCE_BLE_MESH -> "Share details with responders"
        transmissionMode == TransmissionMode.FORCE_INTERNET -> "Saved until internet is available"
        else -> "Save details for nearby relay"
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(AgapSpacing.lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, AgapShape.md),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Text("Report an incident", style = MaterialTheme.typography.titleMedium)
                Text(routeMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
