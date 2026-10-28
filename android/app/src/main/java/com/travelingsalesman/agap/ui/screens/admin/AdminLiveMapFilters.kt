package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.EmergencyType
import com.travelingsalesman.agap.ui.theme.GreenOnline

@Composable
internal fun LiveMapLegendV2(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(170.dp),
        shape = AgapShape.lg,
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            MapBorder
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            Text(
                text = "MAP LEGEND",
                color = MapText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )

            LegendRowV2(
                color = EmergencyType.DISASTER.color,
                label = EmergencyType.DISASTER.label
            )

            LegendRowV2(
                color = EmergencyType.MEDICAL.color,
                label = EmergencyType.MEDICAL.label
            )

            LegendRowV2(
                color = EmergencyType.FIRE.color,
                label = EmergencyType.FIRE.label
            )

            LegendRowV2(
                color = EmergencyType.ACCIDENT.color,
                label = EmergencyType.ACCIDENT.label
            )

            LegendRowV2(
                color = EmergencyType.CRIME.color,
                label = EmergencyType.CRIME.label
            )

            LegendRowV2(
                color = BlueMedical,
                label = "Responder"
            )

            LegendRowV2(
                color = GreenOnline,
                label = "Command device"
            )
        }
    }
}

@Composable
internal fun LegendRowV2(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(Modifier.width(AgapSpacing.sm))

        Text(
            text = label,
            color = MapTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun LiveMapFilterPanelV2(
    showIncidents: Boolean,
    showResponders: Boolean,
    onlyOpen: Boolean,
    onShowIncidentsChange: (Boolean) -> Unit,
    onShowRespondersChange: (Boolean) -> Unit,
    onOnlyOpenChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(250.dp),
        shape = AgapShape.xl,
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            MapBorder
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "MAP FILTERS",
                        color = MapText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp
                    )

                    Text(
                        text = "Visible operational layers",
                        color = MapTextMuted,
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = onClose
                ) {
                    Text(
                        text = "DONE",
                        color = BlueMedical,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            FilterRowV2(
                title = "Incidents",
                subtitle = "Emergency markers",
                checked = showIncidents,
                onCheckedChange = onShowIncidentsChange
            )

            FilterRowV2(
                title = "Responder units",
                subtitle = "Field resources",
                checked = showResponders,
                onCheckedChange = onShowRespondersChange
            )

            FilterRowV2(
                title = "Open incidents only",
                subtitle = "Hide resolved calls",
                checked = onlyOpen,
                onCheckedChange = onOnlyOpenChange
            )
        }
    }
}

@Composable
internal fun FilterRowV2(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = MapText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = MapTextMuted,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BlueMedical,
                uncheckedThumbColor = MapTextMuted,
                uncheckedTrackColor = MapPanelSoft,
                uncheckedBorderColor = MapBorder
            )
        )
    }
}
