package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.R
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.util.Locale

@Composable
internal fun JoystickHeader(
    isLoading: Boolean,
    isDragging: Boolean,
    candidate: EmergencyTypeOption?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(AgapShape.md)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.agap_splash_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column {
                Text(
                    text = "SOS COMMAND CONTROL",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.65.sp
                )

                Text(
                    text = "Drag toward an emergency type",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        val label = when {
            isLoading -> "SENDING"
            isDragging && candidate != null -> "LOCKED"
            isDragging -> "SELECT"
            else -> "SAFE"
        }

        val color = when {
            isLoading -> GreenOnline
            isDragging && candidate != null -> candidate.iconTintColor()
            isDragging -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> GreenOnline
        }

        Surface(
            shape = AgapShape.pill,
            color = color.copy(alpha = 0.10f),
            border = BorderStroke(
                1.dp,
                color.copy(alpha = 0.34f)
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 5.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = label,
                    color = color,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp
                )
            }
        }
    }
}

@Composable
internal fun PremiumEmergencyTarget(
    option: EmergencyTypeOption,
    isCandidate: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = option.iconTintColor()

    val targetScale by animateFloatAsState(
        targetValue = when {
            isCandidate -> 1.12f
            isSelected -> 1.04f
            else -> 1f
        },
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "target_scale"
    )

    Column(
        modifier = modifier
            .scale(targetScale)
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(
                    if (isCandidate) 43.dp else 39.dp
                )
                .shadow(
                    elevation = if (isCandidate) 9.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = accent.copy(alpha = 0.30f),
                    spotColor = accent.copy(alpha = 0.30f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.25f),
                            accent.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(
                    width = if (isCandidate) 2.dp else 1.dp,
                    color = when {
                        isCandidate -> accent
                        isSelected -> accent.copy(alpha = 0.62f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.displayName,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = option.displayName.uppercase(Locale.ROOT),
            color = when {
                isCandidate -> accent
                isSelected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = TextAlign.Center,
            letterSpacing = 0.35.sp
        )

        if (isCandidate) {
            Text(
                text = "LOCKED",
                color = accent,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.55.sp
            )
        } else if (isSelected) {
            Text(
                text = "READY",
                color = accent.copy(alpha = 0.85f),
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
internal fun DragInstruction(
    candidate: EmergencyTypeOption?,
    isArmed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = when {
            isArmed -> accent.copy(alpha = 0.13f)
            candidate != null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        },
        border = BorderStroke(
            1.dp,
            when {
                isArmed -> accent.copy(alpha = 0.52f)
                candidate != null -> accent.copy(alpha = 0.24f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = when {
                isArmed && candidate != null ->
                    "RELEASE NOW • SEND ${candidate.displayName.uppercase(Locale.ROOT)} SOS"
                candidate != null ->
                    "KEEP DRAGGING • ARM ${candidate.displayName.uppercase(Locale.ROOT)} SOS"
                else ->
                    "MOVE TOWARD AN EMERGENCY TYPE"
            },
            color = if (isArmed) {
                accent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.55.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp
            )
        )
    }
}

@Composable
internal fun JoystickSafetyFooter(
    selected: EmergencyTypeOption?,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.lg)
            .background(
                if (isLoading) {
                    GreenOnline.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isLoading) {
                    GreenOnline.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = AgapShape.lg
            )
            .padding(
                horizontal = 11.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isLoading) {
                        GreenOnline.copy(alpha = 0.12f)
                    } else {
                        Color.White
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = GreenOnline,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.agap_splash_mark),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(9.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (isLoading) {
                    "TRANSMITTING EMERGENCY ALERT"
                } else {
                    "ACCIDENTAL-SEND PROTECTION"
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.45.sp
            )

            Text(
                text = if (isLoading) {
                    "Keep AGAP open while the alert is handed to the emergency network."
                } else {
                    "Taps only preview. A full drag across the outer ring and release is required to send."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.5.sp,
                lineHeight = 12.sp
            )
        }
    }
}
