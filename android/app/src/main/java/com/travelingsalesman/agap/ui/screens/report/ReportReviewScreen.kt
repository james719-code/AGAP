package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportReviewScreen(
    uiState: ReportUiState,
    onSubmit: () -> Unit,
    onPrev: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text(
        text = "Step 5: Review & Submit Report",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderCol, AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Emergency Category: ${uiState.selectedType}",
                fontWeight = FontWeight.Bold,
                color = RedPrimary
            )
            Text(
                text = "Subject Relation: ${uiState.subjectRelation} (Immediate Danger: ${if (uiState.immediateDanger) "YES" else "NO"})",
                color = textColor
            )
            Text(
                text = "Location Source: ${uiState.locationSource.name}",
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            val coordText = if (uiState.latitude != null && uiState.longitude != null) {
                "%.5f, %.5f".format(uiState.latitude, uiState.longitude)
            } else "Location Unavailable (Unknown)"
            Text(
                text = "Coordinates: $coordText",
                color = subTextColor
            )
            uiState.barangayCode?.let { brgy ->
                Text(
                    text = "Barangay Reference: $brgy",
                    color = subTextColor
                )
            }
            Text(
                text = "Estimated People Affected: ${uiState.peopleAffected}",
                color = textColor
            )
            if (uiState.description.isNotBlank()) {
                Text(
                    text = "Details / Landmarks: ${uiState.description}",
                    color = textColor
                )
            }
        }
    }

    if (uiState.isSubmitting) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RedPrimary)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onPrev,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("BACK", color = textColor)
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("SUBMIT REPORT NOW", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
