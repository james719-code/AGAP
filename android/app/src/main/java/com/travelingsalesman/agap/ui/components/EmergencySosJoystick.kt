package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.travelingsalesman.agap.R
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Premium radial SOS selector.
 *
 * Safety model:
 * 1. Tapping a target only previews/selects it.
 * 2. Dragging the center handle chooses a target direction.
 * 3. The target locks after the selection threshold.
 * 4. The alert is armed only after the user crosses the outer dispatch ring.
 * 5. Releasing while armed sends exactly that emergency code.
 * 6. Releasing early or cancelling sends nothing.
 */
@Composable
fun EmergencySosJoystick(
    emergencyTypes: List<EmergencyTypeOption>,
    selectedTypeCode: String,
    onTypeSelected: (String) -> Unit,
    onEmergencyTriggered: (String) -> Unit,
    isLoading: Boolean,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val options = remember(emergencyTypes) {
        orderEmergencyTypes(
            emergencyTypes.ifEmpty {
                defaultEmergencyTypeOptions()
            }
        ).take(6)
    }

    val haptics = LocalHapticFeedback.current
    fun performAlertHaptic() {
        if (hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val density = LocalDensity.current

    var dragOffset by remember {
        mutableStateOf(Offset.Zero)
    }

    var isDragging by remember {
        mutableStateOf(false)
    }

    var candidateIndex by remember {
        mutableIntStateOf(-1)
    }

    var armedHapticPlayed by remember {
        mutableStateOf(false)
    }

    val selectedIndex = options.indexOfFirst {
        it.code.equals(
            selectedTypeCode,
            ignoreCase = true
        )
    }

    val candidate = options.getOrNull(candidateIndex)
    val preview = candidate ?: options.getOrNull(selectedIndex)

    val previewColor = preview?.iconTintColor() ?: RedPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.xxl)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = AgapShape.xxl
            )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 14.dp
            )
        ) {
            JoystickHeader(
                isLoading = isLoading,
                isDragging = isDragging,
                candidate = candidate
            )

            Spacer(modifier = Modifier.height(6.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.04f)
            ) {
                val containerPx = constraints.maxWidth.toFloat()

                val targetWidthPx = with(density) {
                    80.dp.toPx()
                }

                val marginPx = with(density) {
                    7.dp.toPx()
                }

                val preferredRingPx = with(density) {
                    121.dp.toPx()
                }

                val minimumRingPx = with(density) {
                    91.dp.toPx()
                }

                val ringRadiusPx = minOf(
                    preferredRingPx,
                    (containerPx / 2f) -
                            (targetWidthPx / 2f) -
                            marginPx
                ).coerceAtLeast(minimumRingPx)

                val maxDragRadiusPx =
                    (ringRadiusPx - with(density) {
                        38.dp.toPx()
                    }).coerceAtLeast(
                        with(density) {
                            54.dp.toPx()
                        }
                    )

                val selectionThresholdPx =
                    maxDragRadiusPx * 0.34f

                val dispatchThresholdPx =
                    maxDragRadiusPx * 0.82f

                val distance =
                    offsetMagnitude(dragOffset)

                val isArmed =
                    isDragging &&
                            candidate != null &&
                            distance >= dispatchThresholdPx

                val animatedX by animateFloatAsState(
                    targetValue = if (isDragging) {
                        dragOffset.x
                    } else {
                        0f
                    },
                    animationSpec = if (isDragging) {
                        snap()
                    } else {
                        spring(
                            dampingRatio =
                                Spring.DampingRatioMediumBouncy,
                            stiffness =
                                Spring.StiffnessMediumLow
                        )
                    },
                    label = "premium_sos_x"
                )

                val animatedY by animateFloatAsState(
                    targetValue = if (isDragging) {
                        dragOffset.y
                    } else {
                        0f
                    },
                    animationSpec = if (isDragging) {
                        snap()
                    } else {
                        spring(
                            dampingRatio =
                                Spring.DampingRatioMediumBouncy,
                            stiffness =
                                Spring.StiffnessMediumLow
                        )
                    },
                    label = "premium_sos_y"
                )

                val handleColor by animateColorAsState(
                    targetValue = when {
                        isArmed && candidate != null ->
                            candidate.iconTintColor()

                        candidate != null ->
                            candidate.iconTintColor()

                        else ->
                            RedPrimary
                    },
                    label = "premium_sos_color"
                )

                val optionColors =
                    options.map {
                        it.iconTintColor()
                    }

                // MaterialTheme values must be captured in the composable scope,
                // not read from inside the Canvas DrawScope.
                val canvasSurfaceVariant =
                    MaterialTheme.colorScheme.surfaceVariant

                val canvasOnSurfaceVariant =
                    MaterialTheme.colorScheme.onSurfaceVariant

                val centerX =
                    containerPx / 2f

                val centerY =
                    containerPx / 2f

                val step =
                    (2.0 * PI) /
                            options.size.coerceAtLeast(1)

                Canvas(
                    modifier = Modifier.matchParentSize()
                ) {
                    val center = Offset(
                        size.width / 2f,
                        size.height / 2f
                    )

                    // Soft ambient command zone.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                previewColor.copy(alpha = 0.10f),
                                RedPrimary.copy(alpha = 0.035f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ringRadiusPx + 52.dp.toPx()
                        ),
                        radius = ringRadiusPx + 52.dp.toPx(),
                        center = center
                    )

                    // Outer command ring.
                    drawCircle(
                        color = canvasSurfaceVariant
                            .copy(alpha = 0.78f),
                        radius = ringRadiusPx - 8.dp.toPx(),
                        center = center,
                        style = Stroke(
                            width = 1.dp.toPx()
                        )
                    )

                    // Dispatch threshold ring.
                    drawCircle(
                        color = if (isArmed) {
                            previewColor.copy(alpha = 0.72f)
                        } else {
                            canvasOnSurfaceVariant
                                .copy(alpha = 0.20f)
                        },
                        radius = dispatchThresholdPx,
                        center = center,
                        style = Stroke(
                            width = if (isArmed) {
                                2.dp.toPx()
                            } else {
                                1.dp.toPx()
                            },
                            pathEffect =
                                PathEffect.dashPathEffect(
                                    floatArrayOf(
                                        7.dp.toPx(),
                                        7.dp.toPx()
                                    )
                                )
                        )
                    )

                    options.forEachIndexed {
                            index,
                            _ ->

                        val angle =
                            (-PI / 2.0) +
                                    (index * step)

                        val lineEndRadius =
                            ringRadiusPx -
                                    39.dp.toPx()

                        val end = Offset(
                            x = center.x +
                                    lineEndRadius *
                                    cos(angle).toFloat(),
                            y = center.y +
                                    lineEndRadius *
                                    sin(angle).toFloat()
                        )

                        val isCandidateLane =
                            index == candidateIndex

                        val isSelectedLane =
                            !isDragging &&
                                    index == selectedIndex

                        val laneColor =
                            optionColors[index]

                        drawLine(
                            color = when {
                                isCandidateLane ->
                                    laneColor.copy(
                                        alpha = 0.88f
                                    )

                                isSelectedLane ->
                                    laneColor.copy(
                                        alpha = 0.30f
                                    )

                                else ->
                                    canvasSurfaceVariant
                                        .copy(alpha = 0.65f)
                            },
                            start = center,
                            end = end,
                            strokeWidth = when {
                                isCandidateLane ->
                                    3.dp.toPx()

                                isSelectedLane ->
                                    1.5.dp.toPx()

                                else ->
                                    1.dp.toPx()
                            }
                        )

                        drawCircle(
                            color = laneColor.copy(
                                alpha = if (
                                    isCandidateLane
                                ) {
                                    0.90f
                                } else {
                                    0.24f
                                }
                            ),
                            radius = if (
                                isCandidateLane
                            ) {
                                4.dp.toPx()
                            } else {
                                2.5.dp.toPx()
                            },
                            center = end
                        )
                    }

                    // Handle origin marker.
                    drawCircle(
                        color = canvasSurfaceVariant,
                        radius = 57.dp.toPx(),
                        center = center,
                        style = Stroke(
                            width = 1.dp.toPx()
                        )
                    )

                    if (isDragging) {
                        val thumbCenter = Offset(
                            center.x + animatedX,
                            center.y + animatedY
                        )

                        drawLine(
                            color = handleColor.copy(
                                alpha = if (isArmed) {
                                    0.92f
                                } else {
                                    0.58f
                                }
                            ),
                            start = center,
                            end = thumbCenter,
                            strokeWidth = if (isArmed) {
                                5.dp.toPx()
                            } else {
                                3.dp.toPx()
                            }
                        )

                        drawCircle(
                            color = handleColor.copy(
                                alpha = if (isArmed) {
                                    0.16f
                                } else {
                                    0.08f
                                }
                            ),
                            radius = if (isArmed) {
                                68.dp.toPx()
                            } else {
                                57.dp.toPx()
                            },
                            center = thumbCenter
                        )
                    }
                }

                options.forEachIndexed {
                        index,
                        option ->

                    val angle =
                        (-PI / 2.0) +
                                (index * step)

                    val targetCenterX =
                        centerX +
                                ringRadiusPx *
                                cos(angle).toFloat()

                    val targetCenterY =
                        centerY +
                                ringRadiusPx *
                                sin(angle).toFloat()

                    val isCandidate =
                        index == candidateIndex

                    val isSelected =
                        option.code.equals(
                            selectedTypeCode,
                            ignoreCase = true
                        )

                    PremiumEmergencyTarget(
                        option = option,
                        isCandidate = isCandidate,
                        isSelected = isSelected,
                        enabled = !isLoading,
                        onClick = {
                            onTypeSelected(
                                option.code
                            )
                            performAlertHaptic()
                        },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (
                                            targetCenterX -
                                                    with(density) {
                                                        40.dp.toPx()
                                                    }
                                            ).roundToInt(),
                                    y = (
                                            targetCenterY -
                                                    with(density) {
                                                        35.dp.toPx()
                                                    }
                                            ).roundToInt()
                                )
                            }
                            .width(80.dp)
                            .height(70.dp)
                            .zIndex(
                                if (isCandidate) {
                                    4f
                                } else {
                                    2f
                                }
                            )
                    )
                }

                PremiumSosHandle(
                    isLoading = isLoading,
                    isDragging = isDragging,
                    isArmed = isArmed,
                    candidate = candidate,
                    handleColor = handleColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                x = animatedX.roundToInt(),
                                y = animatedY.roundToInt()
                            )
                        }
                        .zIndex(8f)
                        .pointerInput(
                            isLoading,
                            options.size,
                            maxDragRadiusPx
                        ) {
                            if (
                                !isLoading &&
                                options.isNotEmpty()
                            ) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        candidateIndex = -1
                                        dragOffset =
                                            Offset.Zero
                                        armedHapticPlayed =
                                            false
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        candidateIndex = -1
                                        dragOffset =
                                            Offset.Zero
                                        armedHapticPlayed =
                                            false
                                    },
                                    onDragEnd = {
                                        val target =
                                            options.getOrNull(
                                                candidateIndex
                                            )

                                        val shouldSend =
                                            target != null &&
                                                    offsetMagnitude(
                                                        dragOffset
                                                    ) >=
                                                    dispatchThresholdPx

                                        isDragging = false
                                        candidateIndex = -1
                                        dragOffset =
                                            Offset.Zero
                                        armedHapticPlayed =
                                            false

                                        if (
                                            shouldSend &&
                                            target != null
                                        ) {
                                            performAlertHaptic()

                                            onEmergencyTriggered(
                                                target.code
                                            )
                                        }
                                    },
                                    onDrag = {
                                            change,
                                            dragAmount ->

                                        change.consume()

                                        val next =
                                            clampOffset(
                                                offset =
                                                    dragOffset +
                                                            dragAmount,
                                                maxRadius =
                                                    maxDragRadiusPx
                                            )

                                        dragOffset = next

                                        val nextCandidate =
                                            if (
                                                offsetMagnitude(
                                                    next
                                                ) >=
                                                selectionThresholdPx
                                            ) {
                                                nearestRadialIndex(
                                                    next,
                                                    options.size
                                                )
                                            } else {
                                                -1
                                            }

                                        if (
                                            nextCandidate !=
                                            candidateIndex
                                        ) {
                                            candidateIndex =
                                                nextCandidate

                                            options.getOrNull(
                                                nextCandidate
                                            )?.let {
                                                    target ->
                                                onTypeSelected(
                                                    target.code
                                                )

                                                performAlertHaptic()
                                            }
                                        }

                                        val nowArmed =
                                            nextCandidate >= 0 &&
                                                    offsetMagnitude(
                                                        next
                                                    ) >=
                                                    dispatchThresholdPx

                                        if (
                                            nowArmed &&
                                            !armedHapticPlayed
                                        ) {
                                            armedHapticPlayed =
                                                true

                                            performAlertHaptic()
                                        } else if (
                                            !nowArmed
                                        ) {
                                            armedHapticPlayed =
                                                false
                                        }
                                    }
                                )
                            }
                        }
                )

                if (isDragging) {
                    DragInstruction(
                        candidate = candidate,
                        isArmed = isArmed,
                        accent = handleColor,
                        modifier = Modifier
                            .align(
                                Alignment.BottomCenter
                            )
                            .fillMaxWidth()
                            .padding(
                                start = 22.dp,
                                end = 22.dp,
                                bottom = 1.dp
                            )
                    )
                }
            }

        }
    }
}

@Composable
private fun JoystickHeader(
    isLoading: Boolean,
    isDragging: Boolean,
    candidate: EmergencyTypeOption?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
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
                    color =
                        MaterialTheme.colorScheme
                            .onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.65.sp
                )

                Text(
                    text =
                        "Drag toward an emergency type",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }

        val label = when {
            isLoading ->
                "SENDING"

            isDragging &&
                    candidate != null ->
                "LOCKED"

            isDragging ->
                "SELECT"

            else ->
                "SAFE"
        }

        val color = when {
            isLoading ->
                GreenOnline

            isDragging &&
                    candidate != null ->
                candidate.iconTintColor()

            isDragging ->
                MaterialTheme.colorScheme
                    .onSurfaceVariant

            else ->
                GreenOnline
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
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )

                Spacer(
                    modifier = Modifier.width(5.dp)
                )

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
private fun PremiumEmergencyTarget(
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
            dampingRatio =
                Spring.DampingRatioMediumBouncy,
            stiffness =
                Spring.StiffnessMedium
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
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(
                    if (isCandidate) {
                        43.dp
                    } else {
                        39.dp
                    }
                )
                .shadow(
                    elevation = if (isCandidate) {
                        9.dp
                    } else {
                        2.dp
                    },
                    shape = CircleShape,
                    ambientColor =
                        accent.copy(alpha = 0.30f),
                    spotColor =
                        accent.copy(alpha = 0.30f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.25f),
                            accent.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme
                                .surface
                        )
                    )
                )
                .border(
                    width = if (isCandidate) {
                        2.dp
                    } else {
                        1.dp
                    },
                    color = when {
                        isCandidate ->
                            accent

                        isSelected ->
                            accent.copy(alpha = 0.62f)

                        else ->
                            MaterialTheme.colorScheme
                                .surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment =
                Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription =
                    option.displayName,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                option.displayName.uppercase(),
            color = when {
                isCandidate ->
                    accent

                isSelected ->
                    MaterialTheme.colorScheme
                        .onSurface

                else ->
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
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
private fun PremiumSosHandle(
    isLoading: Boolean,
    isDragging: Boolean,
    isArmed: Boolean,
    candidate: EmergencyTypeOption?,
    handleColor: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isArmed -> 1.08f
            isDragging -> 1.03f
            else -> 1f
        },
        spring(
            dampingRatio =
                Spring.DampingRatioMediumBouncy,
            stiffness =
                Spring.StiffnessMedium
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
                    if (isArmed) {
                        112.dp
                    } else {
                        104.dp
                    }
                )
                .clip(CircleShape)
                .background(
                    handleColor.copy(
                        alpha = if (isArmed) {
                            0.14f
                        } else {
                            0.08f
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    color = handleColor.copy(
                        alpha = if (isArmed) {
                            0.55f
                        } else {
                            0.28f
                        }
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(
                    elevation = if (isArmed) {
                        18.dp
                    } else {
                        10.dp
                    },
                    shape = CircleShape,
                    ambientColor =
                        handleColor.copy(alpha = 0.44f),
                    spotColor =
                        handleColor.copy(alpha = 0.44f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            handleColor.copy(alpha = 0.92f),
                            handleColor,
                            handleColor.copy(alpha = 0.78f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color =
                        Color.White.copy(alpha = 0.24f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(27.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text = "SENDING",
                            color =
                                Color.White.copy(
                                    alpha = 0.88f
                                ),
                            fontSize = 7.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing = 0.7.sp
                        )
                    }
                }

                isArmed &&
                        candidate != null -> {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(19.dp)
                        )

                        Text(
                            text = "RELEASE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )

                        Text(
                            text =
                                "TO SEND",
                            color =
                                Color.White.copy(
                                    alpha = 0.80f
                                ),
                            fontSize = 7.sp,
                            fontWeight =
                                FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                isDragging &&
                        candidate != null -> {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector =
                                candidate.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(20.dp)
                        )

                        Text(
                            text = "LOCKED",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing = 0.7.sp
                        )

                        Text(
                            text =
                                candidate.displayName
                                    .uppercase(),
                            color =
                                Color.White.copy(
                                    alpha = 0.78f
                                ),
                            fontSize = 6.5.sp,
                            fontWeight =
                                FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SOS",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing = 1.6.sp
                        )

                        Text(
                            text = "DRAG",
                            color =
                                Color.White.copy(
                                    alpha = 0.76f
                                ),
                            fontSize = 7.5.sp,
                            fontWeight =
                                FontWeight.Black,
                            letterSpacing = 1.1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragInstruction(
    candidate: EmergencyTypeOption?,
    isArmed: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = when {
            isArmed ->
                accent.copy(alpha = 0.13f)

            candidate != null ->
                MaterialTheme.colorScheme
                    .surface.copy(alpha = 0.94f)

            else ->
                MaterialTheme.colorScheme
                    .surface.copy(alpha = 0.86f)
        },
        border = BorderStroke(
            1.dp,
            when {
                isArmed ->
                    accent.copy(alpha = 0.52f)

                candidate != null ->
                    accent.copy(alpha = 0.24f)

                else ->
                    MaterialTheme.colorScheme
                        .surfaceVariant
            }
        )
    ) {
        Text(
            text = when {
                isArmed &&
                        candidate != null ->
                    "RELEASE NOW • SEND ${candidate.displayName.uppercase()} SOS"

                candidate != null ->
                    "KEEP DRAGGING • ARM ${candidate.displayName.uppercase()} SOS"

                else ->
                    "MOVE TOWARD AN EMERGENCY TYPE"
            },
            color = if (isArmed) {
                accent
            } else {
                MaterialTheme.colorScheme
                    .onSurfaceVariant
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
private fun JoystickSafetyFooter(
    selected: EmergencyTypeOption?,
    isLoading: Boolean
) {
    val accent =
        selected?.iconTintColor() ?: RedPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.lg)
            .background(
                if (isLoading) {
                    GreenOnline.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme
                        .background.copy(alpha = 0.72f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isLoading) {
                    GreenOnline.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme
                        .surfaceVariant
                },
                shape = AgapShape.lg
            )
            .padding(
                horizontal = 11.dp,
                vertical = 9.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
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
            contentAlignment =
                Alignment.Center
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
                color =
                    MaterialTheme.colorScheme
                        .onSurface,
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
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                fontSize = 8.5.sp,
                lineHeight = 12.sp
            )
        }
    }
}

private fun orderEmergencyTypes(
    source: List<EmergencyTypeOption>
): List<EmergencyTypeOption> {
    val order = listOf(
        "MEDICAL",
        "FIRE",
        "ACCIDENT",
        "DISASTER",
        "CRIME",
        "POLICE"
    )

    return source.sortedWith(
        compareBy<EmergencyTypeOption> {
            val index =
                order.indexOf(
                    it.code.uppercase()
                )

            if (index == -1) {
                Int.MAX_VALUE
            } else {
                index
            }
        }.thenBy {
            it.displayName
        }
    )
}

private fun clampOffset(
    offset: Offset,
    maxRadius: Float
): Offset {
    val magnitude =
        offsetMagnitude(offset)

    if (
        magnitude <= maxRadius ||
        magnitude == 0f
    ) {
        return offset
    }

    val scale =
        maxRadius / magnitude

    return Offset(
        x = offset.x * scale,
        y = offset.y * scale
    )
}

private fun offsetMagnitude(
    offset: Offset
): Float {
    return sqrt(
        (offset.x * offset.x) +
                (offset.y * offset.y)
    )
}

private fun nearestRadialIndex(
    offset: Offset,
    count: Int
): Int {
    if (count <= 1) {
        return 0
    }

    var bestIndex = 0
    var bestDot =
        Float.NEGATIVE_INFINITY

    val magnitude =
        offsetMagnitude(offset)
            .coerceAtLeast(0.0001f)

    val nx =
        offset.x / magnitude

    val ny =
        offset.y / magnitude

    val step =
        (2.0 * PI) / count

    repeat(count) { index ->
        val angle =
            (-PI / 2.0) +
                    (index * step)

        val tx =
            cos(angle).toFloat()

        val ty =
            sin(angle).toFloat()

        val dot =
            (nx * tx) +
                    (ny * ty)

        if (dot > bestDot) {
            bestDot = dot
            bestIndex = index
        }
    }

    return bestIndex
}
