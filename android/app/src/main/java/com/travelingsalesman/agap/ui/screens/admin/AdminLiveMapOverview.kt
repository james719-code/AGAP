package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.core.routing.OfflineRouteResult
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
internal fun MapOverviewCardV2(
    incidents: Int,
    responders: Int,
    incidentIndex: Int,
    responderIndex: Int,
    selectedResponder: ResponderUnitEntity?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = if (expanded) {
            AgapShape.xl
        } else {
            AgapShape.pill
        },
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            MapBorder
        ),
        shadowElevation = 2.dp
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExpandedChange(true)
                    }
                    .padding(
                        horizontal = AgapSpacing.md,
                        vertical = AgapSpacing.sm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            BlueMedical.copy(alpha = 0.13f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.sm))

                Text(
                    text = "$incidents incidents • $responders units",
                    color = MapText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Expand map controls",
                    tint = MapTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(
                    horizontal = AgapSpacing.md,
                    vertical = AgapSpacing.md
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(AgapShape.md)
                            .background(
                                BlueMedical.copy(alpha = 0.13f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = BlueMedical,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(AgapSpacing.md))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Operational map ready",
                            color = MapText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "$incidents incidents • $responders responder units",
                            color = MapTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            onExpandedChange(false)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Minimize map controls",
                            tint = MapTextMuted
                        )
                    }
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                HorizontalDivider(
                    color = MapBorder
                )

                Spacer(Modifier.height(AgapSpacing.sm))

                BottomMapNavigator(
                    incidentIndex = incidentIndex,
                    incidentCount = incidents,
                    responderIndex = responderIndex,
                    responderCount = responders,
                    selectedResponder = selectedResponder,
                    onPreviousIncident = onPreviousIncident,
                    onNextIncident = onNextIncident,
                    onPreviousResponder = onPreviousResponder,
                    onNextResponder = onNextResponder
                )
            }
        }
    }
}

@Composable
internal fun AdminRoutePreviewBanner(
    responderCallsign: String?,
    route: OfflineRouteResult?,
    isCalculating: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            if (route?.isRoadRoute == false) RedPrimary.copy(alpha = 0.55f)
            else AmberWarning.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.md,
                vertical = AgapSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            if (isCalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AmberWarning
                )
                Text(
                    text = "CALCULATING ROUTE${responderCallsign?.let { " • $it" } ?: ""}",
                    color = MapText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (route != null) {
                Icon(
                    imageVector = if (route.isRoadRoute) Icons.Outlined.NearMe else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (route.isRoadRoute) AmberWarning else RedPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = buildString {
                        append(responderCallsign ?: "RESPONDER")
                        append(" • ")
                        append(if (route.isRoadRoute) "SHORTEST ROAD" else "DIRECT FALLBACK")
                        append(" • ")
                        append(formatRouteDistance(route.distanceMeters))
                        if (route.isRoadRoute) {
                            append(" • ")
                            append(formatRouteEta(route.durationSeconds))
                        }
                    },
                    color = MapText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
