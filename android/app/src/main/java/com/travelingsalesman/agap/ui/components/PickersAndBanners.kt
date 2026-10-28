package com.travelingsalesman.agap.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.WifiTethering
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.EmergencyType
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary

import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

data class EmergencyTypeOption(
    val code: String,
    val displayName: String,
    val targetAgencyType: String? = null
) {
    private val canonicalType: EmergencyType
        get() = EmergencyType.fromCode(code)

    val icon: ImageVector
        get() = canonicalType.icon

    @Composable
    fun iconTintColor(): Color = canonicalType.color

    @Composable
    fun iconBgColor(): Color = canonicalType.color.copy(alpha = 0.15f)
}

fun defaultEmergencyTypeOptions(): List<EmergencyTypeOption> {
    val agencyByCode = mapOf(
        "MEDICAL" to "MEDICAL",
        "FIRE" to "FIRE",
        "CRIME" to "POLICE",
        "ACCIDENT" to "RESCUE",
        "DISASTER" to "DRRMO"
    )
    return EmergencyType.entries.map { EmergencyTypeOption(it.name, it.label, agencyByCode[it.name]) }
}

@Composable
fun EmergencyTypePicker(
    selectedTypeCode: String,
    onTypeSelected: (String) -> Unit,
    emergencyTypes: List<EmergencyTypeOption>,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUICK ALERTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = AgapSpacing.xs)
        )
        Text(
            text = "Select emergency type",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = AgapSpacing.sm)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            val rows = emergencyTypes.ifEmpty { defaultEmergencyTypeOptions() }.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                rows.forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        rowTypes.forEach { type ->
                            val isSelected = type.code.equals(selectedTypeCode, ignoreCase = true)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(AgapShape.lg)
                                    .background(cardBg)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) type.iconTintColor() else borderCol,
                                        shape = AgapShape.lg
                                    )
                                    .clickable { onTypeSelected(type.code) }
                                    .padding(vertical = AgapSpacing.md, horizontal = AgapSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(AgapShape.sm)
                                        .background(type.iconBgColor()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = type.displayName,
                                        tint = type.iconTintColor(),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(AgapSpacing.xs))

                                Text(
                                    text = type.displayName,
                                    color = textColor,
                                    style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge
                                )
                                type.targetAgencyType?.let { agencyType ->
                                    Text(
                                        text = agencyType,
                                        color = type.iconTintColor(),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        repeat(3 - rowTypes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemStatusCard(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.lg)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = subTextColor,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) GreenOnline else AmberWarning)
                    )
                    Spacer(modifier = Modifier.width(AgapSpacing.xs))
                    Text(
                        text = if (isOnline) "OPERATIONAL" else "BLE MESH ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) GreenOnline else AmberWarning,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            // Primary Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgapSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CellTower,
                        contentDescription = null,
                        tint = GreenOnline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(AgapSpacing.sm))
                    Column {
                        Text(text = "Primary Channel", style = MaterialTheme.typography.labelMedium, color = textColor)
                        Text(text = "Mobile Data", style = MaterialTheme.typography.bodySmall, color = subTextColor)
                    }
                }
                Text(
                    text = if (isOnline) "Connected" else "Standby",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOnline) GreenOnline else subTextColor
                )
            }

            // Fallback Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgapSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.WifiTethering,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(AgapSpacing.sm))
                    Column {
                        Text(text = "Fallback Channel", style = MaterialTheme.typography.labelMedium, color = textColor)
                        Text(text = "BLE Store-Carry-Forward Relay", style = MaterialTheme.typography.bodySmall, color = subTextColor)
                    }
                }
                Text(
                    text = if (isOnline) "Standby" else "Active Relay",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOnline) subTextColor else GreenOnline
                )
            }
        }
    }
}

@Composable
fun StatusBanner(
    isOnline: Boolean,
    pendingPacketCount: Int,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val statusColor = if (isOnline) GreenOnline else AmberWarning

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AgapShape.lg)
            .background(cardBg)
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.35f),
                shape = AgapShape.lg
            )
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = AgapSpacing.sm)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = if (isOnline) "ONLINE MODE (REST Uplink Active)" else "OFFLINE MODE (BLE Delay-Tolerant Relay Active)",
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.25.sp
            )
        }

        if (pendingPacketCount > 0) {
            Box(
                modifier = Modifier
                    .clip(AgapShape.xs)
                    .background(AmberWarning.copy(alpha = 0.18f))
                    .padding(horizontal = AgapSpacing.sm, vertical = 2.dp)
            ) {
                Text(
                    text = "$pendingPacketCount Queued",
                    color = AmberWarning,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
