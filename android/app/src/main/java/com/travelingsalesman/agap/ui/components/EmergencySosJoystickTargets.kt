package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.*

@Composable
internal fun JoystickHeader(isLoading: Boolean, isDragging: Boolean, candidate: EmergencyTypeOption?, isPreview: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, AgapShape.circle))
            Text(if (isPreview) "Explore emergency SOS" else "Emergency SOS",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Text(when {
            isLoading -> "Sending your alert…"
            isDragging && candidate != null -> "${candidate.displayName} selected"
            isPreview -> "Try the SOS controls"
            else -> "Need emergency help?"
        }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(if (isPreview) "Explore the controls. Sign in to send an alert."
            else "Drag SOS toward the help you need, then release beyond the dashed ring.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PremiumEmergencyTarget(
    option: EmergencyTypeOption, isCandidate: Boolean, isSelected: Boolean,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val accent = option.iconTintColor()
    val label = EmergencyType.fromCode(option.code).label
    val scale by animateFloatAsState(if (isCandidate) 1.08f else 1f, label = "target_scale")
    Column(modifier.scale(scale).semantics { selected = isSelected || isCandidate }
        .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        Surface(shape = AgapShape.lg,
            color = if (isSelected || isCandidate) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, if (isSelected || isCandidate) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(option.icon, contentDescription = option.displayName, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, color = if (isSelected || isCandidate) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
internal fun DragInstruction(candidate: EmergencyTypeOption?, isArmed: Boolean, accent: Color, modifier: Modifier = Modifier, isPreview: Boolean = false) {
    Surface(modifier, shape = AgapShape.pill, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isArmed) accent else MaterialTheme.colorScheme.outlineVariant)) {
        Text(when {
            isPreview && isArmed -> "Release to sign in • no alert is sent"
            isArmed && candidate != null -> "Release to send ${candidate.displayName} SOS"
            candidate != null -> "Keep dragging to confirm"
            else -> "Move toward an emergency type"
        }, color = if (isArmed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm))
    }
}

@Composable
internal fun JoystickSafetyFooter(selected: EmergencyTypeOption?, isLoading: Boolean) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, AgapShape.md)
        .padding(AgapSpacing.md), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isLoading) "Handing your alert to the emergency network…" else "Taps select. Drag and release to send.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
    }
}
