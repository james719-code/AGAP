package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToDetailedProfile: () -> Unit = {},
    onSwitchView: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "PROFILE",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
        ) {
            // User Header Card
            UserHeaderCard(
                name = uiState.userProfile?.fullName?.ifBlank { null } ?: uiState.username ?: "User",
                citizenId = uiState.userProfile?.citizenId?.ifBlank { null } ?: (if (uiState.role == "ADMIN") "ADMIN-ACCESS" else if (uiState.role == "RESPONDER") "RES-UNIT-${uiState.userId ?: 0}" else "AGAP-${10000 + (uiState.userId ?: 0)}"),
                onClick = onNavigateToDetailedProfile
            )

            // Admin or Responder specific profile items (e.g. Duty Info)
            if (uiState.role != "CITIZEN" && uiState.role != null) {
                SettingsSection(title = "Duty Information") {
                    InfoRow(
                        icon = Icons.Outlined.Badge,
                        title = "Role",
                        value = uiState.role ?: "Unknown"
                    )
                    uiState.agencyInfo?.let { agency ->
                        InfoRow(
                            icon = Icons.Outlined.AccountBalance,
                            title = "Agency",
                            value = agency.name
                        )
                    }
                }
            }

            // App Appearance Settings
            SettingsSection(title = "App Appearance") {
                PreferenceToggleRow(
                    icon = if (uiState.isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    title = "Dark Mode",
                    subtitle = "Toggle between light and dark themes",
                    checked = uiState.isDarkMode,
                    onCheckedChange = viewModel::updateDarkMode
                )
            }

            // Emergency Agencies
            var showAgencyDialog by remember { mutableStateOf(false) }
            var editingAgency by remember { mutableStateOf<AgencyDto?>(null) }
            var agencyCode by remember { mutableStateOf("") }
            var agencyName by remember { mutableStateOf("") }
            var agencyType by remember { mutableStateOf("") }
            var agencyContact by remember { mutableStateOf("") }

            SettingsSection(
                title = "Emergency Response Agencies",
                actionLabel = if (uiState.role == "ADMIN") "+ Add Agency" else null,
                onActionClick = {
                    editingAgency = null
                    agencyCode = ""
                    agencyName = ""
                    agencyType = "DISPATCH"
                    agencyContact = ""
                    showAgencyDialog = true
                }
            ) {
                if (uiState.agenciesList.isEmpty()) {
                    Text("No agencies registered", color = subTextColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(AgapSpacing.md))
                } else {
                    uiState.agenciesList.forEach { agency ->
                        TargetRow(
                            name = agency.name,
                            id = if (agency.code.isNotBlank()) agency.code else "AGENCY-${agency.id}",
                            icon = Icons.Outlined.Shield,
                            onDelete = if (uiState.role == "ADMIN") { { viewModel.deleteAgency(agency.id) } } else null,
                            onClick = if (uiState.role == "ADMIN") {
                                {
                                    editingAgency = agency
                                    agencyCode = agency.code
                                    agencyName = agency.name
                                    agencyType = agency.type
                                    agencyContact = agency.contactNumber
                                    showAgencyDialog = true
                                }
                            } else null
                        )
                    }
                }
            }

            if (showAgencyDialog) {
                AlertDialog(
                    onDismissRequest = { showAgencyDialog = false },
                    title = { Text(if (editingAgency == null) "Add Emergency Agency" else "Edit Agency", color = textColor, style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                            OutlinedTextField(
                                value = agencyName,
                                onValueChange = { agencyName = it },
                                label = { Text("Agency Name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                            )
                            OutlinedTextField(
                                value = agencyCode,
                                onValueChange = { agencyCode = it },
                                label = { Text("Agency Code (Target ID)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                            )
                            OutlinedTextField(
                                value = agencyType,
                                onValueChange = { agencyType = it },
                                label = { Text("Type (e.g. MEDICAL, POLICE, FIRE)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                            )
                            OutlinedTextField(
                                value = agencyContact,
                                onValueChange = { agencyContact = it },
                                label = { Text("Contact Number") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (agencyName.isNotBlank()) {
                                    val targetAgency = editingAgency
                                    if (targetAgency == null) {
                                        viewModel.createAgency(agencyCode, agencyName, agencyType, agencyContact)
                                    } else {
                                        viewModel.updateAgency(targetAgency.id, agencyCode, agencyName, agencyType, agencyContact)
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
                        TextButton(onClick = { showAgencyDialog = false }) {
                            Text("CANCEL", color = subTextColor)
                        }
                    },
                    containerColor = cardBg,
                    titleContentColor = textColor,
                    textContentColor = textColor
                )
            }

            // Network Server Configuration
            SettingsSection(title = "Network Server Configuration") {
                Column(modifier = Modifier.padding(AgapSpacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Dns, contentDescription = null, tint = subTextColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(AgapSpacing.md))
                        Text("Server API Endpoint", color = textColor, style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = uiState.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary,
                            unfocusedBorderColor = subTextColor.copy(alpha = 0.4f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        shape = AgapShape.sm
                    )
                }
            }

            // Logout Button
            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AgapShape.md,
                border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(AgapSpacing.sm))
                Text("Logout", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(80.dp)) // Extra space for floating bottom nav
        }
    }
}

@Composable
fun UserHeaderCard(name: String, citizenId: String, onClick: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = AgapShape.xl,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(AgapSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image Placeholder
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(RedPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(AgapSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                Text("Citizen ID", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(citizenId, color = RedPrimary, style = MaterialTheme.typography.titleMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = AgapShape.xs,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Outlined.QrCode2,
                        contentDescription = "QR Code",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(AgapSpacing.xs)
                    )
                }
                Text("Tap to view", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            if (actionLabel != null) {
                Text(
                    text = actionLabel,
                    color = RedPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onActionClick?.invoke() }
                )
            }
        }
        Spacer(modifier = Modifier.height(AgapSpacing.md))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = AgapShape.lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = AgapSpacing.sm)) {
                content()
            }
        }
    }
}


@Composable
fun PreferenceToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun NavigationRow(icon: ImageVector, title: String, subtitle: String? = null, iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TargetRow(
    name: String,
    id: String,
    icon: ImageVector,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text("Target ID: $id", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .clip(AgapShape.xs)
                .background(GreenActive.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Active", color = GreenActive, style = MaterialTheme.typography.labelSmall)
        }
        if (onDelete != null) {
            Spacer(modifier = Modifier.width(AgapSpacing.xs))
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete Agency", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(modifier = Modifier.width(AgapSpacing.sm))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}


