package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PremiumIncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeVisual = incidentTypeVisual(incident.emergencyType)
    val agency = targetAgencyTypeForIncident(incident)
    val channelLabel = incidentChannelLabel(incident)
    val channelColor = incidentChannelColor(incident)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.xl)
            .clickable(onClick = onClick)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Accent Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(typeVisual.color)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header Row: Icon + Info + Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Premium Dark Icon Box
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(AgapShape.lg)
                            .background(Color(0xFF1A1C1E)) // Deep dark for contrast
                            .border(1.dp, typeVisual.color.copy(alpha = 0.25f), AgapShape.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeVisual.icon,
                            contentDescription = null,
                            tint = typeVisual.color,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = incident.emergencyType.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Incident #${incident.incidentId.take(8).uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    StatusBadge(status = incident.status)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Row 1: Time + Connectivity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatIncidentTimestamp(incident.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = if (channelLabel.contains("MESH")) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = channelColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = channelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = channelColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Row 2: Agency Tags
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PremiumTag(text = "AGENCY", color = typeVisual.color.copy(alpha = 0.2f), textColor = typeVisual.color)
                    Spacer(modifier = Modifier.width(8.dp))
                    agency?.let {
                        PremiumTag(text = it, color = typeVisual.color.copy(alpha = 0.35f), textColor = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Row: Location + Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "%.5f, %.5f".format(Locale.US, incident.latitude, incident.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onClick() }
                    ) {
                        Text(
                            text = "VIEW DETAILS",
                            style = MaterialTheme.typography.labelSmall,
                            color = RedPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTag(text: String, color: Color, textColor: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

private data class TypeVisual(val color: Color, val icon: ImageVector)

private fun incidentTypeVisual(type: String): TypeVisual = when (type.uppercase()) {
    "MEDICAL" -> TypeVisual(BlueMedical, Icons.Default.LocalHospital)
    "CRIME" -> TypeVisual(PurpleCrime, Icons.Default.Security)
    "ACCIDENT" -> TypeVisual(OrangeAccident, Icons.Default.DirectionsCar)
    "FIRE" -> TypeVisual(RedPrimary, Icons.Default.Whatshot)
    "DISASTER" -> TypeVisual(RedPrimary, Icons.Default.Warning)
    else -> TypeVisual(RedPrimary, Icons.Default.Info)
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

private fun incidentChannelColor(incident: IncidentEntity): Color = when (incidentChannelLabel(incident)) {
    "ONLINE" -> GreenOnline
    "MESH RELAY" -> BlueMedical
    else -> AmberWarning
}

private fun formatIncidentTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    val age = System.currentTimeMillis() - timestamp
    val minute = 60_000L
    val hour = 60L * minute
    return when {
        age < minute -> "Just now"
        age < hour -> "${age / minute}m ago"
        age < 24 * hour -> "${age / hour}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun targetAgencyTypeForIncident(incident: IncidentEntity): String? {
    incident.description?.substringAfter("Target agency: ", "")?.substringBefore("|")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return when (incident.emergencyType.uppercase()) {
        "MEDICAL" -> "MEDICAL"
        "FIRE" -> "FIRE"
        "CRIME" -> "POLICE"
        "ACCIDENT" -> "RESCUE"
        "DISASTER" -> "DRRMO"
        else -> null
    }
}
