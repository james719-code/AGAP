package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.components.EmergencyTypeOption
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
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(modeTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (bleOnly) BlueMedical else RedPrimary)
                Text(modeMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    ReportFormSection("Emergency category") {
        EmergencyTypeChoices(uiState.selectedType, onTypeSelected)
    }
    ReportFormSection("Who needs help?") {
        StringChoices(listOf("SELF", "OTHER_PERSON", "PUBLIC_OR_UNKNOWN"), uiState.subjectRelation) {
            onSubjectChanged(it, uiState.immediateDanger)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Immediate danger", fontWeight = FontWeight.Bold)
                Text("Requires urgent dispatch attention", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(uiState.immediateDanger, { onSubjectChanged(uiState.subjectRelation, it) })
        }
    }
    ReportFormSection("Location") {
        Text(if (uiState.latitude != null) "Current location ready" else "Location is still being acquired", style = MaterialTheme.typography.bodySmall, color = if (uiState.latitude != null) BlueMedical else AmberWarning)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onRefreshLocation, Modifier.weight(1f)) { Text("REFRESH GPS") }
            OutlinedButton(onOpenMapPicker, Modifier.weight(1f)) { Text("CHOOSE MAP") }
        }
    }
    ReportFormSection("People affected") {
        IntChoices(listOf(1 to "1", 3 to "2–5", 8 to "6–10", 11 to "11+"), uiState.peopleAffected, onPeopleAffectedChanged)
    }
    if (!bleOnly) {
        ReportFormSection("Optional notes") {
            if (uiState.transmissionMode == TransmissionMode.AUTO) Text("Notes are saved and sent once internet is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = uiState.description, onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth().height(124.dp), shape = AgapShape.md,
                label = { Text("Landmarks or immediate needs") },
                placeholder = { Text("Blocked road, smoke visible, trapped person") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary), maxLines = 4
            )
        }
    }
    Button(
        onClick = onSend, enabled = !uiState.isSubmitting,
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = AgapShape.md,
        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
    ) { Text(if (uiState.isSubmitting) "SENDING REPORT…" else "SEND INCIDENT REPORT", fontWeight = FontWeight.Black) }
}

@Composable private fun ReportFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = RedPrimary)
        content()
    }
}

@Composable
private fun EmergencyTypeChoices(
    selectedTypeCode: String,
    onTypeSelected: (String) -> Unit
) {
    val options = remember {
        val order = listOf("MEDICAL", "FIRE", "ACCIDENT", "DISASTER", "CRIME")
        defaultEmergencyTypeOptions()
            .sortedBy { opt -> order.indexOf(opt.code.uppercase()).let { if (it < 0) Int.MAX_VALUE else it } }
            .take(5)
    }
    val textColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { type ->
            val isSelected = selectedTypeCode.equals(type.code, ignoreCase = true)
            val accent = type.iconTintColor()
            Card(
                onClick = { onTypeSelected(type.code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        accent.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = AgapShape.sm,
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = AgapShape.sm
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(accent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = type.displayName,
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        text = type.displayName.uppercase(),
                        color = if (isSelected) accent else textColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable private fun StringChoices(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { option -> FilterChip(selected.equals(option, true), { onSelected(option) }, { Text(option.replace('_', ' ')) }, Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RedPrimary.copy(alpha = .14f), selectedLabelColor = RedPrimary)) }
        } }
    }
}

@Composable private fun IntChoices(options: List<Pair<Int, String>>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) -> FilterChip(selected == value, { onSelected(value) }, { Text(label) }, Modifier.weight(1f)) }
    }
}
