package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.OrangeAccident
import com.travelingsalesman.agap.ui.theme.PurpleCrime
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeVisual = incidentTypeVisual(incident.emergencyType)
    val agency = targetAgencyTypeForIncident(incident)
    val channelLabel = incidentChannelLabel(incident)
    val channelColor = incidentChannelColor(incident)
    val cleanDescription = incident.description
        ?.substringBefore("Target agency:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .clickable(onClick = onClick)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AgapShape.md)
                    .background(typeVisual.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeVisual.icon,
                    contentDescription = incident.emergencyType,
                    tint = typeVisual.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = incident.emergencyType.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "• #${incident.incidentId.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactMeta(
                        icon = Icons.Default.AccessTime,
                        text = formatIncidentTimestamp(incident.timestamp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CompactMeta(
                        icon = if (channelLabel.contains("MESH")) {
                            Icons.Default.Bluetooth
                        } else {
                            Icons.Default.Wifi
                        },
                        text = channelLabel,
                        tint = channelColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactMeta(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class IncidentTypeVisual(
    val color: Color,
    val icon: ImageVector
)

private fun incidentTypeVisual(type: String): IncidentTypeVisual {
    val upper = type.uppercase()
    return when {
        upper.contains("MEDICAL") -> IncidentTypeVisual(BlueMedical, Icons.Default.LocalHospital)
        upper.contains("FIRE") -> IncidentTypeVisual(Color(0xFFFF5722), Icons.Default.LocalFireDepartment)
        upper.contains("CRIME") || upper.contains("SECURITY") -> IncidentTypeVisual(Color(0xFF8E24AA), Icons.Default.LocalPolice)
        upper.contains("ACCIDENT") || upper.contains("TRAFFIC") || upper.contains("VEHICULAR") -> IncidentTypeVisual(Color(0xFFFFB300), Icons.Default.CarCrash)
        upper.contains("DISASTER") || upper.contains("NATURAL") -> IncidentTypeVisual(Color(0xFF4CAF50), Icons.Default.Warning)
        else -> IncidentTypeVisual(RedPrimary, Icons.Default.Warning)
    }
}

private fun incidentChannelLabel(incident: IncidentEntity): String {
    val channel = incident.channel.uppercase()
    val deliveryPath = incident.deliveryPath.uppercase()

    return when {
        channel.contains("BLE") || deliveryPath.contains("MESH") -> "MESH RELAY"
        channel.contains("OFFLINE") -> "OFFLINE"
        else -> "ONLINE"
    }
}

private fun incidentChannelColor(incident: IncidentEntity): Color {
    return when (incidentChannelLabel(incident)) {
        "ONLINE" -> GreenOnline
        "MESH RELAY" -> BlueMedical
        else -> AmberWarning
    }
}

private fun formatIncidentTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown time"

    val now = System.currentTimeMillis()
    val age = (now - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour

    return when {
        age < minute -> "Just now"
        age < hour -> "${age / minute}m ago"
        age < day -> "${age / hour}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun targetAgencyTypeForIncident(incident: IncidentEntity): String? {
    incident.description
        ?.substringAfter("Target agency: ", missingDelimiterValue = "")
        ?.substringBefore("|")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return when (incident.emergencyType.uppercase()) {
        "MEDICAL" -> "MEDICAL"
        "FIRE" -> "FIRE"
        "CRIME" -> "POLICE"
        "ACCIDENT" -> "RESCUE"
        "DISASTER" -> "DRRMO"
        else -> null
    }
}