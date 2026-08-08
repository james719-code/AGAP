package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun ReportDetailsScreen(
    description: String,
    peopleAffected: Int,
    onDescriptionChanged: (String) -> Unit,
    onPeopleCountChanged: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text(
        text = "Step 4: Additional Details",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
    Text(
        text = "Provide situation landmarks or specific details to guide emergency dispatchers.",
        style = MaterialTheme.typography.bodyMedium,
        color = subTextColor
    )

    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChanged,
        label = { Text("Landmarks / Incident Description") },
        placeholder = { Text("Describe location landmarks, hazards, or immediate needs...") },
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = AgapShape.sm,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RedPrimary,
            unfocusedBorderColor = borderCol
        ),
        maxLines = 5
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "ESTIMATED PEOPLE AFFECTED / INVOLVED",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = subTextColor
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onPeopleCountChanged((peopleAffected - 1).coerceAtLeast(1)) },
                shape = AgapShape.sm
            ) {
                Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "$peopleAffected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = RedPrimary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            OutlinedButton(
                onClick = { onPeopleCountChanged(peopleAffected + 1) },
                shape = AgapShape.sm
            ) {
                Text("+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

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
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
        ) {
            Text("REVIEW REPORT", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
