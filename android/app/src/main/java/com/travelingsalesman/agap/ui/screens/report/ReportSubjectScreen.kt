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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
fun ReportSubjectScreen(
    subjectRelation: String,
    immediateDanger: Boolean,
    onSubjectChanged: (relation: String, danger: Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text(
        text = "Step 2: Who Needs Assistance?",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
    Text(
        text = "Identify whether this emergency concerns yourself, another victim, or a public hazard.",
        style = MaterialTheme.typography.bodyMedium,
        color = subTextColor
    )

    val subjects = listOf(
        "SELF" to "Myself (Self-Report)",
        "OTHER_PERSON" to "Another Person in Need",
        "PUBLIC_OR_UNKNOWN" to "Public Hazard / Unknown Victim"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        subjects.forEach { (code, label) ->
            val isSelected = subjectRelation.equals(code, ignoreCase = true)
            Card(
                onClick = { onSubjectChanged(code, immediateDanger) },
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
                Text(
                    text = label,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) RedPrimary else textColor
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = immediateDanger,
            onCheckedChange = { onSubjectChanged(subjectRelation, it) },
            colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
        )
        Text(
            text = "IMMEDIATE DANGER INDICATED",
            fontWeight = FontWeight.Bold,
            color = RedPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )
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
            Text("NEXT: LOCATION", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
