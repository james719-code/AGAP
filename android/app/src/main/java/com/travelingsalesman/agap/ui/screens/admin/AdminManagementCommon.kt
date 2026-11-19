package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.AgencyType
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary
import java.util.Locale

internal const val MANAGEMENT_BATCH_SIZE = 15
internal const val MANAGEMENT_LARGE_BATCH_SIZE = 30

internal val agencyTypes = listOf("DRRMO", "RESCUE", "POLICE", "FIRE", "MEDICAL", "VOLUNTEER")
internal val responderStatuses = listOf("IDLE", "DISPATCHED", "OFFLINE")

internal data class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean = true,
    val onConfirm: () -> Unit
)

internal data class UserDetail(
    val label: String,
    val value: String,
    val maxLines: Int = 1
)

internal data class OverviewMetric(
    val label: String,
    val value: Int,
    val color: Color
)

@Composable
internal fun ManagementTabs(
    selectedTab: Int,
    onSelected: (Int) -> Unit
) {
    val labels = listOf("Users", "Agencies", "Units")

    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelected(index) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Composable
internal fun ManagementFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer
        )
    )
}

@Composable
internal fun ManagementLoadMore(
    showing: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    OutlinedButton(
        onClick = onLoadMore,
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Text(
            text = "SHOW MORE  •  $showing / $total",
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.45.sp
        )
    }
}

@Composable
internal fun ManagementOverview(
    eyebrow: String,
    title: String,
    subtitle: String,
    metrics: List<OverviewMetric>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = RedPrimary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(AgapSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                metrics.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = AgapShape.md,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.md)) {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = metric.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = metric.value.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManagementSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = AgapShape.md,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@Composable
internal fun ManagementSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 0.55.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(AgapSpacing.sm))
        Surface(
            shape = AgapShape.pill,
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.xs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
internal fun EmptyManagementState(
    title: String,
    message: String,
    success: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgapSpacing.xl, vertical = AgapSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background((if (success) GreenActive else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (success) GreenActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ContactLine(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.sm)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.45.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = AgapShape.pill,
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(if (text.equals("DRRMO", true)) "DRRMO" else text.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun ConfirmationDialog(
    request: ConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (request.destructive) RedPrimary else PaletteOrangeDark
            )
        },
        title = {
            Text(
                text = request.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = request.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (request.destructive) RedPrimary else PaletteOrangeDark
                )
            ) {
                Text(request.confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

internal fun String.maskedIdentifier(): String {
    if (isBlank()) return ""
    val visible = takeLast(minOf(4, length))
    return "•••• $visible"
}

internal fun String.compactIdentifier(): String {
    if (length <= 24) return this
    return "${take(10)}…${takeLast(8)}"
}

@Composable
internal fun accountStatusColor(status: String): Color = when (status.uppercase(Locale.getDefault())) {
    "APPROVED", "ACTIVE", "VERIFIED" -> AgapStatus.success
    "PENDING" -> AgapStatus.warning
    "SUSPENDED", "REJECTED" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun responderStatusColor(status: String): Color = when (status.uppercase(Locale.getDefault())) {
    "IDLE" -> AgapStatus.success
    "DISPATCHED" -> AgapStatus.warning
    "OFFLINE" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun agencyTypeColor(type: String): Color =
    AgencyType.fromCode(type)?.color ?: MaterialThemeFallbackGray

internal val MaterialThemeFallbackGray = SlateTextSecondary
