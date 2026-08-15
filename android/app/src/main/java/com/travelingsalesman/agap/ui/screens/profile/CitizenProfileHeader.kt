package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.PaletteBlueDark
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard

@Composable
internal fun PremiumProfileHeader(
    name: String,
    role: String,
    accountStatus: String?,
    isVerified: Boolean,
    memberSince: String?,
    lastLoginAt: String?
) {
    val statusColor = accountStatusColor(accountStatus)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl),
        color = MaterialTheme.colorScheme.surface,
        shape = AgapShape.xl,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.48f), CircleShape)
                        .padding(AgapSpacing.xs)
                        .border(1.dp, statusColor.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AGAP IDENTITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.9.sp
                    )
                    Spacer(Modifier.height(AgapSpacing.xs))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(AgapSpacing.xs))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactStatusChip(
                            label = role,
                            color = PaletteBlueDark,
                            icon = Icons.Outlined.Badge
                        )
                        if (isVerified) {
                            CompactStatusChip(
                                label = "VERIFIED",
                                color = GreenActive,
                                icon = Icons.Outlined.VerifiedUser
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AgapSpacing.lg))
            HorizontalDivider(color = statusColor.copy(alpha = 0.16f))
            Spacer(Modifier.height(AgapSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "STATUS",
                    value = accountStatus?.replace("_", " ")?.uppercase() ?: "UNKNOWN",
                    color = statusColor
                )
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "MEMBER SINCE",
                    value = formatDateCompact(memberSince),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "LAST LOGIN",
                    value = formatDateCompact(lastLoginAt),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun HeaderMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.md,
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(AgapSpacing.xs))
            Text(
                text = value,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CompactStatusChip(
    label: String,
    color: Color,
    icon: ImageVector? = null
) {
    Surface(
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        shape = AgapShape.pill
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(AgapSpacing.xs))
            }
            Text(
                text = label,
                color = color,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
internal fun EmergencyReadinessCard(
    progress: Float,
    completed: Int,
    total: Int,
    emergencyContacts: Int,
    autoGps: Boolean,
    bleFallback: Boolean,
    pendingSync: Int
) {
    val readinessColor = when {
        progress >= 0.85f -> GreenActive
        progress >= 0.60f -> PaletteOrangeDark
        else -> RedPrimary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(readinessColor.copy(alpha = 0.12f), AgapShape.md),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = readinessColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMERGENCY PROFILE READINESS",
                        color = readinessColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = "$completed of $total essentials configured",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = readinessColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(AgapSpacing.md))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(AgapShape.pill),
                color = readinessColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(AgapSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Call,
                    value = emergencyContacts.toString(),
                    label = "CONTACTS",
                    active = emergencyContacts > 0,
                    activeColor = GreenActive
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.LocationOn,
                    value = if (autoGps) "ON" else "OFF",
                    label = "AUTO GPS",
                    active = autoGps,
                    activeColor = PaletteBlueDark
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Bluetooth,
                    value = if (bleFallback) "READY" else "OFF",
                    label = "BLE RELAY",
                    active = bleFallback,
                    activeColor = PaletteBlueDark
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Sync,
                    value = pendingSync.toString(),
                    label = "PENDING",
                    active = pendingSync == 0,
                    activeColor = if (pendingSync == 0) GreenActive else PaletteOrangeDark
                )
            }
        }
    }
}

@Composable
internal fun ReadinessMetric(
    icon: ImageVector,
    value: String,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.07f),
        shape = AgapShape.md
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgapSpacing.xs, vertical = AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.height(AgapSpacing.xs))
            Text(
                text = value,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
