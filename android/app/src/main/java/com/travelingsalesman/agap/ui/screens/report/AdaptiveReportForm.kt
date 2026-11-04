package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun AdaptiveReportForm(
    uiState: ReportUiState,
    onTypeSelected: (String) -> Unit,
    onSubjectChanged: (String, Boolean) -> Unit,
    onPeopleAffectedChanged: (Int) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenMapPicker: () -> Unit,
    onSend: () -> Unit
) {
    val canEdit = uiState.canEditDraft
    val bleOnly = uiState.transmissionMode == TransmissionMode.FORCE_BLE_MESH
    val modeTitle = if (bleOnly) "Offline relay" else if (uiState.transmissionMode == TransmissionMode.AUTO) "Automatic delivery" else "Internet delivery"
    val modeMessage = when {
        bleOnly -> "Compact report only. Nearby P2P is preferred with native BLE fallback for category, subject, danger and location."
        uiState.transmissionMode == TransmissionMode.AUTO -> "Core report can relay offline. Notes are delivered when internet is available."
        else -> "Full report details are sent directly to the command server."
    }

    ReportFormSection("Delivery") {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = AgapShape.md,
            color = if (bleOnly) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (bleOnly) BlueMedical.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                Modifier.padding(AgapSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Text(modeTitle, style = MaterialTheme.typography.labelLarge, color = if (bleOnly) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
                Text(modeMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    ReportFormSection("Emergency category") {
        EmergencyTypeChoices(
            selectedTypeCode = uiState.selectedType,
            enabled = canEdit,
            onTypeSelected = onTypeSelected
        )
    }
    ReportFormSection("Who needs help?") {
        StringChoices(
            options = listOf("SELF", "OTHER_PERSON", "PUBLIC_OR_UNKNOWN"),
            selected = uiState.subjectRelation,
            enabled = canEdit
        ) { onSubjectChanged(it, uiState.immediateDanger) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AgapShape.md,
            color = if (uiState.immediateDanger) RedPrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (uiState.immediateDanger) RedPrimary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AgapSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Immediate danger",
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.immediateDanger) RedPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Requires urgent dispatch attention",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.immediateDanger,
                    onCheckedChange = { onSubjectChanged(uiState.subjectRelation, it) },
                    enabled = canEdit
                )
            }
        }
    }
    ReportFormSection("Location") {
        LocationStatus(uiState)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            OutlinedButton(
                onClick = onRefreshLocation,
                modifier = Modifier.weight(1f),
                enabled = canEdit && !uiState.isRefreshingLocation
            ) {
                Icon(
                    imageVector = Icons.Outlined.GpsFixed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = AgapSpacing.xs)
                )
                Text(if (uiState.isRefreshingLocation) "Locating…" else "Refresh GPS")
            }
            OutlinedButton(
                onClick = onOpenMapPicker,
                modifier = Modifier.weight(1f),
                enabled = canEdit
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = AgapSpacing.xs)
                )
                Text("Choose on map")
            }
        }
    }
    ReportFormSection("People affected") {
        IntChoices(
            options = listOf(1 to "1", 3 to "2–5", 8 to "6–10", 11 to "11+"),
            selected = uiState.peopleAffected,
            enabled = canEdit,
            onSelected = onPeopleAffectedChanged
        )
    }
    if (!bleOnly) {
        ReportFormSection("Optional notes") {
            if (uiState.transmissionMode == TransmissionMode.AUTO) Text("Notes are saved and sent once internet is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = uiState.description, onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth().height(124.dp), shape = AgapShape.md,
                label = { Text("Landmarks or immediate needs") },
                placeholder = { Text("Blocked road, smoke visible, trapped person") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary),
                maxLines = 4,
                enabled = canEdit
            )
        }
    }
    uiState.submissionError?.let {
        SubmissionNotice(
            message = it,
            isSavedLocally = uiState.isReportSavedLocally
        )
    }
    Button(
        onClick = onSend, enabled = uiState.canSubmit,
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = AgapShape.lg,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) { Text(if (uiState.isSubmitting) "Sending report…" else "Send incident report", fontWeight = FontWeight.Black) }

    Spacer(modifier = Modifier.height(32.dp))
}

@Composable private fun ReportFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun LocationStatus(uiState: ReportUiState) {
    val (title, detail, accent) = when {
        uiState.isRefreshingLocation -> Triple(
            "Finding your location",
            "Looking for a current GPS fix. You can also place a map pin.",
            BlueMedical
        )

        !uiState.hasConfirmedIncidentLocation -> Triple(
            "Location required",
            "Refresh GPS or choose a map pin before sending this report.",
            AmberWarning
        )

        uiState.locationSource == com.travelingsalesman.agap.domain.model.LocationSource.MAP_PIN -> Triple(
            "Map pin selected",
            "Dispatch will use the location you confirmed on the map.",
            RedPrimary
        )

        else -> Triple(
            "GPS location ready",
            uiState.accuracyMeters?.let { "GPS accuracy: within ${it.toInt()} metres." }
                ?: "A current GPS location is attached to this report.",
            BlueMedical
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Black
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubmissionNotice(
    message: String,
    isSavedLocally: Boolean
) {
    val accent = if (isSavedLocally) AmberWarning else RedPrimary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Text(
                text = if (isSavedLocally) {
                    "REPORT SAVED — DELIVERY PENDING"
                } else {
                    "CHECK REPORT BEFORE SENDING"
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Black
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isSavedLocally) {
                    "AGAP will retry delivery automatically. Do not send this report again."
                } else {
                    "Update the missing or invalid detail, then try sending again."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmergencyTypeChoices(
    selectedTypeCode: String,
    enabled: Boolean,
    onTypeSelected: (String) -> Unit
) {
    val options = remember {
        val order = listOf("MEDICAL", "FIRE", "ACCIDENT", "DISASTER", "CRIME")
        defaultEmergencyTypeOptions()
            .sortedBy { opt -> order.indexOf(opt.code.uppercase()).let { if (it < 0) Int.MAX_VALUE else it } }
            .take(5)
    }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                row.forEach { type ->
                    val isSelected = selectedTypeCode.equals(type.code, ignoreCase = true)
                    val accent = type.iconTintColor()
                    Card(
                        onClick = { onTypeSelected(type.code) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).semantics { selected = isSelected },
                        shape = AgapShape.md,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accent.copy(alpha = 0.12f) else colors.surface
                        ),
                        border = BorderStroke(1.dp, if (isSelected) accent else colors.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .background(accent.copy(alpha = 0.12f), AgapShape.md),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(type.icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                            Text(type.displayName, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StringChoices(
    options: List<String>,
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    val labels = mapOf(
        "SELF" to "Self",
        "OTHER_PERSON" to "Other person",
        "PUBLIC_OR_UNKNOWN" to "Public or unknown"
    )

    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        options.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                row.forEach { option ->
                    val isSelected = selected.equals(option, true)
                    val displayLabel = labels[option] ?: option.replace('_', ' ')
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelected(option) },
                        label = { Text(displayLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = enabled,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun IntChoices(
    options: List<Pair<Int, String>>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(value) },
                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = enabled,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
