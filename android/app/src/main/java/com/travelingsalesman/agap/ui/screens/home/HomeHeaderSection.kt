package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard

@Composable
internal fun CitizenCommandHeader(
    username: String?,
    userDisplayId: String?,
    isOnline: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    hasLocationFix: Boolean,
    meshPeers: Int
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .padding(AgapSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) GreenOnline else AmberWarning)
                )
                Spacer(Modifier.size(AgapSpacing.xs))
                Text(
                    text = userDisplayId?.let { "UNIT #$it" } ?: (username?.uppercase() ?: "CITIZEN DISPATCH"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Surface(
                color = (if (isOnline) GreenOnline else AmberWarning).copy(alpha = 0.12f),
                shape = AgapShape.pill
            ) {
                Text(
                    text = if (isOnline) "NETWORK READY" else "MESH STANDBY",
                    modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = 2.dp),
                    color = if (isOnline) GreenOnline else AmberWarning,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(Modifier.height(AgapSpacing.sm))

        Text(
            text = "EMERGENCY ACCESS",
            color = primaryColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

        Spacer(Modifier.height(AgapSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = if (isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                label = if (isOnline) "ONLINE" else "OFFLINE",
                accent = if (isOnline) GreenOnline else AmberWarning
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocationOn,
                label = when {
                    !hasLocationPermission -> "NO ACCESS"
                    !gpsEnabled -> "GPS OFF"
                    hasLocationFix -> "GPS READY"
                    else -> "GPS ON"
                },
                accent = when {
                    !hasLocationPermission -> AmberWarning
                    !gpsEnabled -> primaryColor
                    hasLocationFix -> GreenOnline
                    else -> BlueMedical
                }
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Bluetooth,
                label = "$meshPeers PEER${if (meshPeers == 1) "" else "S"}",
                accent = if (meshPeers > 0) BlueMedical else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ReadinessPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = AgapShape.pill
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.sm,
                vertical = AgapSpacing.sm
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.size(AgapSpacing.xs))
            Text(
                text = label,
                color = accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun DetailedReportAction(
    onClick: () -> Unit,
    isOnline: Boolean,
    transmissionMode: TransmissionMode,
    modifier: Modifier = Modifier
) {
    val routeAccent = if (isOnline && transmissionMode != TransmissionMode.FORCE_BLE_MESH) {
        GreenOnline
    } else {
        AmberWarning
    }
    val routeMessage = if (isOnline && transmissionMode != TransmissionMode.FORCE_BLE_MESH) {
        "Ready to upload securely"
    } else {
        "Saved locally • relay will continue automatically"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.lg,
        color = routeAccent.copy(alpha = 0.065f),
        border = BorderStroke(
            1.dp,
            routeAccent.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.lg,
                vertical = AgapSpacing.md
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(routeAccent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                    tint = routeAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Text(
                    text = "Report Incident",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(routeAccent, CircleShape)
                    )
                    Spacer(Modifier.size(AgapSpacing.sm))
                    Text(
                        text = routeMessage,
                        color = routeAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "ADD",
                color = routeAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
