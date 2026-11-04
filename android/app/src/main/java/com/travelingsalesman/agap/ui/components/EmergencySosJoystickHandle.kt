package com.travelingsalesman.agap.ui.components

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.PaletteTextPrimaryLight
import java.util.Locale

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
    val handleContentColor = if (handleColor.luminance() > 0.18f) PaletteTextPrimaryLight else Color.White
    // Keep the decorative acronym inside the fixed thumb; instructions and category labels scale normally.
    val acronymSize = (25f / maxOf(1f, LocalDensity.current.fontScale / 1.5f)).sp
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

    Box(
        modifier = modifier
            .scale(scale)
            .size(112.dp),
        contentAlignment = Alignment.Center
    ) {
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
                .size(88.dp)
                .shadow(
                    elevation = if (isArmed) 4.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = handleColor.copy(alpha = 0.12f),
                    spotColor = handleColor.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(handleColor)
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
                            color = handleContentColor.copy(alpha = 0.88f),
                            fontSize = 10.sp,
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )

                        Text(
                            text = if (isPreview) "to sign in" else "to send",
                            color = handleContentColor.copy(alpha = 0.80f),
                            fontSize = 10.sp,
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
                            text = "Selected",
                            color = handleContentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )

                        Text(
                            text = candidate.displayName.uppercase(Locale.ROOT),
                            color = handleContentColor.copy(alpha = 0.78f),
                            fontSize = 10.sp,
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
                            color = handleContentColor.copy(alpha = 0.76f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }
        }
    }
}
