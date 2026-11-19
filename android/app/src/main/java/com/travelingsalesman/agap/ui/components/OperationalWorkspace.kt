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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Box(Modifier.size(52.dp).background(colors.secondaryContainer, AgapShape.lg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(AgapSpacing.section))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    Text(role, style = MaterialTheme.typography.labelMedium, color = colors.secondary)
                    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                val accent = if (isOnline) AgapStatus.success else AgapStatus.warning
                Surface(shape = AgapShape.pill, color = accent.copy(alpha = 0.10f)) {
                    Row(Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                        Icon(if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, null, tint = accent, modifier = Modifier.size(AgapSpacing.lg))
                        Text(if (isOnline) "Online" else "Offline", color = accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) {
                    Surface(shape = AgapShape.pill, color = colors.secondaryContainer) {
                        Text("Local test data", Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
                            color = colors.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            statusDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            OutlinedButton(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = AgapShape.md,
                border = BorderStroke(1.dp, colors.outlineVariant)
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

@Composable
internal fun OperationalMetrics(metrics: List<OperationalMetric>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val useGrid = maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.3f
        if (useGrid) {
            Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                metrics.forEach { metric ->
                    OperationalMetricCard(metric, Modifier.weight(1f), stacked = false)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                metrics.forEach { metric ->
                    OperationalMetricCard(metric, Modifier.fillMaxWidth(), stacked = true)
                }
            }
        }
    }
}

@Composable
private fun OperationalMetricCard(metric: OperationalMetric, modifier: Modifier, stacked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = AgapShape.lg, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        if (stacked) {
            Row(Modifier.padding(AgapSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(AgapSpacing.xxl))
                Column(Modifier.weight(1f)) {
                    Text(metric.label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(metric.value, color = colors.onSurface, style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            Column(Modifier.heightIn(min = 96.dp).padding(AgapSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs, Alignment.CenterVertically)) {
                Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(AgapSpacing.xl))
                Text(metric.value, color = colors.onSurface, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(metric.label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
