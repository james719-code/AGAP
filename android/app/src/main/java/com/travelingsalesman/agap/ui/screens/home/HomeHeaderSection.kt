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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.theme.*

@Composable
internal fun HomeReadiness(
    isOnline: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    hasLocationFix: Boolean,
    meshPeers: Int
) {
    // One instrument strip: the three things that decide whether an SOS can leave this phone.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            ReadinessCell(
                icon = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                title = "Network",
                value = if (isOnline) "Online" else "Offline",
                accent = if (isOnline) AgapStatus.success else AgapStatus.warning,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReadinessCell(
                icon = Icons.Outlined.LocationOn,
                title = "Location",
                value = when {
                    !hasLocationPermission -> "Needed"
                    !gpsEnabled -> "GPS off"
                    hasLocationFix -> "Ready"
                    else -> "Finding…"
                },
                accent = when {
                    !hasLocationPermission || !gpsEnabled -> MaterialTheme.colorScheme.error
                    hasLocationFix -> AgapStatus.success
                    else -> AgapStatus.warning
                },
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReadinessCell(
                icon = Icons.Outlined.Bluetooth,
                title = "Relay",
                value = when (meshPeers) {
                    0 -> "None"
                    1 -> "1 phone"
                    else -> "$meshPeers phones"
                },
                accent = if (meshPeers > 0) AgapStatus.info else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReadinessCell(icon: ImageVector, title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    // Value first (what the person needs), label second; no all-caps.
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = AgapSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        Box(Modifier.size(28.dp).background(accent.copy(alpha = 0.14f), AgapShape.circle), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.md), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, AgapShape.circle),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(AgapSpacing.xxl))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Report an incident", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(routeMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(AgapSpacing.xl))
        }
    }
}
