package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.util.Locale

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val normalized = status.trim().uppercase(Locale.ROOT)
    val fallbackBg = MaterialTheme.colorScheme.surfaceVariant
    val fallbackText = MaterialTheme.colorScheme.onSurfaceVariant

    val isUrgent = normalized in setOf("CRITICAL", "RECEIVED", "ACKNOWLEDGED")
    val isDispatched = normalized in setOf("DISPATCHED", "EN_ROUTE", "ON_SCENE", "IDLE")
    val isResolved = normalized == "RESOLVED"

    val (backgroundColor, textColor, borderColor) = when {
        isUrgent -> Triple(
            RedPrimary.copy(alpha = 0.12f),
            RedPrimary,
            RedPrimary.copy(alpha = 0.35f)
        )
        isDispatched -> Triple(
            AmberWarning.copy(alpha = 0.12f),
            AmberWarning,
            AmberWarning.copy(alpha = 0.35f)
        )
        isResolved -> Triple(
            GreenResolved.copy(alpha = 0.12f),
            GreenResolved,
            GreenResolved.copy(alpha = 0.35f)
        )
        else -> Triple(
            fallbackBg.copy(alpha = 0.50f),
            fallbackText,
            fallbackText.copy(alpha = 0.25f)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_badge_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 0.30f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .clip(AgapShape.pill)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = AgapShape.pill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isUrgent || isResolved || isDispatched) {
                val dotColor = when {
                    isUrgent -> RedPrimary
                    isDispatched -> AmberWarning
                    else -> GreenResolved
                }
                Box(
                    modifier = Modifier
                        .scale(if (isUrgent) pulseScale else 1f)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = if (isUrgent) pulseAlpha else 1f))
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = normalized.replace('_', ' '),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}



