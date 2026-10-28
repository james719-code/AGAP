package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
internal fun CompactMapControlRail(
    legendOpen: Boolean,
    onLegend: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = AgapShape.lg,
            color = if (legendOpen) {
                BlueMedical.copy(alpha = 0.94f)
            } else {
                MapPanel
            },
            border = BorderStroke(
                1.dp,
                if (legendOpen) {
                    BlueMedical
                } else {
                    MapBorder
                }
            ),
            shadowElevation = 8.dp,
            onClick = onLegend
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = "Legend",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = "LEGEND",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Surface(
            modifier = Modifier.width(50.dp),
            shape = AgapShape.lg,
            color = MapPanel,
            border = BorderStroke(
                1.dp,
                MapBorder
            ),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.width(50.dp)
            ) {
                RailButtonV2(
                    icon = Icons.Outlined.Add,
                    label = "Zoom in",
                    onClick = onZoomIn
                )

                HorizontalDivider(
                    color = MapBorder
                )

                RailButtonV2(
                    icon = Icons.Outlined.Remove,
                    label = "Zoom out",
                    onClick = onZoomOut
                )

                HorizontalDivider(
                    color = MapBorder
                )

                RailButtonV2(
                    icon = Icons.Outlined.MyLocation,
                    label = "Recenter",
                    onClick = onRecenter
                )
            }
        }
    }
}

@Composable
internal fun RailButtonV2(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MapText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun BottomMapNavigator(
    incidentIndex: Int,
    incidentCount: Int,
    responderIndex: Int,
    responderCount: Int,
    selectedResponder: ResponderUnitEntity?,
    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MAP NAVIGATION",
                color = MapTextMuted,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.75.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Use arrows to move camera",
                color = MapTextMuted.copy(alpha = 0.78f),
                fontSize = 7.sp
            )
        }

        Spacer(Modifier.height(AgapSpacing.sm))

        NavigationLine(
            title = "INCIDENT",
            position = mapPositionLabel(incidentIndex, incidentCount),
            subtitle = if (incidentCount > 0) {
                "Priority queue"
            } else {
                "No mapped incidents"
            },
            accent = RedPrimary,
            enabled = incidentCount > 0,
            onPrevious = onPreviousIncident,
            onNext = onNextIncident
        )

        Spacer(Modifier.height(AgapSpacing.sm))

        NavigationLine(
            title = "RESPONDER",
            position = mapPositionLabel(responderIndex, responderCount),
            subtitle = selectedResponder?.let {
                "${it.unitCallsign} • ${it.status}"
            } ?: if (responderCount > 0) {
                "Select a field unit"
            } else {
                "No mapped responder units"
            },
            accent = BlueMedical,
            enabled = responderCount > 0,
            onPrevious = onPreviousResponder,
            onNext = onNextResponder
        )
    }
}

@Composable
internal fun NavigationLine(
    title: String,
    position: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        color = MapPanelSoft.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            if (enabled) {
                accent.copy(alpha = 0.22f)
            } else {
                MapBorder
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(
                start = AgapSpacing.md,
                top = AgapSpacing.sm,
                end = AgapSpacing.sm,
                bottom = AgapSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            accent
                        } else {
                            MapTextMuted.copy(alpha = 0.30f)
                        }
                    )
            )

            Spacer(Modifier.width(AgapSpacing.sm))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = if (enabled) {
                            MapText
                        } else {
                            MapTextMuted
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.45.sp
                    )

                    Spacer(Modifier.width(AgapSpacing.sm))

                    Text(
                        text = position,
                        color = if (enabled) {
                            accent
                        } else {
                            MapTextMuted
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = subtitle,
                    color = MapTextMuted,
                    fontSize = 7.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(AgapSpacing.sm))

            CompactNavigatorArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Previous $title",
                enabled = enabled,
                accent = accent,
                onClick = onPrevious
            )

            Spacer(Modifier.width(AgapSpacing.sm))

            CompactNavigatorArrow(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Next $title",
                enabled = enabled,
                accent = accent,
                onClick = onNext
            )
        }
    }
}

@Composable
internal fun CompactNavigatorArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = AgapShape.md,
        color = if (enabled) {
            accent.copy(alpha = 0.10f)
        } else {
            MapPanel
        },
        border = BorderStroke(
            1.dp,
            if (enabled) {
                accent.copy(alpha = 0.42f)
            } else {
                MapBorder
            }
        ),
        enabled = enabled,
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) {
                    accent
                } else {
                    MapTextMuted
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun mapPositionLabel(
    index: Int,
    count: Int
): String {
    if (count <= 0) {
        return "0 / 0"
    }

    if (index < 0) {
        return "— / $count"
    }

    return "${index + 1} / $count"
}
