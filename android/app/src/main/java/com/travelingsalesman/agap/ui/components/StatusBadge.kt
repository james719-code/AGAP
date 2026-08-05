package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = when (status.uppercase()) {
        "ACTIVE" -> Triple(RedPrimary, Color.White, RedPrimary)
        "DISPATCHED" -> Triple(AmberWarning, Color.Black, AmberWarning)
        "RESOLVED" -> Triple(GreenResolved, Color.White, GreenResolved)
        else -> Triple(Color(0xFF334155), Color.White, Color(0xFF475569))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}


