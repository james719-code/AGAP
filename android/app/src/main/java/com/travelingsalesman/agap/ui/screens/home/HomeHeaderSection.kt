package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus

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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
            Text(
                text = username?.takeIf { it.isNotBlank() }?.let { "Hello, $it" } ?: "Your emergency hub",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Get help. Stay connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                label = if (isOnline) "Online" else "Offline",
                accent = if (isOnline) AgapStatus.success else AgapStatus.warning
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocationOn,
                label = when {
                    !hasLocationPermission -> "No access"
                    !gpsEnabled -> "GPS off"
                    hasLocationFix -> "GPS ready"
                    else -> "Locating"
                },
                accent = when {
                    !hasLocationPermission || !gpsEnabled -> MaterialTheme.colorScheme.error
                    hasLocationFix -> AgapStatus.success
                    else -> AgapStatus.warning
                }
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Bluetooth,
                label = "$meshPeers nearby",
                accent = if (meshPeers > 0) AgapStatus.info else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ReadinessPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = AgapShape.md
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgapSpacing.xs, vertical = AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium)
        }
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
        isOnline && transmissionMode != TransmissionMode.FORCE_BLE_MESH -> "Add details and share your location"
        transmissionMode == TransmissionMode.FORCE_INTERNET -> "Saved until internet is available"
        else -> "Save a report for nearby relay"
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(AgapSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, AgapShape.lg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Text("Report an incident", style = MaterialTheme.typography.titleMedium)
                Text(routeMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
