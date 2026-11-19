@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.AgencyType
import com.travelingsalesman.agap.ui.theme.EmergencyType as ThemeEmergencyType
import com.travelingsalesman.agap.ui.theme.glassCard
import com.travelingsalesman.agap.ui.theme.contentColorForAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class IncidentCardVariant {
    Compact,
    Detailed
}

/**
 * The shared presentation for an incident across list and responder-dispatch surfaces.
 *
 * [IncidentCardVariant.Compact] preserves the scan-friendly incident feed density, while
 * [IncidentCardVariant.Detailed] retains the operational context required in the responder queue.
 */
@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IncidentCardVariant = IncidentCardVariant.Compact
) {
    val emergencyType = ThemeEmergencyType.fromCode(incident.emergencyType)
    val agency = targetAgencyTypeForIncident(incident)
    val channelLabel = incidentChannelLabel(incident)
    val channelColor = incidentChannelColor(channelLabel)

    when (variant) {
        IncidentCardVariant.Compact -> CompactIncidentCard(
            incident = incident,
            emergencyType = emergencyType,
            channelLabel = channelLabel,
            channelColor = channelColor,
            onClick = onClick,
            modifier = modifier
        )

        IncidentCardVariant.Detailed -> DetailedIncidentCard(
            incident = incident,
            emergencyType = emergencyType,
            agency = agency,
            channelLabel = channelLabel,
            channelColor = channelColor,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactIncidentCard(
    incident: IncidentEntity,
    emergencyType: ThemeEmergencyType,
    channelLabel: String,
    channelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
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
                .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AgapShape.md)
                    .background(emergencyType.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = emergencyType.icon,
                    contentDescription = emergencyType.label,
                    tint = contentColorForAccent(emergencyType.color),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(AgapSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
                ) {
                    Text(
                        text = emergencyType.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
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

                Spacer(modifier = Modifier.height(AgapSpacing.xs))

                StatusBadge(status = incident.status)

                Spacer(modifier = Modifier.height(AgapSpacing.xs))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    IncidentMeta(
                        icon = Icons.Outlined.AccessTime,
                        text = formatIncidentTimestamp(
                            timestamp = incident.timestamp,
                            variant = IncidentCardVariant.Compact
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IncidentMeta(
                        icon = if (channelLabel.contains("Mesh", ignoreCase = true)) {
                            Icons.Outlined.Bluetooth
                        } else {
                            Icons.Outlined.Wifi
                        },
                        text = channelLabel,
                        tint = channelColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(AgapSpacing.sm))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DetailedIncidentCard(
    incident: IncidentEntity,
    emergencyType: ThemeEmergencyType,
    agency: String?,
    channelLabel: String,
    channelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val agencyColor = agency
        ?.let { AgencyType.fromCode(it)?.color }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.lg)
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(emergencyType.color)
            )

            Column(
                modifier = Modifier
                    .padding(AgapSpacing.lg)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(AgapShape.lg)
                            .background(emergencyType.color)
                            .border(1.dp, emergencyType.color.copy(alpha = 0.25f), AgapShape.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = emergencyType.icon,
                            contentDescription = emergencyType.label,
                            tint = contentColorForAccent(emergencyType.color),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AgapSpacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = emergencyType.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp
                        )
                        Text(
                            text = "Incident #${incident.incidentId.take(8).uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(AgapSpacing.xs))
                        StatusBadge(status = incident.status)
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    IncidentMeta(
                        icon = Icons.Outlined.AccessTime,
                        text = formatIncidentTimestamp(
                            timestamp = incident.timestamp,
                            variant = IncidentCardVariant.Detailed
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                    IncidentMeta(
                        icon = if (channelLabel.contains("Mesh", ignoreCase = true)) {
                            Icons.Outlined.Bluetooth
                        } else {
                            Icons.Outlined.Wifi
                        },
                        text = channelLabel,
                        tint = channelColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    IncidentTag(
                        text = "Agency",
                        color = agencyColor.copy(alpha = 0.2f),
                        textColor = agencyColor
                    )
                    agency?.let {
                        IncidentTag(
                            text = it,
                            color = agencyColor.copy(alpha = 0.35f),
                            textColor = agencyColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(AgapSpacing.xs))
                        Text(
                            text = "%.5f, %.5f".format(Locale.US, incident.latitude, incident.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "View details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentMeta(
    icon: ImageVector,
    text: String,
    tint: Color,
    fontWeight: FontWeight = FontWeight.Medium
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(AgapSpacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IncidentTag(text: String, color: Color, textColor: Color) {
    Surface(
        color = color,
        shape = AgapShape.xs
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.sp
        )
    }
}

private fun incidentChannelLabel(incident: IncidentEntity): String {
    val channel = incident.channel.uppercase()
    val deliveryPath = incident.deliveryPath.uppercase()

    return when {
        channel.contains("BLE") || deliveryPath.contains("MESH") -> "Mesh relay"
        channel.contains("OFFLINE") -> "Offline"
        else -> "Online"
    }
}

@Composable
private fun incidentChannelColor(channelLabel: String): Color = when (channelLabel) {
    "Online" -> AgapStatus.success
    "Mesh relay" -> AgapStatus.info
    else -> AgapStatus.warning
}

private fun formatIncidentTimestamp(timestamp: Long, variant: IncidentCardVariant): String {
    if (timestamp <= 0L) {
        return if (variant == IncidentCardVariant.Compact) "Unknown time" else "Unknown"
    }

    val now = System.currentTimeMillis()
    val age = (now - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour

    return when {
        age < minute -> "Just now"
        age < hour -> "${age / minute}m ago"
        age < day -> "${age / hour}h ago"
        variant == IncidentCardVariant.Compact ->
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
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
