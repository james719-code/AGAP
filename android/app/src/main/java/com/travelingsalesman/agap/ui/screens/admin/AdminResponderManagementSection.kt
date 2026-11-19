@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.ui.map.MapCameraAction
import com.travelingsalesman.agap.ui.map.MapCameraCommand
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.util.Locale
import org.osmdroid.util.GeoPoint

@Composable
internal fun ResponderManagementContent(
    units: List<ResponderUnitDto>,
    agencies: List<AgencyDto>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onUnitClick: (ResponderUnitDto) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ResponderUnitDto) -> Unit,
    onDelete: (ResponderUnitDto) -> Unit
) {
    val agencyById = remember(agencies) {
        agencies.associateBy { it.id }
    }

    val query = searchQuery.trim()

    var selectedUnitStatus by rememberSaveable { mutableStateOf("ALL") }

    val filtered = remember(units, agencyById, query, selectedUnitStatus) {
        units
            .filter { unit ->
                val agency = agencyById[unit.agencyId]

                (selectedUnitStatus == "ALL" || normalizeResponderStatusValue(unit.status) == selectedUnitStatus) &&
                    (query.isBlank() ||
                        unit.unitCallsign.contains(query, true) ||
                        normalizeResponderStatusValue(unit.status).contains(query, true) ||
                        unit.targetId.contains(query, true) ||
                        agency?.name.orEmpty().contains(query, true) ||
                        agency?.code.orEmpty().contains(query, true))
            }
            .sortedBy {
                it.unitCallsign.lowercase(Locale.getDefault())
            }
    }

    var visibleLimit by rememberSaveable(query, selectedUnitStatus) {
        mutableIntStateOf(MANAGEMENT_LARGE_BATCH_SIZE)
    }

    val visibleUnits = filtered.take(visibleLimit)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            com.travelingsalesman.agap.ui.components.AgapPageIntro("Responder units", "Find field units and review their current state.")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManagementSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = "Search units or agencies",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                listOf("ALL", "IDLE", "DISPATCHED", "OFFLINE").forEach { status ->
                    val count = if (status == "ALL") units.size else units.count { normalizeResponderStatusValue(it.status) == status }
                    ManagementFilterChip(
                        selected = selectedUnitStatus == status,
                        onClick = { selectedUnitStatus = status },
                        label = "${status.lowercase(java.util.Locale.ROOT).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }} ($count)"
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyManagementState(
                    title = if (query.isBlank()) "No responder units registered" else "No matching units",
                    message = if (query.isBlank()) {
                        "Create a responder unit after registering its parent agency."
                    } else {
                        "Try a callsign, unit status, target ID or agency name."
                    }
                )
            }
        } else {
            items(
                items = visibleUnits,
                key = { it.id },
                contentType = { "responder_unit_card" }
            ) { unit ->
                ResponderUnitCard(
                    unit = unit,
                    agency = agencyById[unit.agencyId],
                    onClick = { onUnitClick(unit) },
                    onEdit = { onEdit(unit) },
                    onDelete = { onDelete(unit) }
                )
            }

            if (visibleUnits.size < filtered.size) {
                item {
                    ManagementLoadMore(
                        showing = visibleUnits.size,
                        total = filtered.size,
                        onLoadMore = {
                            visibleLimit += MANAGEMENT_LARGE_BATCH_SIZE
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ResponderUnitCard(
    unit: ResponderUnitDto,
    agency: AgencyDto?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val normalizedStatus = normalizeResponderStatusValue(unit.status)
    val statusColor = responderStatusColor(normalizedStatus)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .glassCard(cornerShape = AgapShape.xl),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AgapShape.md)
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.unitCallsign,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = agency?.let { "${it.code} • ${it.name}" } ?: "Agency #${unit.agencyId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(AgapSpacing.sm))
                StatusPill(normalizedStatus, statusColor)
            }
        }
    }
}

@Composable
internal fun ResponderUnitDetailsContent(
    unit: ResponderUnitDto,
    agency: AgencyDto?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val normalizedStatus = normalizeResponderStatusValue(unit.status)
    val statusColor = responderStatusColor(normalizedStatus)
    val coordinates = if (unit.currentLat == 0.0 && unit.currentLon == 0.0) "Not reported" else "${unit.currentLat}, ${unit.currentLon}"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = AgapSpacing.section
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().glassCard(cornerShape = AgapShape.xl, borderColor = statusColor.copy(alpha = 0.3f)),
                color = Color.Transparent
            ) {
                Row(Modifier.padding(AgapSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(AgapShape.lg).background(statusColor.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.DirectionsCar, null, tint = statusColor, modifier = Modifier.size(27.dp)) }
                    Spacer(Modifier.width(AgapSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(unit.unitCallsign, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Unit ID ${unit.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(normalizedStatus, statusColor)
                }
            }
        }
        item {
            UserDetailSection("UNIT RECORD") {
                UserDetailGridRow(UserDetail("Target ID", unit.targetId.orNotProvided()), UserDetail("Status", normalizedStatus))
                UserDetailGridRow(UserDetail("Device ID", unit.deviceId.maskedValue()), UserDetail("Last seen", unit.lastSeenAt.orNotProvided()))
            }
        }
        item {
            UserDetailSection("ASSIGNMENT & LOCATION") {
                UserDetailGridRow(UserDetail("Agency", agency?.name ?: "Agency #${unit.agencyId}"), UserDetail("Agency code", agency?.code.orNotProvided()))
                UserDetailGridRow(UserDetail("Last location", if (coordinates == "Not reported") coordinates else "Map pin available"))
            }
        }
        if (unit.currentLat != 0.0 || unit.currentLon != 0.0) {
            item {
                Surface(modifier = Modifier.fillMaxWidth().height(210.dp).clip(AgapShape.lg), color = MaterialTheme.colorScheme.surface) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize(), pickerPoint = GeoPoint(unit.currentLat, unit.currentLon),
                        cameraCommand = MapCameraCommand(unit.id, MapCameraAction.FOCUS_POINT, unit.currentLat, unit.currentLon, 16.0),
                        showIncidents = false, showResponders = false, zoomLevel = 16.0
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(46.dp), shape = AgapShape.md) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("EDIT", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDelete, modifier = Modifier.weight(1f).height(46.dp), shape = AgapShape.md,
                    border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.65f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("DELETE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ResponderEditorDialog(
    isEditing: Boolean,
    callsign: String,
    agencyId: Long,
    targetId: String,
    status: String,
    agencies: List<AgencyDto>,
    onCallsignChange: (String) -> Unit,
    onAgencyChange: (Long) -> Unit,
    onTargetIdChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val canSave = callsign.isNotBlank() && agencyId != 0L && agencies.any { it.id == agencyId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditing) "Edit responder unit" else "Add responder unit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Field dispatch resource",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                if (agencies.isEmpty()) {
                    Surface(
                        shape = AgapShape.md,
                        color = PaletteOrangeDark.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, PaletteOrangeDark.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(AgapSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = PaletteOrangeDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Create an agency before adding responder units.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = callsign,
                    onValueChange = onCallsignChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Unit callsign") },
                    placeholder = { Text("e.g. DRRMO-ALPHA-1") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                if (agencies.isNotEmpty()) {
                    Column {
                        Text(
                            text = "ASSIGNED AGENCY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.55.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            agencies.forEach { agency ->
                                FilterChip(
                                    selected = agencyId == agency.id,
                                    onClick = { onAgencyChange(agency.id) },
                                    label = {
                                        Text(
                                            agency.code,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    shape = AgapShape.pill
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetId,
                    onValueChange = onTargetIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target spatial ID (optional)") },
                    supportingText = { Text("Leave blank to let AGAP generate one.") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                if (isEditing) {
                    Column {
                        Text(
                            text = "UNIT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.55.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            responderStatuses.forEach { value ->
                                val enabled = when {
                                    value == "DISPATCHED" -> false
                                    status == "DISPATCHED" && value == "IDLE" -> false
                                    else -> true
                                }
                                FilterChip(
                                    selected = status == value,
                                    onClick = { onStatusChange(value) },
                                    enabled = enabled,
                                    label = { Text(value) },
                                    shape = AgapShape.pill,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = responderStatusColor(value).copy(alpha = 0.13f),
                                        selectedLabelColor = responderStatusColor(value)
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "DISPATCHED is controlled by active incident assignments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text(if (isEditing) "SAVE CHANGES" else "ADD UNIT", fontWeight = FontWeight.Bold)
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
