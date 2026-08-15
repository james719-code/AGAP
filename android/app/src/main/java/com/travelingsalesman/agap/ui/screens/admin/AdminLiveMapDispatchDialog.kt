package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.core.routing.OfflineRouteResult
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
internal fun AdminMapDispatchDialogV2(
    incidentId: String,
    responders: List<ResponderUnitDto>,
    isLoadingResponders: Boolean,
    selectedResponderId: Long?,
    route: OfflineRouteResult?,
    isCalculatingRoute: Boolean,
    onDismiss: () -> Unit,
    onSelectResponder: (Long) -> Unit,
    onDispatch: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MapPanelStrong,
        titleContentColor = MapText,
        textContentColor = MapTextMuted,
        title = {
            Column {
                Text(
                    text = "Choose responder",
                    color = MapText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "INCIDENT #$incidentId • ROUTE PREVIEW",
                    color = AmberWarning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                if (isLoadingResponders) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AgapSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                            color = AmberWarning
                        )
                        Spacer(Modifier.width(AgapSpacing.md))
                        Column {
                            Text(
                                text = "CHECKING AVAILABLE UNITS",
                                color = MapText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Busy, offline and incompatible units are excluded",
                                color = MapTextMuted,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                } else if (responders.isEmpty()) {
                    Text(
                        text = "No eligible responder units are currently available.",
                        color = MapTextMuted
                    )
                    Text(
                        text = "Units already assigned, offline, or incompatible with this emergency are hidden.",
                        color = MapTextMuted,
                        fontSize = 8.5.sp
                    )
                } else {
                    Text(
                        text = "Only eligible IDLE units are shown. Tap one to preview its shortest road path.",
                        color = MapTextMuted,
                        fontSize = 10.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 285.dp),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        items(
                            items = responders.take(FULL_MAP_DISPATCH_LIMIT),
                            key = { it.id },
                            contentType = { "map_dispatch_unit" }
                        ) { responder ->
                            val normalizedStatus = normalizeResponderStatusValue(responder.status)
                            val selectable = normalizedStatus == "IDLE"
                            val selected = responder.id == selectedResponderId && selectable
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectable) { onSelectResponder(responder.id) },
                                shape = AgapShape.md,
                                color = if (selected) {
                                    AmberWarning.copy(alpha = 0.11f)
                                } else {
                                    MapPanelSoft
                                },
                                border = BorderStroke(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) AmberWarning else MapBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(AgapSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    AmberWarning.copy(alpha = 0.18f)
                                                } else {
                                                    BlueMedical.copy(alpha = 0.15f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) {
                                                Icons.Outlined.NearMe
                                            } else {
                                                Icons.Outlined.Group
                                            },
                                            contentDescription = null,
                                            tint = if (selected) AmberWarning else BlueMedical,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(AgapSpacing.sm))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = responder.unitCallsign,
                                            color = MapText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Agency #${responder.agencyId} • $normalizedStatus",
                                            color = MapTextMuted,
                                            fontSize = 8.5.sp
                                        )
                                    }

                                    if (selected) {
                                        Text(
                                            text = "PREVIEWING",
                                            color = AmberWarning,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedResponderId != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AgapShape.md,
                        color = MapPanelSoft,
                        border = BorderStroke(1.dp, MapBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AgapSpacing.md,
                                vertical = AgapSpacing.md
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCalculatingRoute) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AmberWarning
                                )
                                Spacer(Modifier.width(AgapSpacing.md))
                                Column {
                                    Text(
                                        text = "CALCULATING SHORTEST ROAD PATH",
                                        color = MapText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Using downloaded routing graph; corridor fallback only if the package is missing",
                                        color = MapTextMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            } else if (route != null) {
                                Icon(
                                    imageVector = if (route.isRoadRoute) {
                                        Icons.Outlined.NearMe
                                    } else {
                                        Icons.Outlined.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = if (route.isRoadRoute) AmberWarning else RedPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(AgapSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (route.isRoadRoute) {
                                            "SHORTEST ROAD ROUTE • ${formatRouteDistance(route.distanceMeters)} • ${formatRouteEta(route.durationSeconds)}"
                                        } else {
                                            "DIRECT FALLBACK • ${formatRouteDistance(route.distanceMeters)}"
                                        },
                                        color = MapText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (route.isRoadRoute) {
                                            route.message ?: "A* route follows cached OpenStreetMap roads"
                                        } else {
                                            route.message ?: "Road routing data unavailable"
                                        },
                                        color = MapTextMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "This unit has no usable GPS position for route preview.",
                                    color = MapTextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedResponderAvailable = selectedResponderId != null && responders.any {
                it.id == selectedResponderId && normalizeResponderStatusValue(it.status) == "IDLE"
            }
            Button(
                onClick = {
                    selectedResponderId?.let(onDispatch)
                },
                enabled = selectedResponderAvailable && route != null && !isCalculatingRoute && !isLoadingResponders,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarning,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(AgapSpacing.xs))
                Text(
                    text = if (route?.isRoadRoute == true) {
                        "DISPATCH & KEEP ROUTE"
                    } else {
                        "DISPATCH"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    color = MapTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
