@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminAlertCard(
    alert: AdminAlertEntity,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    val severityColor = when (alert.severity.uppercase()) {
        "EMERGENCY", "CRITICAL" -> MaterialTheme.colorScheme.error
        "WARNING" -> AgapStatus.warning
        else -> AgapStatus.info
    }
    
    val icon = when (alert.category.uppercase()) {
        "WEATHER" -> Icons.Outlined.Cloud
        "EVACUATION" -> Icons.Outlined.Warning
        "SAFETY" -> Icons.Outlined.HealthAndSafety
        "HEALTH" -> Icons.Outlined.MedicalServices
        "INFRASTRUCTURE" -> Icons.Outlined.Engineering
        else -> Icons.Outlined.Campaign
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.xl,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (alert.isRead) null else androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(severityColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = severityColor, modifier = Modifier.size(20.dp))
                }
                
                Spacer(Modifier.width(AgapSpacing.md))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alert.severity.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (alert.isPlaceholder) {
                    com.travelingsalesman.agap.ui.components.StatusBadge(status = "OFFLINE")
                }
            }
            
            Spacer(Modifier.height(AgapSpacing.md))
            
            Text(
                text = alert.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(AgapSpacing.md))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Text(
                    text = "By ${alert.authorName ?: "Emergency Command"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val timeStr = remember(alert.publishedAt) {
                    SimpleDateFormat("HH:mm • MMM d", Locale.getDefault()).format(Date(alert.publishedAt))
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AnnouncementCard(
    announcement: NotificationEntity,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    val (tag, color, icon) = when {
        announcement.title.contains("Severe", ignoreCase = true) || 
        announcement.message.contains("Urgent", ignoreCase = true) -> 
            Triple("High priority", MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        
        announcement.title.contains("Road", ignoreCase = true) || 
        announcement.title.contains("Closure", ignoreCase = true) -> 
            Triple("Advisory", AgapStatus.warning, Icons.Outlined.MinorCrash)
            
        announcement.title.contains("Flood", ignoreCase = true) || 
        announcement.title.contains("Water", ignoreCase = true) -> 
            Triple("Update", AgapStatus.info, Icons.Outlined.Flood)

        announcement.title.contains("Normal", ignoreCase = true) || 
        announcement.title.contains("Operational", ignoreCase = true) -> 
            Triple("General", AgapStatus.success, Icons.Outlined.GppGood)

        else -> Triple("Public service", AgapStatus.info, Icons.Outlined.Campaign)
    }

    val dateStr = remember(announcement.createdAt) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(announcement.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .clickable(onClick = onClick)
            .padding(AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Icon Box
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(AgapShape.lg)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(AgapSpacing.md))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = AgapShape.xs
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = subTextColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = announcement.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Text(
                text = announcement.message,
                style = MaterialTheme.typography.bodySmall,
                color = subTextColor,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(AgapSpacing.sm))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun LatestAnnouncementsHeader(
    count: Int,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Latest updates", color = textColor, style = MaterialTheme.typography.titleMedium)
        Text("$count updates", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AdminInfoBox(
    subTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerShape = AgapShape.lg, 
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
            .padding(AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = AgapStatus.info,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Text(
            text = "Only direct announcements from admins will be shown in this feed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RadioTowerIllustration() {
    val primaryRed = RedPrimary
    val towerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // Draw Signal Waves with Gradient
            for (i in 1..3) {
                val radius = 30f + (i * 30f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to primaryRed.copy(alpha = 0.6f / i),
                        1.0f to Color.Transparent,
                        center = Offset(centerX, centerY - 20f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(centerX, centerY - 20f),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Draw Red Light Pulse
            drawCircle(
                color = primaryRed,
                radius = 8f,
                center = Offset(centerX, centerY - 20f)
            )
            drawCircle(
                color = primaryRed.copy(alpha = 0.3f),
                radius = 16f,
                center = Offset(centerX, centerY - 20f)
            )

            // Draw Tower (Modern structure)
            val towerHeight = 120f
            val towerWidth = 70f
            val towerTop = Offset(centerX, centerY - 20f)
            
            drawLine(
                color = towerColor,
                start = towerTop,
                end = Offset(centerX - towerWidth/2, centerY + towerHeight),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = towerColor,
                start = towerTop,
                end = Offset(centerX + towerWidth/2, centerY + towerHeight),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Modern Cross bars (lattice)
            for (i in 1..4) {
                val y = centerY - 20f + (towerHeight / 5 * i)
                val currentWidth = towerWidth * (i.toFloat() / 5)
                
                // Horizontal
                drawLine(
                    color = towerColor.copy(alpha = 0.8f),
                    start = Offset(centerX - currentWidth, y),
                    end = Offset(centerX + currentWidth, y),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Crosses
                if (i < 4) {
                    val nextY = centerY - 20f + (towerHeight / 5 * (i+1))
                    val nextWidth = towerWidth * ((i.toFloat()+1) / 5)
                    drawLine(
                        color = towerColor.copy(alpha = 0.4f),
                        start = Offset(centerX - currentWidth, y),
                        end = Offset(centerX + nextWidth, nextY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = towerColor.copy(alpha = 0.4f),
                        start = Offset(centerX + currentWidth, y),
                        end = Offset(centerX - nextWidth, nextY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun LocalSosCard(
    report: LocalIncidentReportEntity,
    isCitizen: Boolean = false,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onOpenIncidentTimeline: (() -> Unit)? = null
) {
    val createdStr = remember(report.observedTimestampMs) {
        SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(report.observedTimestampMs))
    }

    val syncState = report.syncState.uppercase(Locale.ROOT)
    val (syncLabel, syncColor) = when (syncState) {
        "SYNCED" -> if (isCitizen) Pair("RECEIVED BY DISPATCH", GreenOnline) else Pair("DELIVERED TO COMMAND", GreenOnline)
        "BLE_ONLY" -> if (isCitizen) Pair("SEARCHING NEARBY RELAYS", BlueMedical) else Pair("GATT MESH SEARCHING", BlueMedical)
        "QUEUED_OFFLINE" -> if (isCitizen) Pair("SAVED (WAITING FOR CONNECTION)", AmberWarning) else Pair("QUEUED (OFFLINE)", AmberWarning)
        else -> if (isCitizen) Pair("SENDING SOS...", BlueMedical) else Pair("PENDING UPLINK", BlueMedical)
    }
    val deliveryDetail = when {
        onOpenIncidentTimeline != null ->
            "Command received this SOS. Open the incident timeline for dispatch updates."
        syncState == "SYNCED" ->
            "Command receipt is recorded. An incident timeline will appear when a reference is available."
        syncState == "BLE_ONLY" ->
            "Saved on this device. A nearby relay is still needed before command can receive it."
        syncState == "QUEUED_OFFLINE" ->
            "Saved on this device. Command has not received it yet; delivery will retry when a connection is available."
        else ->
            "Saved locally and still attempting delivery. Command has not confirmed receipt yet."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.md)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = EmergencyType.fromCode(report.emergencyType).color
                Text(
                    text = report.emergencyType.uppercase(),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = syncLabel,
                    color = syncColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))

            val refText = if (isCitizen) {
                report.serverIncidentId?.let { "Reference ID: #$it" } ?: "Ref: ${report.transportToken.take(8)}"
            } else {
                "Token: ${report.transportToken}"
            }

            Text(
                text = refText,
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )

            val lat = report.latitude
            val lon = report.longitude
            val locText = if (isCitizen) {
                if (lat != null && lon != null) "GPS Location Pinpointed" else "Location: GPS Pending"
            } else {
                if (lat != null && lon != null) "Lat: %.4f, Lon: %.4f".format(lat, lon) else "Location: Offline / GPS Pending"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = subTextColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(AgapSpacing.xs))
                Text(text = locText, color = subTextColor, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(
                text = deliveryDetail,
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            if (!isCitizen && report.serverIncidentId != null) {
                Text(
                    text = "Server Incident ID: #${report.serverIncidentId}",
                    color = GreenOnline,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(
                text = if (isCitizen) "Sent: $createdStr" else "Created: $createdStr",
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            onOpenIncidentTimeline?.let { openTimeline ->
                Spacer(modifier = Modifier.height(AgapSpacing.md))
                OutlinedButton(
                    onClick = openTimeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = AgapShape.sm
                ) {
                    Text(
                        text = "VIEW INCIDENT UPDATES",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

@Composable
fun EmptyStatePlaceholder(
    message: String,
    subMessage: String = "",
    textColor: Color,
    subTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = AgapSpacing.section + AgapSpacing.md),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerShape = AgapShape.xxl)
                .padding(vertical = AgapSpacing.xxl, horizontal = AgapSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RadioTowerIllustration()

            Spacer(modifier = Modifier.height(AgapSpacing.xxl))

            Text(
                text = message,
                color = textColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            if (subMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AgapSpacing.sm))
                Text(
                    text = subMessage,
                    color = subTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AgapSpacing.md)
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            AdminInfoBox(
                subTextColor = subTextColor
            )
        }
    }
}

@Composable
fun DisabledMessageBar(
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(AgapSpacing.lg)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(
                        cornerShape = AgapShape.pill, 
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        borderColor = borderColor.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = AgapSpacing.lg, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                            tint = RedPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Direct replies are disabled for admin announcements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
