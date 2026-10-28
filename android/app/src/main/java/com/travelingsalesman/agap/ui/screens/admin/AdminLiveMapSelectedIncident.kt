package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.EmergencyType
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.util.Locale

@Composable
internal fun SelectedIncidentCardV2(
    incident: IncidentEntity,
    userLocation: Location?,
    focused: Boolean,
    incidentIndex: Int,
    incidentCount: Int,
    responderIndex: Int,
    responderCount: Int,
    selectedResponder: ResponderUnitEntity?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit,
    onFocus: () -> Unit,
    onDetails: () -> Unit,
    onDispatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emergencyType = EmergencyType.fromCode(incident.emergencyType)
    val accent = if (incident.status.equals("RESOLVED", ignoreCase = true)) {
        GreenOnline
    } else {
        emergencyType.color
    }

    val distance = distanceText(
        userLocation = userLocation,
        incident = incident
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = if (expanded) {
            AgapShape.xxl
        } else {
            AgapShape.xl
        },
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            if (focused) {
                accent.copy(alpha = 0.80f)
            } else {
                MapBorder
            }
        ),
        shadowElevation = if (expanded) {
            15.dp
        } else {
            10.dp
        }
    ) {
        if (!expanded) {
            /*
             * COLLAPSED MODE
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExpandedChange(true)
                    }
                    .padding(
                        start = AgapSpacing.md,
                        top = AgapSpacing.sm,
                        end = AgapSpacing.sm,
                        bottom = AgapSpacing.sm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = emergencyType.icon,
                        contentDescription = emergencyType.label,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.sm))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = emergencyType.label.uppercase(),
                            color = MapText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.width(AgapSpacing.xs))

                        Text(
                            text = "#${incident.incidentId}",
                            color = MapTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = buildString {
                            if (distance != null) {
                                append(distance)
                                append(" • ")
                            }

                            append(
                                formatMapAge(
                                    incident.timestamp
                                )
                            )

                            if (focused) {
                                append(" • focused")
                            }
                        },
                        color = MapTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                CommandStatusPill(
                    status = incident.status
                )

                Spacer(Modifier.width(AgapSpacing.xs))

                IconButton(
                    onClick = {
                        onExpandedChange(true)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Expand incident panel",
                        tint = MapText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            /*
             * EXPANDED COMMAND DECK
             */
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = emergencyType.icon,
                            contentDescription = emergencyType.label,
                            tint = accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(Modifier.width(AgapSpacing.md))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${emergencyType.label.uppercase()} EMERGENCY",
                            color = MapText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildString {
                                if (distance != null) {
                                    append(distance)
                                    append(" • ")
                                }

                                append(
                                    formatMapAge(
                                        incident.timestamp
                                    )
                                )

                                append(
                                    " • #${incident.incidentId}"
                                )
                            },
                            color = MapTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    CommandStatusPill(
                        status = incident.status
                    )

                    Spacer(Modifier.width(AgapSpacing.xs))

                    IconButton(
                        onClick = {
                            onExpandedChange(false)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Minimize incident panel",
                            tint = MapTextMuted,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MapTextMuted,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(AgapSpacing.sm))

                    Text(
                        text = incident.targetId.ifBlank {
                            "%.5f, %.5f".format(
                                Locale.ROOT,
                                incident.latitude,
                                incident.longitude
                            )
                        },
                        color = MapTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDetails,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Details",
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onFocus,
                        modifier = Modifier.weight(0.9f),
                        shape = AgapShape.md,
                        border = BorderStroke(
                            1.dp,
                            if (focused) {
                                accent.copy(alpha = 0.70f)
                            } else {
                                MapBorder
                            }
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (focused) {
                                accent
                            } else {
                                MapText
                            }
                        ),
                        contentPadding = PaddingValues(
                            vertical = AgapSpacing.sm
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(Modifier.width(AgapSpacing.xs))

                        Text(
                            text = if (focused) {
                                "FOCUSED"
                            } else {
                                "FOCUS"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onDetails,
                        modifier = Modifier.weight(0.9f),
                        shape = AgapShape.md,
                        border = BorderStroke(
                            1.dp,
                            MapBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MapText
                        ),
                        contentPadding = PaddingValues(
                            vertical = AgapSpacing.sm
                        )
                    ) {
                        Text(
                            text = "DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (incident.status.uppercase() !in LIVE_MAP_CLOSED_STATUSES) {
                        Button(
                            onClick = onDispatch,
                            modifier = Modifier.weight(1.2f),
                            shape = AgapShape.md,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(
                                vertical = AgapSpacing.sm
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NearMe,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )

                            Spacer(Modifier.width(AgapSpacing.xs))

                            Text(
                                text = "RESPOND",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                HorizontalDivider(
                    color = MapBorder
                )

                Spacer(Modifier.height(AgapSpacing.sm))

                BottomMapNavigator(
                    incidentIndex = incidentIndex,
                    incidentCount = incidentCount,
                    responderIndex = responderIndex,
                    responderCount = responderCount,
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
internal fun CommandStatusPill(
    status: String
) {
    val accent = when (status.uppercase()) {
        "RECEIVED" -> RedPrimary
        "ACKNOWLEDGED" -> AmberWarning
        "DISPATCHED",
        "EN_ROUTE" -> AmberWarning
        "ON_SCENE" -> BlueMedical
        "RESOLVED" -> GreenOnline
        else -> MapTextMuted
    }

    Surface(
        shape = AgapShape.pill,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.sm,
                vertical = AgapSpacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )

            Spacer(Modifier.width(AgapSpacing.xs))

            Text(
                text = status.replace("_", " ").uppercase(),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
        }
    }
}
