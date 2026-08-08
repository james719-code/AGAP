package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.components.EmergencyTypeOption
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportTypeScreen(
    selectedType: String,
    emergencyTypes: List<EmergencyTypeOption> = defaultEmergencyTypeOptions(),
    onSelectType: (String) -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text(
        text = "Step 1: Select Emergency Category",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
    Text(
        text = "Choose the category that best describes the incident to dispatch appropriate response agencies.",
        style = MaterialTheme.typography.bodyMedium,
        color = subTextColor
    )

    val options = emergencyTypes.ifEmpty { defaultEmergencyTypeOptions() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSelected = selectedType.equals(opt.code, ignoreCase = true)
            Card(
                onClick = { onSelectType(opt.code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                shape = AgapShape.md,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) RedPrimary else borderCol,
                        shape = AgapShape.md
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = opt.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) RedPrimary else textColor
                    )
                }
            }
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
    ) {
        Text("NEXT: SUBJECT & DANGER", fontWeight = FontWeight.Bold, color = Color.White)
    }
}
