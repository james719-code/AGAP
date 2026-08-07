package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AdminManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf("User Verification", "Agencies", "Responder Units")

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var showAgencyDialog by remember { mutableStateOf(false) }
    var editingAgency by remember { mutableStateOf<AgencyDto?>(null) }
    var agencyCode by remember { mutableStateOf("") }
    var agencyName by remember { mutableStateOf("") }
    var agencyType by remember { mutableStateOf("DISPATCH") }
    var agencyContact by remember { mutableStateOf("") }

    var showUnitDialog by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf<ResponderUnitDto?>(null) }
    var unitCallsign by remember { mutableStateOf("") }
    var unitAgencyId by remember { mutableStateOf(101L) }
    var unitTargetId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "SYSTEM MANAGEMENT",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = viewModel::refreshAllData) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Data", tint = textColor)
                    }
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AgapSpacing.lg)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = cardBg,
                contentColor = RedPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                color = if (uiState.selectedTab == index) RedPrimary else subTextColor,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.lg))

            when (uiState.selectedTab) {
                0 -> { // User Verification Queue
                    val unverifiedUsers = uiState.usersList.filter { !it.isVerified }
                    val verifiedUsers = uiState.usersList.filter { it.isVerified }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PENDING VERIFICATION (${unverifiedUsers.size})",
                                    color = RedPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (unverifiedUsers.isEmpty()) {
                            item {
                                Text(
                                    text = "No pending registration approvals",
                                    color = subTextColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(AgapSpacing.lg)
                                )
                            }
                        } else {
                            items(unverifiedUsers, key = { it.id }) { user ->
                                UserVerificationCard(
                                    user = user,
                                    onApprove = { viewModel.verifyUserAccount(user.id) },
                                    onReject = { viewModel.deleteUserAccount(user.id) }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(AgapSpacing.lg))
                            Text(
                                text = "VERIFIED USERS (${verifiedUsers.size})",
                                color = subTextColor,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        items(verifiedUsers, key = { it.id }) { user ->
                            UserVerificationCard(
                                user = user,
                                isApproved = true,
                                onApprove = {},
                                onReject = { viewModel.deleteUserAccount(user.id) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                1 -> { // Agencies CRUD
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                editingAgency = null
                                agencyCode = ""
                                agencyName = ""
                                agencyType = "DISPATCH"
                                agencyContact = ""
                                showAgencyDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = AgapShape.md
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(AgapSpacing.sm))
                            Text("+ ADD NEW AGENCY", style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(modifier = Modifier.height(AgapSpacing.lg))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                            items(uiState.agenciesList, key = { it.id }) { agency ->
                                Surface(
                                    color = cardBg,
                                    shape = AgapShape.lg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(AgapSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(AgapSpacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(agency.name, color = textColor, style = MaterialTheme.typography.titleMedium)
                                            Text("Code: ${agency.code} • ${agency.type}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = {
                                            editingAgency = agency
                                            agencyCode = agency.code
                                            agencyName = agency.name
                                            agencyType = agency.type
                                            agencyContact = agency.contactNumber
                                            showAgencyDialog = true
                                        }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = subTextColor)
                                        }
                                        IconButton(onClick = { viewModel.deleteAgency(agency.id) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = subTextColor)
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }

                2 -> { // Responder Units CRUD
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                editingUnit = null
                                unitCallsign = ""
                                unitAgencyId = uiState.agenciesList.firstOrNull()?.id ?: 101L
                                unitTargetId = ""
                                showUnitDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = AgapShape.md
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(AgapSpacing.sm))
                            Text("+ ADD RESPONDER UNIT", style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(modifier = Modifier.height(AgapSpacing.lg))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                            items(uiState.responderUnitsList, key = { it.id }) { unit ->
                                Surface(
                                    color = cardBg,
                                    shape = AgapShape.lg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(AgapSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(AgapSpacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(unit.unitCallsign, color = textColor, style = MaterialTheme.typography.titleMedium)
                                            Text("Status: ${unit.status} • Target: ${unit.targetId.take(16)}...", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = {
                                            editingUnit = unit
                                            unitCallsign = unit.unitCallsign
                                            unitAgencyId = unit.agencyId
                                            unitTargetId = unit.targetId
                                            showUnitDialog = true
                                        }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = subTextColor)
                                        }
                                        IconButton(onClick = { viewModel.deleteResponderUnit(unit.id) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = subTextColor)
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }

        // Agency Add/Edit Dialog
        if (showAgencyDialog) {
            AlertDialog(
                onDismissRequest = { showAgencyDialog = false },
                title = { Text(if (editingAgency == null) "Create Agency" else "Edit Agency", color = textColor, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                        OutlinedTextField(
                            value = agencyName,
                            onValueChange = { agencyName = it },
                            label = { Text("Agency Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = agencyCode,
                            onValueChange = { agencyCode = it },
                            label = { Text("Code (e.g. BFP-LEGAZPI)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = agencyType,
                            onValueChange = { agencyType = it },
                            label = { Text("Type (e.g. FIRE, POLICE, MEDICAL)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = agencyContact,
                            onValueChange = { agencyContact = it },
                            label = { Text("Contact Number") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (agencyName.isNotBlank()) {
                                val target = editingAgency
                                if (target == null) {
                                    viewModel.createAgency(agencyCode, agencyName, agencyType, agencyContact)
                                } else {
                                    viewModel.updateAgency(target.id, agencyCode, agencyName, agencyType, agencyContact)
                                }
                                showAgencyDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("SAVE AGENCY", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAgencyDialog = false }) { Text("CANCEL", color = subTextColor) }
                },
                containerColor = cardBg,
                titleContentColor = textColor,
                textContentColor = textColor
            )
        }

        // Responder Unit Add/Edit Dialog
        if (showUnitDialog) {
            AlertDialog(
                onDismissRequest = { showUnitDialog = false },
                title = { Text(if (editingUnit == null) "Create Responder Unit" else "Edit Unit", color = textColor, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                        OutlinedTextField(
                            value = unitCallsign,
                            onValueChange = { unitCallsign = it },
                            label = { Text("Unit Callsign (e.g. RES-ALPHA-01)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = unitTargetId,
                            onValueChange = { unitTargetId = it },
                            label = { Text("Target Spatial ID (Optional)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (unitCallsign.isNotBlank()) {
                                val target = editingUnit
                                if (target == null) {
                                    viewModel.createResponderUnit(unitAgencyId, unitCallsign, "", "", unitTargetId)
                                } else {
                                    viewModel.updateResponderUnit(target.id, unitAgencyId, unitCallsign, target.status, unitTargetId)
                                }
                                showUnitDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("SAVE UNIT", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnitDialog = false }) { Text("CANCEL", color = subTextColor) }
                },
                containerColor = cardBg,
                titleContentColor = textColor,
                textContentColor = textColor
            )
        }

        uiState.feedbackMessage?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearFeedbackMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                modifier = Modifier.padding(AgapSpacing.lg)
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
fun UserVerificationCard(
    user: UserDto,
    isApproved: Boolean = false,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = cardBg,
        shape = AgapShape.lg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isApproved) GreenActive.copy(alpha = 0.15f) else RedPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (user.role == "RESPONDER") Icons.Outlined.LocalHospital else Icons.Outlined.Person,
                            contentDescription = null,
                            tint = if (isApproved) GreenActive else RedPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(AgapSpacing.md))
                    Column {
                        Text(user.name ?: user.username, color = textColor, style = MaterialTheme.typography.titleMedium)
                        Text("Role: ${user.role} • ID: ${user.displayId ?: user.id}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Surface(
                    color = if (isApproved) GreenActive.copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = AgapShape.xs
                ) {
                    Text(
                        text = if (isApproved) "VERIFIED" else "PENDING",
                        color = if (isApproved) GreenActive else Color(0xFFFF9800),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))
            HorizontalDivider(color = subTextColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Phone: ${user.phoneNumber ?: "Not provided"}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                    Text("Email: ${user.email ?: "Not provided"}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                    if (!user.nationalId.isNull_or_blank()) {
                        Text("National ID: ${user.nationalId}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (!isApproved) {
                Spacer(modifier = Modifier.height(AgapSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenActive),
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = AgapShape.sm
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("APPROVE ACCOUNT", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.height(40.dp),
                        shape = AgapShape.sm,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                    ) {
                        Text("REJECT", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

