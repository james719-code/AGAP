package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.contentColorForAccent

@Composable
internal fun PremiumSosHandle(
    isLoading: Boolean,
    isDragging: Boolean,
    isArmed: Boolean,
    candidate: EmergencyTypeOption?,
    handleColor: Color,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val handleContentColor = contentColorForAccent(handleColor)
    // The thumb is deliberately fixed-size for the gesture. Its full instruction and
    // category name are repeated outside the dial at the user's selected text size.
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current.density
    val acronymSize = (25f / maxOf(1f, fontScale / 1.5f)).sp
    val controlLabelSize = (12f / maxOf(1f, fontScale / 1.25f)).sp
    val scale by animateFloatAsState(
        targetValue = when {
            isArmed -> 1.08f
            isDragging -> 1.03f
            else -> 1f
        },
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "handle_scale"
    )

    // Idle beacon: a slow expanding ring marks the control to use.
    val beacon = rememberInfiniteTransition(label = "sos_beacon")
    val beaconProgress by beacon.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "sos_beacon_progress"
    )
    val showBeacon = !isDragging && !isLoading

    Box(
        modifier = modifier
            .scale(scale)
            .size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showBeacon) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(1f + beaconProgress * 0.5f)
                    .border(2.dp, handleColor.copy(alpha = (1f - beaconProgress) * 0.55f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(
                    if (isArmed) 112.dp else 104.dp
                )
                .clip(CircleShape)
                .background(
                    handleColor.copy(
                        alpha = if (isArmed) 0.14f else 0.08f
                    )
                )
                .border(
                    width = 1.dp,
                    color = handleColor.copy(
                        alpha = if (isArmed) 0.55f else 0.28f
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(92.dp)
                .shadow(
                    elevation = if (isArmed) 14.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = handleColor.copy(alpha = 0.45f),
                    spotColor = handleColor.copy(alpha = 0.6f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(lerp(handleColor, Color.White, 0.18f), handleColor, lerp(handleColor, Color.Black, 0.22f)),
                        center = Offset(30f * density, 26f * density),
                        radius = 70f * density
                    )
                )
                .border(
                    width = 2.dp,
                    color = handleContentColor.copy(alpha = 0.24f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(27.dp),
                            color = handleContentColor,
                            strokeWidth = 3.dp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Sending",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )
                    }
                }

                isArmed && candidate != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = handleContentColor,
                            modifier = Modifier.size(19.dp)
                        )

                        Text(
                            text = "Release",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )

                        Text(
                            text = if (isPreview) "to sign in" else "to send",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )
                    }
                }

                isDragging && candidate != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = candidate.icon,
                            contentDescription = null,
                            tint = handleContentColor,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = "Keep",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )

                        Text(
                            text = "dragging",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SOS",
                            color = handleContentColor,
                            fontSize = acronymSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )

                        Text(
                            text = "Drag",
                            color = handleContentColor,
                            fontSize = controlLabelSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }
        }
    }
}
