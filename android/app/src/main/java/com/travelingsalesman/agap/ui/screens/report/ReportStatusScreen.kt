package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportStatusScreen(
    submissionResult: String?,
    submissionError: String?,
    onDone: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (submissionError != null) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = RedPrimary,
                modifier = Modifier.height(64.dp)
            )
            Text(
                text = "Report Submission Issue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RedPrimary
            )
            Text(
                text = submissionError,
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor
            )
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = GreenActive,
                modifier = Modifier.height(64.dp)
            )
            Text(
                text = "Incident Report Submitted",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GreenActive
            )
            Text(
                text = submissionResult ?: "Your detailed incident report has been submitted to dispatch.",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
        ) {
            Text("RETURN TO HOME", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
