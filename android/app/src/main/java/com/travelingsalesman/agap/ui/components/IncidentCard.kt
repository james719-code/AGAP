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
        // A thin incident-type accent makes cards easier to scan without tinting the whole card.
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(152.dp)
                .align(Alignment.CenterStart)
                .background(typeVisual.color)
        )

        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 14.dp,
                top = 14.dp,
                bottom = 13.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AgapShape.md)
                        .background(typeVisual.color.copy(alpha = 0.14f))
                        .border(
                            width = 1.dp,
                            color = typeVisual.color.copy(alpha = 0.32f),
                            shape = AgapShape.md
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeVisual.icon,
                        contentDescription = incident.emergencyType,
                        tint = typeVisual.color,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                                text = "Incident #${incident.incidentId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = incident.status)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactMeta(
                            icon = Icons.Default.AccessTime,
                            text = formatIncidentTimestamp(incident.timestamp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
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
            }

            Spacer(modifier = Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = AgapShape.sm,
                    color = typeVisual.color.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "AGENCY  ${agency ?: "UNASSIGNED"}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = typeVisual.color,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.35.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (incident.isDirty) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Surface(
                        shape = AgapShape.sm,
                        color = AmberWarning.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "PENDING SYNC",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            cleanDescription?.let { description ->
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "%.5f, %.5f".format(
                        Locale.US,
                        incident.latitude,
                        incident.longitude
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "VIEW DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    return when (type.uppercase()) {
        "MEDICAL" -> IncidentTypeVisual(BlueMedical, Icons.Default.LocalHospital)
        "CRIME" -> IncidentTypeVisual(PurpleCrime, Icons.Default.LocalPolice)
        "ACCIDENT" -> IncidentTypeVisual(OrangeAccident, Icons.Default.CarCrash)
        "FIRE" -> IncidentTypeVisual(RedPrimary, Icons.Default.Warning)
        "DISASTER" -> IncidentTypeVisual(RedPrimary, Icons.Default.Warning)
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