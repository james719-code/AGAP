package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.GradientPrimaryRed
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun SosButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale2"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ambient glow ring
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulseScale2)
                .clip(CircleShape)
                .background(RedPrimary.copy(alpha = 0.12f))
        )

        // Middle animated pulse halo
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(pulseScale1)
                .clip(CircleShape)
                .background(RedPrimary.copy(alpha = 0.25f))
                .border(width = 1.5.dp, color = RedPrimary.copy(alpha = 0.4f), shape = CircleShape)
        )

        // Inner tactile button core
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(CircleShape)
                .background(GradientPrimaryRed)
                .border(width = 2.dp, color = Color.White.copy(alpha = 0.3f), shape = CircleShape)
                .clickable(enabled = !isLoading, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isLoading) "SENDING" else "SOS",
                    color = Color.White,
                    fontSize = if (isLoading) 20.sp else 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                if (!isLoading) {
                    Text(
                        text = "HOLD / TAP",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

