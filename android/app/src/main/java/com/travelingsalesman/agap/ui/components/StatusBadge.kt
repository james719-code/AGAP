package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val fallbackBg = MaterialTheme.colorScheme.surfaceVariant
    val fallbackText = MaterialTheme.colorScheme.onSurfaceVariant

    val (backgroundColor, textColor, borderColor) = when (status.uppercase()) {
        "RECEIVED", "ACKNOWLEDGED", "CRITICAL" -> Triple(RedPrimary.copy(alpha = 0.15f), RedPrimary, RedPrimary.copy(alpha = 0.4f))
        "DISPATCHED", "EN_ROUTE", "ON_SCENE" -> Triple(AmberWarning.copy(alpha = 0.15f), AmberWarning, AmberWarning.copy(alpha = 0.4f))
        "RESOLVED" -> Triple(GreenResolved.copy(alpha = 0.15f), GreenResolved, GreenResolved.copy(alpha = 0.4f))
        else -> Triple(fallbackBg, fallbackText, fallbackText.copy(alpha = 0.4f))
    }

    Box(
        modifier = modifier
            .clip(AgapShape.pill)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = AgapShape.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status.uppercase() == "RECEIVED" || status.uppercase() == "ACKNOWLEDGED" || status.uppercase() == "CRITICAL") {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RedPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (status.uppercase() == "RESOLVED") {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(GreenResolved)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = status.uppercase(),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.5.sp
            )
        }
    }
}



