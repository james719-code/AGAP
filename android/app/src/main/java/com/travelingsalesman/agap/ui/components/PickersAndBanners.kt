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
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.OrangeAccident
import com.travelingsalesman.agap.ui.theme.PurpleCrime
import com.travelingsalesman.agap.ui.theme.RedPrimary

enum class EmergencyType(
    val displayName: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val iconTintColor: Color
) {
    CRIME("Crime", Icons.Default.LocalPolice, Color(0xFF2A1B3D), PurpleCrime),
    MEDICAL("Medical", Icons.Default.LocalHospital, Color(0xFF3B1822), RedPrimary),
    ACCIDENT("Accident", Icons.Default.CarCrash, Color(0xFF3A2B15), OrangeAccident),
    DISASTER("Disaster", Icons.Default.Warning, Color(0xFF143024), GreenOnline)
}

@Composable
fun EmergencyTypePicker(
    selectedType: EmergencyType,
    onTypeSelected: (EmergencyType) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUICK ALERTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Select emergency type",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmergencyType.entries.forEach { type ->
                val isSelected = type == selectedType
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) type.iconTintColor else borderCol,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onTypeSelected(type) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(type.iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = type.displayName,
                            tint = type.iconTintColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = type.displayName,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontSize = 11.sp
                    )
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
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = subTextColor,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) GreenOnline else RedPrimary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "OPERATIONAL" else "BLE MESH ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isOnline) GreenOnline else RedPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = null,
                        tint = GreenOnline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Primary Channel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(text = "Mobile Data", fontSize = 10.sp, color = subTextColor)
                    }
                }
                Text(
                    text = if (isOnline) "Connected" else "Unavailable",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) GreenOnline else RedPrimary
                )
            }

            // Fallback Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Fallback Channel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text(text = "BLE Mesh", fontSize = 10.sp, color = subTextColor)
                    }
                }
                Text(
                    text = if (isOnline) "Standby" else "Active Relay",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
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
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = if (isOnline) GreenOnline.copy(alpha = 0.4f) else RedPrimary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) GreenOnline else RedPrimary)
            )
            Text(
                text = if (isOnline) "ONLINE MODE (REST Uplink Active)" else "OFFLINE MODE (BLE Mesh Active)",
                color = if (isOnline) GreenOnline else RedPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.25.sp
            )
        }

        if (pendingPacketCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RedPrimary.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$pendingPacketCount Queued",
                    color = RedPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


