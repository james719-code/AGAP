package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.SosOverlayBottom
import com.travelingsalesman.agap.ui.theme.SosOverlayOnWhite
import com.travelingsalesman.agap.ui.theme.SosOverlayTop

/**
 * Full-screen cancel window shown after the SOS control is released.
 *
 * Design notes (see Android Emergency SOS): a short, clearly counted delay with
 * one obvious escape. Cancel is the largest control; Back also cancels. The
 * screen is modal so the person cannot navigate away while it is counting.
 */
@Composable
fun SosCountdownOverlay(
    state: SosCountdownUiState,
    hapticsEnabled: Boolean,
    onCancel: () -> Unit,
    onSendNow: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.secondsRemaining) {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val progress by animateFloatAsState(state.progress, animationSpec = tween(120), label = "sos_countdown_progress")

    Dialog(
        onDismissRequest = onCancel, // system Back cancels
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(SosOverlayTop, SosOverlayBottom))
                )
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = AgapSpacing.xxl, vertical = AgapSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sending ${state.emergencyLabel} SOS",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.size(AgapSpacing.sm))
                Text(
                    text = "Tap Cancel if this was a mistake.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.size(AgapSpacing.section + AgapSpacing.lg))

                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.White.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke)
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - progress),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = state.secondsRemaining.toString(),
                        color = Color.White,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "Sending in ${state.secondsRemaining} seconds"
                        }
                    )
                }

                Spacer(Modifier.size(AgapSpacing.section))

                Box(
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f), AgapShape.pill)
                        .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm)
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
                    ) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            "Your location will be shared with responders",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(Modifier.size(AgapSpacing.section + AgapSpacing.lg))

                // Cancel is the dominant control: biggest, highest contrast, first in focus order.
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = AgapShape.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SosOverlayOnWhite)
                ) {
                    Icon(Icons.Outlined.Close, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(AgapSpacing.sm))
                    Text("Cancel", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.size(AgapSpacing.md))
                OutlinedButton(
                    onClick = onSendNow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = AgapShape.pill,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Send now", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
