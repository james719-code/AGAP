package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus

/** Shared visual language for operator and field responder dashboards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OperationalWorkspaceHeader(
    title: String,
    subtitle: String,
    role: String,
    icon: ImageVector,
    isOnline: Boolean,
    mapAction: String,
    onOpenMap: () -> Unit,
    statusDetail: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val onCard = colors.onSurface
    // Tone-based surface (Material 3); the unit's identity is carried by the red badge.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = colors.surface,
        contentColor = onCard,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Box(Modifier.size(52.dp).background(colors.primary, AgapShape.circle), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = colors.onPrimary, modifier = Modifier.size(AgapSpacing.xxl))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(role, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.secondary)
                    Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.semantics { heading() })
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                val dot = if (isOnline) AgapStatus.success else AgapStatus.warning
                Surface(shape = AgapShape.pill, color = dot.copy(alpha = 0.12f), contentColor = dot) {
                    Row(Modifier.padding(horizontal = AgapSpacing.sm, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, null, tint = dot, modifier = Modifier.size(14.dp))
                        Text(if (isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) {
                    Surface(shape = AgapShape.pill, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                        Text("Local test data", Modifier.padding(horizontal = AgapSpacing.sm, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            statusDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            FilledTonalButton(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = AgapShape.pill
            ) {
                Icon(Icons.Outlined.Fullscreen, null, modifier = Modifier.size(AgapSpacing.xl))
                Spacer(Modifier.width(AgapSpacing.sm))
                Text(mapAction, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

internal data class OperationalMetric(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accent: Color
)

/** One connected strip of counters, matching the citizen home status strip. */
@Composable
internal fun OperationalMetrics(metrics: List<OperationalMetric>) {
    val colors = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale > 1.3f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        if (stacked) {
            Column {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    MetricCell(metric, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min)) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) VerticalDivider(color = colors.outlineVariant)
                    MetricCell(metric, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCell(metric: OperationalMetric, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(AgapSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(16.dp))
            Text(metric.label, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Text(metric.value, color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
    }
}
