package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.OrangeAccident
import com.travelingsalesman.agap.ui.theme.PurpleCrime
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard

import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val (typeColor, icon) = when (incident.emergencyType.uppercase()) {
        "MEDICAL" -> BlueMedical to Icons.Default.LocalHospital
        "CRIME" -> PurpleCrime to Icons.Default.LocalPolice
        "ACCIDENT" -> OrangeAccident to Icons.Default.CarCrash
        else -> RedPrimary to Icons.Default.Warning
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.lg)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(AgapSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color indicator bar & icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(AgapShape.md)
                    .background(typeColor.copy(alpha = 0.15f))
                    .border(width = 1.dp, color = typeColor.copy(alpha = 0.3f), shape = AgapShape.md),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = incident.emergencyType,
                    tint = typeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(AgapSpacing.md))

            // Main details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = incident.emergencyType.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        letterSpacing = 0.5.sp
                    )
                    StatusBadge(status = incident.status)
                }

                Spacer(modifier = Modifier.height(AgapSpacing.xs))

                Text(
                    text = "TARGET: ${incident.targetId.take(18)}...",
                    style = MaterialTheme.typography.labelMedium,
                    color = subTextColor,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "GPS: %.4f, %.4f".format(incident.latitude, incident.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}



