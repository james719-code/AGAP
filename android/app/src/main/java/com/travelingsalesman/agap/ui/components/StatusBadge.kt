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
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = when (status.uppercase()) {
        "ACTIVE", "CRITICAL" -> Triple(RedPrimary.copy(alpha = 0.15f), RedPrimary, RedPrimary.copy(alpha = 0.4f))
        "DISPATCHED" -> Triple(AmberWarning.copy(alpha = 0.15f), AmberWarning, AmberWarning.copy(alpha = 0.4f))
        "RESOLVED" -> Triple(GreenResolved.copy(alpha = 0.15f), GreenResolved, GreenResolved.copy(alpha = 0.4f))
        else -> Triple(Color(0xFF26292C), Color(0xFF9C9994), Color(0xFF3F4448))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(9999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status.uppercase() == "ACTIVE" || status.uppercase() == "CRITICAL") {
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}



