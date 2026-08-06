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

@Composable
fun ReportIncidentDialog(
    onDismissRequest: () -> Unit,
    onSubmitReport: (type: String, description: String) -> Unit
) {
    var selectedType by remember { mutableStateOf("MEDICAL") }
    var description by remember { mutableStateOf("") }
    var photoAttached by remember { mutableStateOf(false) }

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val types = listOf(
        "MEDICAL" to "Medical Emergency",
        "FIRE" to "Fire Incident",
        "CRIME" to "Police / Crime",
        "DISASTER" to "Flood / Disaster",
        "HAZARD" to "Infrastructure Hazard"
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = RedPrimary
                    )
                    Text(
                        text = "REPORT DETAILED HAZARD",
                        color = textColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = "Provide information to dispatch field teams accurately.",
                    color = subTextColor,
                    fontSize = 12.sp
                )

                // Category Selector
                Text(
                    text = "EMERGENCY CATEGORY",
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (typeKey, typeLabel) ->
                        val isSelected = selectedType == typeKey
                        Card(
                            onClick = { selectedType = typeKey },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) RedPrimary else borderCol,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Text(
                                text = typeLabel,
                                color = if (isSelected) RedPrimary else textColor,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Incident Details / Location Landmarks", fontSize = 12.sp) },
                    placeholder = { Text("Describe the situation or specific location details...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = borderCol
                    ),
                    maxLines = 4
                )

                // Photo Attachment Mock Button
                Card(
                    onClick = { photoAttached = !photoAttached },
                    colors = CardDefaults.cardColors(
                        containerColor = if (photoAttached) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (photoAttached) Color(0xFF10B981) else borderCol,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = if (photoAttached) Color(0xFF10B981) else subTextColor
                        )
                        Text(
                            text = if (photoAttached) "Photo Attached (Incident_Capture.jpg)" else "Attach Incident Photo (Optional)",
                            color = if (photoAttached) Color(0xFF10B981) else subTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (photoAttached) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("CANCEL", color = subTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                    Button(
                        onClick = {
                            onSubmitReport(selectedType, description.ifBlank { "Reported via Citizen App" })
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SUBMIT REPORT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
