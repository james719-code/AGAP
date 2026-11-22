package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.*

@Composable
internal fun JoystickHeader(isLoading: Boolean, isPreview: Boolean = false) {
    val accent = if (isPreview) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            Box(Modifier.size(8.dp).background(accent, AgapShape.circle))
            Text(
                if (isPreview) "Practice mode" else "Emergency SOS",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
        Text(when {
            isLoading -> "Sending your alert…"
            isPreview -> "Try the SOS controls"
            else -> "Need help now?"
        }, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() })
        Text(if (isPreview) "Drag SOS toward a type to practice. No emergency alert will be sent."
            else "Drag the red button toward the help you need, then release.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PremiumEmergencyTarget(
    option: EmergencyTypeOption, isCandidate: Boolean, isSelected: Boolean,
    enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val accent = option.iconTintColor()
    val label = EmergencyType.fromCode(option.code).label
    val active = isSelected || isCandidate
    val foreground = MaterialTheme.colorScheme.onSurface
    Column(modifier.semantics { selected = active }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs, Alignment.CenterVertically)) {
        Surface(shape = AgapShape.circle,
            color = if (active) accent else MaterialTheme.colorScheme.surface,
            contentColor = if (active) contentColorForAccent(accent) else accent,
            shadowElevation = if (active) 6.dp else 1.dp,
            border = if (active) null else BorderStroke(1.5.dp, accent.copy(alpha = 0.45f))) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Icon(option.icon, contentDescription = option.displayName,
                    tint = if (active) contentColorForAccent(accent) else accent, modifier = Modifier.size(AgapSpacing.xxl))
            }
        }
        if (showLabel) {
            Text(label, color = foreground, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** A wrapping equivalent for labels that no longer fit around the fixed-size dial at large text sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LargeTextSosLabels(
    options: List<EmergencyTypeOption>, selectedCode: String, enabled: Boolean, onSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        options.forEach { option ->
            val active = option.code.equals(selectedCode, ignoreCase = true)
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.heightIn(min = 48.dp).selectable(active, enabled, Role.RadioButton) { onSelected(option.code) },
                shape = AgapShape.md,
                color = if (active) colors.primaryContainer else colors.surface,
                border = BorderStroke(1.dp, if (active) colors.primary else colors.outline)
            ) {
                Row(Modifier.padding(AgapSpacing.sm), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    Icon(option.icon, null, tint = colors.onSurface, modifier = Modifier.size(AgapSpacing.xxl))
                    Text(EmergencyType.fromCode(option.code).label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    if (active) Icon(Icons.Outlined.CheckCircle, null, tint = colors.primary, modifier = Modifier.size(AgapSpacing.xl))
                }
            }
        }
    }
}

@Composable
internal fun DragInstruction(candidate: EmergencyTypeOption?, isArmed: Boolean, accent: Color, modifier: Modifier = Modifier, isPreview: Boolean = false) {
    Surface(modifier, shape = AgapShape.md,
        color = if (isArmed) accent else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isArmed) contentColorForAccent(accent) else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (isArmed) accent else MaterialTheme.colorScheme.outlineVariant)) {
        Text(when {
            isPreview && isArmed -> "Release to sign in • no alert is sent"
            isArmed && candidate != null -> "Release to send ${candidate.displayName} SOS"
            candidate != null -> "Keep dragging to confirm"
            else -> "Move toward an emergency type"
        }, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm))
    }
}

@Composable
internal fun JoystickSafetyFooter(isLoading: Boolean, isPreview: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm, Alignment.CenterHorizontally)) {
        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when {
            isLoading -> "Sending your alert. Wait for a delivery update."
            isPreview -> "Preview only. Sign in to send an alert."
            else -> "Tapping a type only selects it. After you release, you have 5 seconds to cancel."
        },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false))
    }
}
