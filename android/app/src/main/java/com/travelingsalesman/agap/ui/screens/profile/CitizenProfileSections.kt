package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.remote.dto.UserDeviceDto
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import java.util.Locale

@Composable
internal fun ProfileSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AgapSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AgapShape.lg,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(AgapSpacing.lg), content = content)
        }
    }
}
@Composable
internal fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AgapSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MedicalProfileCard(
    medicalNotes: String,
    onEdit: () -> Unit
) {
    val configured = medicalNotes.isNotBlank()
    val accent = if (configured) AgapStatus.info else AgapStatus.warning

    ProfileSection(
        title = "Medical profile",
        subtitle = "Keep important conditions, allergies, or medical notes concise and current.",
        icon = Icons.Outlined.MedicalServices,
        actionLabel = "EDIT",
        onAction = onEdit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AgapShape.md,
            color = accent.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
        ) {
            Row(
                modifier = Modifier.padding(AgapSpacing.md),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (configured) {
                        Icons.Outlined.HealthAndSafety
                    } else {
                        Icons.Outlined.Info
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AgapSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (configured) "MEDICAL NOTES" else "MEDICAL INFORMATION NOT SET",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(AgapSpacing.xs))
                    Text(
                        text = if (configured) medicalNotes else {
                            "Add only information you want stored in your AGAP profile."
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmergencyContactsSection(
    contacts: List<EmergencyContactDto>,
    isBusy: Boolean,
    onAdd: () -> Unit,
    onCall: (String) -> Unit,
    onEdit: (EmergencyContactDto) -> Unit,
    onSetPrimary: (Long) -> Unit,
    onDelete: (EmergencyContactDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AgapSpacing.xs),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EMERGENCY NETWORK",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "Emergency contacts",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "People you may need to reach quickly during an emergency.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = onAdd, enabled = !isBusy) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(AgapSpacing.xs))
                Text("Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(AgapSpacing.sm))

        if (contacts.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy, onClick = onAdd),
                shape = AgapShape.lg,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = AgapSpacing.xl, vertical = AgapSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(Modifier.height(AgapSpacing.md))
                    Text(
                        text = "No emergency contacts yet",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add at least one trusted contact and mark the preferred person as primary.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(AgapSpacing.sm))
                    Text(
                        text = "+ ADD EMERGENCY CONTACT",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                contacts.sortedByDescending { it.isPrimary }.forEach { contact ->
                    EmergencyContactCard(
                        contact = contact,
                        isBusy = isBusy,
                        onCall = { onCall(contact.phoneNumber) },
                        onEdit = { onEdit(contact) },
                        onSetPrimary = { onSetPrimary(contact.id) },
                        onDelete = { onDelete(contact) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmergencyContactCard(
    contact: EmergencyContactDto,
    isBusy: Boolean,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (contact.isPrimary) AgapStatus.success else AgapStatus.info

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (contact.isPrimary) accent.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = 0.11f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Call,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.contactName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (contact.isPrimary) {
                            Spacer(Modifier.width(AgapSpacing.sm))
                            CompactStatusChip(
                                label = "PRIMARY",
                                color = AgapStatus.success,
                                icon = Icons.Outlined.Star
                            )
                        }
                    }
                    Text(
                        text = contact.relationship.ifBlank { "Emergency contact" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = contact.phoneNumber,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(AgapSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(AgapSpacing.sm))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                if (!contact.isPrimary) {
                    TextButton(onClick = onSetPrimary, enabled = !isBusy) {
                        Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(AgapSpacing.xs))
                        Text("Primary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onCall, enabled = contact.phoneNumber.isNotBlank()) {
                    Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(AgapSpacing.xs))
                    Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onEdit, enabled = !isBusy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit contact",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(onClick = onDelete, enabled = !isBusy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete contact",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.09f), AgapShape.md),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(AgapSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.width(AgapSpacing.sm))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
internal fun RegisteredDevicesSection(
    devices: List<UserDeviceDto>,
    currentDeviceId: String?,
    isBusy: Boolean,
    onDeleteDevice: (UserDeviceDto) -> Unit
) {
    ProfileSection(
        title = "Registered devices (${devices.size})",
        subtitle = "Physical phones linked to this account for emergency relaying and uploads.",
        icon = Icons.Outlined.Smartphone
    ) {
        if (devices.isEmpty()) {
            Text(
                text = "No devices currently registered.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = AgapSpacing.sm)
            )
        } else {
            devices.forEachIndexed { index, device ->
                if (index > 0) SectionDivider()
                UserDeviceRow(
                    device = device,
                    isCurrentDevice = !currentDeviceId.isNullOrBlank() && device.deviceHardwareId == currentDeviceId,
                    isBusy = isBusy,
                    onDelete = { onDeleteDevice(device) }
                )
            }
        }
    }
}

@Composable
internal fun UserDeviceRow(
    device: UserDeviceDto,
    isCurrentDevice: Boolean,
    isBusy: Boolean,
    onDelete: () -> Unit
) {
    val accent = if (isCurrentDevice) AgapStatus.success else AgapStatus.info

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(accent.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(AgapSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.deviceName.ifBlank { device.platform.ifBlank { "Android Device" } },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isCurrentDevice) {
                    Spacer(Modifier.width(AgapSpacing.sm))
                    CompactStatusChip(
                        label = "THIS DEVICE",
                        color = AgapStatus.success,
                        icon = Icons.Outlined.CheckCircle
                    )
                }
            }
            Text(
                text = "Hardware ID: ${device.deviceHardwareId}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            device.lastSeenAt?.let { lastSeen ->
                Text(
                    text = "Last active: ${formatTimestamp(lastSeen)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!isCurrentDevice) {
            IconButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Unlink device",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = AgapSpacing.section),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    )
}
