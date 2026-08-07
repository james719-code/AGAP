package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.travelingsalesman.agap.ui.theme.RedPrimary

import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive

@Composable
fun ReportIncidentDialog(
    emergencyTypes: List<EmergencyTypeOption> = defaultEmergencyTypeOptions(),
    onDismissRequest: () -> Unit,
    onSubmitReport: (type: String, description: String) -> Unit
) {
    val typeOptions = emergencyTypes.ifEmpty { defaultEmergencyTypeOptions() }
    var selectedType by remember(typeOptions) { mutableStateOf(typeOptions.firstOrNull()?.code ?: "MEDICAL") }
    var description by remember { mutableStateOf("") }
    var photoAttached by remember { mutableStateOf(false) }

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = AgapShape.xl,
            color = cardBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.sm)
                .border(width = 1.dp, color = borderCol, shape = AgapShape.xl)
        ) {
            Column(
                modifier = Modifier
                    .padding(AgapSpacing.xl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = RedPrimary
                    )
                    Text(
                        text = "REPORT DETAILED HAZARD",
                        color = textColor,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "Provide information to dispatch field teams accurately.",
                    color = subTextColor,
                    style = MaterialTheme.typography.bodySmall
                )

                // Category Selector
                Text(
                    text = "EMERGENCY CATEGORY",
                    color = RedPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.5.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeOptions.forEach { type ->
                        val isSelected = selectedType.equals(type.code, ignoreCase = true)
                        Card(
                            onClick = { selectedType = type.code },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background
                            ),
                            shape = AgapShape.sm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) RedPrimary else borderCol,
                                    shape = AgapShape.sm
                                )
                        ) {
                            Text(
                                text = type.displayName,
                                color = if (isSelected) RedPrimary else textColor,
                                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Incident Details / Location Landmarks", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("Describe the situation or specific location details...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = borderCol
                    ),
                    shape = AgapShape.sm,
                    maxLines = 4
                )

                // Photo Attachment Mock Button
                Card(
                    onClick = { photoAttached = !photoAttached },
                    colors = CardDefaults.cardColors(
                        containerColor = if (photoAttached) GreenActive.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background
                    ),
                    shape = AgapShape.md,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (photoAttached) GreenActive else borderCol,
                            shape = AgapShape.md
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(AgapSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = if (photoAttached) GreenActive else subTextColor
                        )
                        Text(
                            text = if (photoAttached) "Photo Attached (Incident_Capture.jpg)" else "Attach Incident Photo (Optional)",
                            color = if (photoAttached) GreenActive else subTextColor,
                            style = if (photoAttached) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.xs))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("CANCEL", color = subTextColor, style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.padding(horizontal = AgapSpacing.xs))

                    Button(
                        onClick = {
                            onSubmitReport(selectedType, description.ifBlank { "Reported via Citizen App" })
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = AgapShape.sm
                    ) {
                        Text("SUBMIT REPORT", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
