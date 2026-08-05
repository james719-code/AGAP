package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateSurfaceVariant

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status.uppercase()) {
        "ACTIVE" -> RedPrimary.copy(alpha = 0.2f) to RedPrimary
        "DISPATCHED" -> AmberWarning.copy(alpha = 0.2f) to AmberWarning
        "RESOLVED" -> GreenResolved.copy(alpha = 0.2f) to GreenResolved
        else -> SlateSurfaceVariant to Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
