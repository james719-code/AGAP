package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val modeTitle = if (bleOnly) "OFFLINE RELAY MODE" else if (uiState.transmissionMode == TransmissionMode.AUTO) "AUTO DELIVERY" else "INTERNET DIRECT"
    val modeMessage = when {
        bleOnly -> "Compact report only. Nearby P2P is preferred with native BLE fallback for category, subject, danger and location."
        uiState.transmissionMode == TransmissionMode.AUTO -> "Core report can relay offline. Notes are delivered when internet is available."
        else -> "Full report details are sent directly to the command server."
    }

    ReportFormSection("Delivery") {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = AgapShape.md,
            color = if (bleOnly) BlueMedical.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, if (bleOnly) BlueMedical.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                Modifier.padding(AgapSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Text(modeTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (bleOnly) BlueMedical else RedPrimary)
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
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Text(if (uiState.isRefreshingLocation) "LOCATING…" else "REFRESH GPS")
            }
            OutlinedButton(
                onClick = onOpenMapPicker,
                modifier = Modifier.weight(1f),
                enabled = canEdit
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Text("CHOOSE MAP")
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
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = AgapShape.md,
        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
    ) { Text(if (uiState.isSubmitting) "SENDING REPORT…" else "SEND INCIDENT REPORT", fontWeight = FontWeight.Black) }

    Spacer(modifier = Modifier.height(32.dp))
}

@Composable private fun ReportFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = RedPrimary)
        content()
    }
}

@Composable
private fun LocationStatus(uiState: ReportUiState) {
    val (title, detail, accent) = when {
        uiState.isRefreshingLocation -> Triple(
            "LOCATING DEVICE",
            "Looking for a current GPS fix. You can also place a map pin.",
            BlueMedical
        )

        !uiState.hasConfirmedIncidentLocation -> Triple(
            "LOCATION REQUIRED",
            "Refresh GPS or choose a map pin before sending this report.",
            AmberWarning
        )

        uiState.locationSource == com.travelingsalesman.agap.domain.model.LocationSource.MAP_PIN -> Triple(
            "MAP PIN SELECTED",
            "Dispatch will use the location you confirmed on the map.",
            RedPrimary
        )

        else -> Triple(
            "GPS LOCATION READY",
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
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridItems = options.take(4)
    val lastItem = options.getOrNull(4)

    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        gridItems.chunked(2).forEach { row ->
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
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                accent.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = AgapShape.md,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                },
                                shape = AgapShape.md
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AgapSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (isSelected) accent.copy(alpha = 0.25f) else accent.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = type.displayName,
                                color = if (isSelected) accent else textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        lastItem?.let { type ->
            val isSelected = selectedTypeCode.equals(type.code, ignoreCase = true)
            val accent = type.iconTintColor()
            Card(
                onClick = { onTypeSelected(type.code) },
                enabled = enabled,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        accent.copy(alpha = 0.20f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = AgapShape.md,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            accent
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        },
                        shape = AgapShape.md
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgapSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isSelected) accent.copy(alpha = 0.25f) else accent.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = type.displayName,
                        color = if (isSelected) accent else textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
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
        "OTHER_PERSON" to "Other Person",
        "PUBLIC_OR_UNKNOWN" to "Public / Unknown"
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
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = enabled,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            selectedBorderColor = RedPrimary
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
                    selectedContainerColor = RedPrimary,
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = enabled,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = RedPrimary
                )
            )
        }
    }
}
