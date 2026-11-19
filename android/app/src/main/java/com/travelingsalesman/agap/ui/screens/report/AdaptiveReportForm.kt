@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.EmergencyType
import com.travelingsalesman.agap.ui.theme.glassCard

private val reportCategories = listOf(
    EmergencyType.MEDICAL, EmergencyType.FIRE, EmergencyType.ACCIDENT,
    EmergencyType.DISASTER, EmergencyType.CRIME
)
private val reportSubjects = listOf(
    "SELF" to "Me",
    "OTHER_PERSON" to "Someone else",
    "PUBLIC_OR_UNKNOWN" to "Public or unknown"
)
private val peopleAffectedOptions = listOf(1 to "1", 3 to "2–5", 8 to "6–10", 11 to "11+")

@Composable
internal fun AdaptiveReportForm(
    uiState: ReportUiState,
    onTypeSelected: (String) -> Unit,
    onSubjectChanged: (String, Boolean) -> Unit,
    onPeopleAffectedChanged: (Int) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenMapPicker: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val canEdit = uiState.canEditDraft
    val bleOnly = uiState.transmissionMode == TransmissionMode.FORCE_BLE_MESH

    ReportSection("1", "Incident location") {
        LocationStatus(uiState)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            OutlinedButton(
                onClick = onOpenMapPicker,
                enabled = canEdit,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.Map, null, modifier = Modifier.size(AgapSpacing.lg))
                Text("Choose on map", Modifier.padding(start = AgapSpacing.sm))
            }
            OutlinedButton(
                onClick = onRefreshLocation,
                enabled = canEdit && !uiState.isRefreshingLocation,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.GpsFixed, null, modifier = Modifier.size(AgapSpacing.lg))
                Text(
                    if (uiState.isRefreshingLocation) "Locating…" else "Use my GPS",
                    Modifier.padding(start = AgapSpacing.sm)
                )
            }
        }
    }

    ReportSection("2", "Emergency type") {
        EmergencyTypeChoices(uiState.selectedType, canEdit, onTypeSelected)
    }

    ReportSection("3", "Who needs help?") {
        ReportChoices(reportSubjects, uiState.subjectRelation, canEdit) {
            onSubjectChanged(it, uiState.immediateDanger)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(
                    cornerShape = AgapShape.md,
                    backgroundColor = if (uiState.immediateDanger) colors.errorContainer else colors.surface,
                    borderColor = if (uiState.immediateDanger) colors.error else colors.outlineVariant
                )
                .toggleable(
                    value = uiState.immediateDanger,
                    enabled = canEdit,
                    role = Role.Switch,
                    onValueChange = { onSubjectChanged(uiState.subjectRelation, it) }
                )
                .padding(AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            val foreground = if (uiState.immediateDanger) colors.onErrorContainer else colors.onSurface
            Column(Modifier.weight(1f)) {
                Text("Immediate danger", style = MaterialTheme.typography.titleSmall, color = foreground)
                Text(
                    "Turn on if someone is in danger now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground
                )
            }
            Switch(checked = uiState.immediateDanger, onCheckedChange = null, enabled = canEdit)
        }
    }

    ReportSection("4", "People affected") {
        ReportChoices(peopleAffectedOptions, uiState.peopleAffected, canEdit, onPeopleAffectedChanged)
    }

    if (!bleOnly) {
        ReportSection("5", "Optional notes") {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth(),
                shape = AgapShape.md,
                label = { Text("Landmarks or immediate needs") },
                placeholder = { Text("For example, smoke near the bridge") },
                minLines = 3,
                maxLines = 5,
                enabled = canEdit
            )
        }
    }

    val delivery = if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) {
        "This is an offline test. Your report stays on this phone; nothing is sent to emergency services."
    } else when (uiState.transmissionMode) {
        TransmissionMode.FORCE_BLE_MESH -> "AGAP uses nearby devices to relay the report. Notes are unavailable in this mode."
        TransmissionMode.FORCE_INTERNET -> "AGAP sends the full report over the internet."
        TransmissionMode.AUTO -> "If internet delivery fails, AGAP can relay the essentials through nearby devices. Notes wait for internet."
    }
    ReportSection(if (bleOnly) "5" else "6", "How it’s delivered") {
        Text(delivery, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ReportSection(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            Box(
                Modifier.background(MaterialTheme.colorScheme.secondaryContainer, AgapShape.sm)
                    .padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

@Composable
private fun LocationStatus(uiState: ReportUiState) {
    val colors = MaterialTheme.colorScheme
    val (title, detail, accent) = when {
        uiState.isRefreshingLocation -> Triple(
            "Finding your location",
            "You can place a map pin while AGAP looks for a GPS fix.",
            AgapStatus.info
        )
        !uiState.hasConfirmedIncidentLocation -> Triple(
            "Location required",
            "Use GPS if you’re at the incident, or mark where help is needed on the map.",
            AgapStatus.warning
        )
        uiState.locationSource == LocationSource.MAP_PIN -> Triple(
            "Map pin confirmed",
            "Your report will use the location you marked.",
            AgapStatus.success
        )
        else -> Triple(
            "GPS location ready",
            uiState.accuracyMeters?.let { "Estimated accuracy: within ${it.toInt()} metres." }
                ?: "Your report will use your current GPS location.",
            AgapStatus.success
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.lg, backgroundColor = accent.copy(alpha = 0.07f), borderColor = accent.copy(alpha = 0.35f))
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(AgapSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        Icon(
            if (uiState.hasConfirmedIncidentLocation && !uiState.isRefreshingLocation) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.LocationOn
            },
            contentDescription = null,
            tint = accent
        )
        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmergencyTypeChoices(selectedType: String, enabled: Boolean, onSelected: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.3f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            reportCategories.chunked(columns).forEach { categories ->
                Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    categories.forEach { category ->
                        val selected = category.name.equals(selectedType, ignoreCase = true)
                        val background = if (selected) colors.primaryContainer else colors.surface
                        val foreground = if (selected) colors.onPrimaryContainer else colors.onSurface
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .glassCard(
                                    cornerShape = AgapShape.md,
                                    backgroundColor = background,
                                    borderColor = if (selected) colors.primary else colors.outlineVariant
                                )
                                .selectable(selected, enabled = enabled, role = Role.RadioButton) {
                                    onSelected(category.name)
                                }
                                .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            Box(
                                Modifier.size(36.dp).background(colors.surface, AgapShape.circle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(category.icon, null, tint = foreground)
                            }
                            Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = foreground)
                            if (selected) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = foreground, modifier = Modifier.size(AgapSpacing.lg))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ReportChoices(options: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelected: (T) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}
