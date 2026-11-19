@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.util.Locale
import org.osmdroid.util.GeoPoint

@Composable
internal fun AgencyManagementContent(
    agencies: List<AgencyDto>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAgencyClick: (AgencyDto) -> Unit,
    onEdit: (AgencyDto) -> Unit,
    onDelete: (AgencyDto) -> Unit
) {
    val query = searchQuery.trim()

    var selectedTypeFilter by rememberSaveable { mutableStateOf("ALL") }

    val filtered = remember(agencies, query, selectedTypeFilter) {
        agencies
            .filter { agency ->
                (selectedTypeFilter == "ALL" || agency.type.equals(selectedTypeFilter, true)) &&
                    (query.isBlank() ||
                        agency.name.contains(query, true) ||
                        agency.code.contains(query, true) ||
                        agency.type.contains(query, true) ||
                        agency.contactNumber.contains(query, true))
            }
            .sortedBy {
                it.name.lowercase(Locale.getDefault())
            }
    }

    var visibleLimit by rememberSaveable(query, selectedTypeFilter) {
        mutableIntStateOf(MANAGEMENT_LARGE_BATCH_SIZE)
    }

    val visibleAgencies = filtered.take(visibleLimit)

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
            com.travelingsalesman.agap.ui.components.AgapPageIntro("Response agencies", "Find the organizations responsible for emergency response.")
        }
        item {
            ManagementSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = "Search agencies"
            )
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                (listOf("ALL") + agencyTypes).forEach { type ->
                    val count = if (type == "ALL") agencies.size else agencies.count { it.type.equals(type, true) }
                    ManagementFilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = { selectedTypeFilter = type },
                        label = "$type ($count)"
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyManagementState(
                    title = if (query.isBlank()) "No agencies in this filter" else "No matching agencies",
                    message = if (query.isBlank()) {
                        "Try another agency type or add the first agency."
                    } else {
                        "Try searching by agency name, code, type or contact number."
                    }
                )
            }
        } else {
            items(
                items = visibleAgencies,
                key = { it.id },
                contentType = { "agency_card" }
            ) { agency ->
                AgencyCard(
                    agency = agency,
                    onClick = { onAgencyClick(agency) },
                    onEdit = { onEdit(agency) },
                    onDelete = { onDelete(agency) }
                )
            }

            if (visibleAgencies.size < filtered.size) {
                item {
                    ManagementLoadMore(
                        showing = visibleAgencies.size,
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
internal fun AgencyCard(
    agency: AgencyDto,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = agencyTypeColor(agency.type)

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
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agency.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(text = agency.type, color = accent)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = agency.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit agency", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Delete agency", tint = RedPrimary)
                }
            }
        }
    }
}

@Composable
internal fun AgencyDetailsContent(
    agency: AgencyDto,
    units: List<ResponderUnitDto>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = agencyTypeColor(agency.type)
    val assignedUnits = remember(units, agency.id) {
        units.filter { it.agencyId == agency.id }
            .sortedBy { it.unitCallsign.lowercase(Locale.ROOT) }
    }
    val serviceArea = listOf(agency.regionCode, agency.provinceCode, agency.muniCode, agency.barangayCode)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val coordinates = if (agency.latitude == 0.0 && agency.longitude == 0.0) {
        "Not provided"
    } else {
        "${agency.latitude}, ${agency.longitude}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerShape = AgapShape.xl, borderColor = accent.copy(alpha = 0.3f)),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(AgapSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(AgapShape.lg)
                            .background(accent.copy(alpha = 0.13f))
                            .border(1.dp, accent.copy(alpha = 0.3f), AgapShape.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = accent, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(AgapSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agency.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("${agency.code}  |  Agency ID ${agency.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(text = agency.type, color = accent)
                }
            }
        }

        item {
            UserDetailSection("AGENCY RECORD") {
                UserDetailGridRow(UserDetail("Agency code", agency.code), UserDetail("Agency type", agency.type))
                UserDetailGridRow(UserDetail("Agency ID", agency.id.toString()))
            }
        }

        item {
            UserDetailSection("CONTACT & COVERAGE") {
                UserDetailGridRow(UserDetail("Contact number", agency.contactNumber.orNotProvided()))
                UserDetailGridRow(UserDetail("Service area", serviceArea.orNotProvided(), maxLines = 3))
                UserDetailGridRow(UserDetail("Map location", if (coordinates == "Not provided") coordinates else "Location pin available"))
            }
        }

        item {
            UserDetailSection("ASSIGNED RESPONSE UNITS") {
                UserDetailGridRow(UserDetail("Registered units", "${assignedUnits.size} unit(s)"))
                if (assignedUnits.isEmpty()) {
                    UserDetailGridRow(UserDetail("Availability", "No responder units assigned to this agency"))
                } else {
                    assignedUnits.forEach { unit ->
                        UserDetailItem(
                            title = unit.unitCallsign,
                            subtitle = "${normalizeResponderStatusValue(unit.status)}  |  ${unit.targetId.ifBlank { "No target ID" }}",
                            detail = unit.lastSeenAt?.let { "Last seen: $it" } ?: "No recent location reported"
                        )
                    }
                }
            }
        }

        if (agency.latitude != 0.0 || agency.longitude != 0.0) {
            item {
                Surface(modifier = Modifier.fillMaxWidth().height(210.dp).clip(AgapShape.lg), color = MaterialTheme.colorScheme.surface) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize(), pickerPoint = GeoPoint(agency.latitude, agency.longitude),
                        cameraCommand = MapCameraCommand(agency.id, MapCameraAction.FOCUS_POINT, agency.latitude, agency.longitude, 15.0),
                        showIncidents = false, showResponders = false, zoomLevel = 15.0
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = AgapShape.md
                ) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("EDIT", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(46.dp),
                    border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                    shape = AgapShape.md
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun AgencyEditorDialog(
    isEditing: Boolean,
    agencyName: String,
    agencyCode: String,
    agencyType: String,
    agencyContact: String,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val canSave = agencyName.isNotBlank() && agencyCode.isNotBlank() && agencyType in agencyTypes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditing) "Edit agency" else "Add agency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dispatch directory record",
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
                OutlinedTextField(
                    value = agencyName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agency name") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = agencyCode,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agency code") },
                    placeholder = { Text("e.g. DRRMO-LEGAZPI") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Column {
                    Text(
                        text = "AGENCY TYPE",
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
                        agencyTypes.forEach { type ->
                            FilterChip(
                                selected = agencyType == type,
                                onClick = { onTypeChange(type) },
                                label = { Text(type) },
                                shape = AgapShape.pill,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = agencyTypeColor(type).copy(alpha = 0.13f),
                                    selectedLabelColor = agencyTypeColor(type)
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = agencyContact,
                    onValueChange = onContactChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contact number") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text(if (isEditing) "SAVE CHANGES" else "ADD AGENCY", fontWeight = FontWeight.Bold)
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
