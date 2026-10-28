package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumProfileHeader(
    name: String,
    role: String,
    accountStatus: String?,
    isVerified: Boolean,
    memberSince: String?,
    lastLoginAt: String?
) {
    val statusColor = accountStatusColor(accountStatus)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = AgapShape.lg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, AgapShape.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.take(1).uppercase(Locale.ROOT) },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    Text("Your AGAP profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                CompactStatusChip(role.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Badge)
                if (isVerified) CompactStatusChip("Verified", AgapStatus.success, Icons.Outlined.VerifiedUser)
                CompactStatusChip(accountStatus?.replace('_', ' ')?.lowercase(Locale.ROOT)?.replaceFirstChar { it.titlecase(Locale.ROOT) } ?: "Unknown status", statusColor)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                ProfileDateRow("Member since", formatDateCompact(memberSince))
                ProfileDateRow("Last sign-in", formatDateCompact(lastLoginAt))
            }
        }
    }
}

@Composable
private fun ProfileDateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun CompactStatusChip(label: String, color: Color, icon: ImageVector? = null) {
    Surface(color = color.copy(alpha = 0.09f), shape = AgapShape.xs) {
        Row(
            Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun EmergencyReadinessCard(
    progress: Float,
    completed: Int,
    total: Int,
    emergencyContacts: Int,
    autoGps: Boolean,
    bleFallback: Boolean,
    pendingSync: Int
) {
    val readinessColor = when {
        progress >= 0.85f -> AgapStatus.success
        progress >= 0.60f -> AgapStatus.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Icon(Icons.Outlined.HealthAndSafety, null, tint = readinessColor, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emergency readiness", style = MaterialTheme.typography.titleMedium)
                    Text("$completed of $total essentials added", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(progress * 100).toInt()}%", color = readinessColor, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(AgapShape.pill),
                color = readinessColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    ReadinessMetric(Icons.Outlined.Call, emergencyContacts.toString(), "Contacts", emergencyContacts > 0, AgapStatus.success, Modifier.weight(1f))
                    ReadinessMetric(Icons.Outlined.LocationOn, if (autoGps) "On" else "Off", "Auto GPS", autoGps, AgapStatus.info, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    ReadinessMetric(Icons.Outlined.Bluetooth, if (bleFallback) "Ready" else "Off", "Offline relay", bleFallback, AgapStatus.info, Modifier.weight(1f))
                    ReadinessMetric(Icons.Outlined.Sync, pendingSync.toString(), "Pending", true, if (pendingSync == 0) AgapStatus.success else AgapStatus.warning, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ReadinessMetric(
    icon: ImageVector,
    value: String,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, AgapShape.md).padding(AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}
