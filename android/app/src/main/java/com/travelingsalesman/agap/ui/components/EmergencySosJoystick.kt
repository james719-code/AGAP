package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.RedPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
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

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableIntStateOf(-1) }
    var armedHapticPlayed by remember { mutableStateOf(false) }

    val selectedIndex = options.indexOfFirst {
        it.code.equals(selectedTypeCode, ignoreCase = true)
    }

    val candidate = options.getOrNull(candidateIndex)
    val preview = candidate ?: options.getOrNull(selectedIndex)
    val previewColor = preview?.iconTintColor() ?: RedPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.xxl)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = AgapShape.xxl
            )
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.xl)
        ) {
            JoystickHeader(
                isLoading = isLoading,
                isDragging = isDragging,
                candidate = candidate,
                isPreview = isPreview
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                val containerPx = constraints.maxWidth.toFloat()
                val targetWidthPx = with(density) { 80.dp.toPx() }
                val marginPx = with(density) { 7.dp.toPx() }
                val preferredRingPx = with(density) { 121.dp.toPx() }
                val minimumRingPx = with(density) { 72.dp.toPx() }

                val ringRadiusPx = minOf(
                    preferredRingPx,
                    (containerPx / 2f) - (targetWidthPx / 2f) - marginPx
                ).coerceAtLeast(minimumRingPx)

                val maxDragRadiusPx = (ringRadiusPx - with(density) { 38.dp.toPx() })
                    .coerceAtLeast(with(density) { 54.dp.toPx() })

                val selectionThresholdPx = maxDragRadiusPx * 0.34f
                val dispatchThresholdPx = maxDragRadiusPx * 0.82f
                val distance = offsetMagnitude(dragOffset)

                val isArmed = isDragging && candidate != null && distance >= dispatchThresholdPx

                val animatedX by animateFloatAsState(
                    targetValue = if (isDragging) dragOffset.x else 0f,
                    animationSpec = if (isDragging) snap() else spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "premium_sos_x"
                )

                val animatedY by animateFloatAsState(
                    targetValue = if (isDragging) dragOffset.y else 0f,
                    animationSpec = if (isDragging) snap() else spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "premium_sos_y"
                )

                val handleColor by animateColorAsState(
                    targetValue = when {
                        isArmed && candidate != null -> candidate.iconTintColor()
                        candidate != null -> candidate.iconTintColor()
                        else -> RedPrimary
                    },
                    label = "premium_sos_color"
                )

                val optionColors = options.map { it.iconTintColor() }
                val canvasSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val canvasOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                val centerX = containerPx / 2f
                val centerY = containerPx / 2f
                val step = (2.0 * PI) / options.size.coerceAtLeast(1)

                Canvas(
                    modifier = Modifier.matchParentSize()
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        color = canvasSurfaceVariant.copy(alpha = 0.5f),
                        radius = ringRadiusPx - 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    drawCircle(
                        color = if (isArmed) previewColor.copy(alpha = 0.72f) else canvasOnSurfaceVariant.copy(alpha = 0.20f),
                        radius = dispatchThresholdPx,
                        center = center,
                        style = Stroke(
                            width = if (isArmed) 2.dp.toPx() else 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(7.dp.toPx(), 7.dp.toPx())
                            )
                        )
                    )

                    options.forEachIndexed { index, _ ->
                        val angle = (-PI / 2.0) + (index * step)
                        val lineEndRadius = ringRadiusPx - 39.dp.toPx()
                        val end = Offset(
                            x = center.x + lineEndRadius * cos(angle).toFloat(),
                            y = center.y + lineEndRadius * sin(angle).toFloat()
                        )

                        val isCandidateLane = index == candidateIndex
                        val isSelectedLane = !isDragging && index == selectedIndex
                        val laneColor = optionColors[index]

                        drawLine(
                            color = when {
                                isCandidateLane -> laneColor.copy(alpha = 0.88f)
                                isSelectedLane -> laneColor.copy(alpha = 0.18f)
                                else -> Color.Transparent
                            },
                            start = center,
                            end = end,
                            strokeWidth = when {
                                isCandidateLane -> 3.dp.toPx()
                                isSelectedLane -> 1.5.dp.toPx()
                                else -> 1.dp.toPx()
                            }
                        )

                        drawCircle(
                            color = laneColor.copy(alpha = if (isCandidateLane) 0.90f else 0f),
                            radius = if (isCandidateLane) 4.dp.toPx() else 2.5.dp.toPx(),
                            center = end
                        )
                    }

                    drawCircle(
                        color = canvasSurfaceVariant,
                        radius = 57.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    if (isDragging) {
                        val thumbCenter = Offset(center.x + animatedX, center.y + animatedY)

                        drawLine(
                            color = handleColor.copy(alpha = if (isArmed) 0.92f else 0.58f),
                            start = center,
                            end = thumbCenter,
                            strokeWidth = if (isArmed) 5.dp.toPx() else 3.dp.toPx()
                        )

                        drawCircle(
                            color = handleColor.copy(alpha = if (isArmed) 0.16f else 0.08f),
                            radius = if (isArmed) 68.dp.toPx() else 57.dp.toPx(),
                            center = thumbCenter
                        )
                    }
                }

                options.forEachIndexed { index, option ->
                    val angle = (-PI / 2.0) + (index * step)
                    val targetCenterX = centerX + ringRadiusPx * cos(angle).toFloat()
                    val targetCenterY = centerY + ringRadiusPx * sin(angle).toFloat()
                    val isCandidate = index == candidateIndex
                    val isSelected = option.code.equals(selectedTypeCode, ignoreCase = true)

                    PremiumEmergencyTarget(
                        option = option,
                        isCandidate = isCandidate,
                        isSelected = isSelected,
                        enabled = !isLoading,
                        onClick = {
                            onTypeSelected(option.code)
                            performAlertHaptic()
                        },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (targetCenterX - with(density) { 40.dp.toPx() }).roundToInt(),
                                    y = (targetCenterY - with(density) { 40.dp.toPx() }).roundToInt()
                                )
                            }
                            .width(80.dp)
                            .height(80.dp)
                            .zIndex(if (isCandidate) 4f else 2f)
                    )
                }

                PremiumSosHandle(
                    isLoading = isLoading,
                    isDragging = isDragging,
                    isArmed = isArmed,
                    candidate = candidate,
                    handleColor = handleColor,
                    isPreview = isPreview,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("sos-handle")
                        .offset {
                            IntOffset(
                                x = animatedX.roundToInt(),
                                y = animatedY.roundToInt()
                            )
                        }
                        .zIndex(8f)
                        .pointerInput(isLoading, options.size, maxDragRadiusPx) {
                            if (!isLoading && options.isNotEmpty()) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        candidateIndex = -1
                                        dragOffset = Offset.Zero
                                        armedHapticPlayed = false
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        candidateIndex = -1
                                        dragOffset = Offset.Zero
                                        armedHapticPlayed = false
                                    },
                                    onDragEnd = {
                                        val target = options.getOrNull(candidateIndex)
                                        val shouldSend = target != null &&
                                            offsetMagnitude(dragOffset) >= dispatchThresholdPx

                                        isDragging = false
                                        candidateIndex = -1
                                        dragOffset = Offset.Zero
                                        armedHapticPlayed = false

                                        if (shouldSend && target != null) {
                                            performAlertHaptic()
                                            onEmergencyTriggered(target.code)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val next = clampOffset(
                                            offset = dragOffset + dragAmount,
                                            maxRadius = maxDragRadiusPx
                                        )
                                        dragOffset = next

                                        val nextCandidate = if (offsetMagnitude(next) >= selectionThresholdPx) {
                                            nearestRadialIndex(next, options.size)
                                        } else {
                                            -1
                                        }

                                        if (nextCandidate != candidateIndex) {
                                            candidateIndex = nextCandidate
                                            options.getOrNull(nextCandidate)?.let { target ->
                                                onTypeSelected(target.code)
                                                performAlertHaptic()
                                            }
                                        }

                                        val nowArmed = nextCandidate >= 0 &&
                                            offsetMagnitude(next) >= dispatchThresholdPx

                                        if (nowArmed && !armedHapticPlayed) {
                                            armedHapticPlayed = true
                                            performAlertHaptic()
                                        } else if (!nowArmed) {
                                            armedHapticPlayed = false
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
                        isPreview = isPreview,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, bottom = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(AgapSpacing.xs))
            JoystickSafetyFooter(selected = preview, isLoading = isLoading)
        }
    }
}
