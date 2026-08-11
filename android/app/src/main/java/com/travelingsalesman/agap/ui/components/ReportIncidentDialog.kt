package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportIncidentDialog(
    emergencyTypes: List<EmergencyTypeOption> = defaultEmergencyTypeOptions(),
    onDismissRequest: () -> Unit,
    onStartDetailedReport: (typeCode: String) -> Unit
) {
    val typeOptions = emergencyTypes.ifEmpty { defaultEmergencyTypeOptions() }
    val categoryOptions = remember(typeOptions) {
        val order = listOf("MEDICAL", "FIRE", "ACCIDENT", "DISASTER", "CRIME")
        typeOptions
            .sortedBy { option -> order.indexOf(option.code.uppercase()).let { if (it < 0) Int.MAX_VALUE else it } }
            .take(5)
    }
    var selectedType by remember(typeOptions) { mutableStateOf(typeOptions.firstOrNull()?.code ?: "MEDICAL") }

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
                        imageVector = Icons.Outlined.Warning,
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
                    text = "Select an emergency category to launch the guided detailed reporting workflow.",
                    color = subTextColor,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "EMERGENCY CATEGORY",
                    color = RedPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryOptions.forEach { type ->
                        val isSelected = selectedType.equals(type.code, ignoreCase = true)
                        val accent = type.iconTintColor()
                        Card(
                            onClick = { selectedType = type.code },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    accent.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                }
                            ),
                            shape = AgapShape.sm,
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accent else accent.copy(alpha = 0.34f),
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

                Spacer(modifier = Modifier.height(AgapSpacing.xs))

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
                            onStartDetailedReport(selectedType)
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = AgapShape.sm
                    ) {
                        Text("LAUNCH REPORT WORKFLOW", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
