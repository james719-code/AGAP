package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Settings
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
import com.travelingsalesman.agap.ui.theme.PaletteBgDark
import com.travelingsalesman.agap.ui.theme.PaletteSurfaceDark
import com.travelingsalesman.agap.ui.theme.PaletteSurfaceVariantDark
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

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            ProfileTopBar()
        },
        containerColor = PaletteBgDark
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // User Header Card
            UserHeaderCard(
                name = uiState.username ?: "Juan Dela Cruz",
                citizenId = if (uiState.role == "ADMIN") "ADMIN-ACCESS" else if (uiState.role == "RESPONDER") "RES-UNIT-${uiState.userId ?: 0}" else "AGAP-${10000 + (uiState.userId ?: 0)}",
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

            // Offline Features & App Sharing
            SettingsSection(title = "App Sharing & Offline") {
                NavigationRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Share via Bluetooth",
                    subtitle = "Share AGAP with nearby devices",
                    iconColor = Color(0xFF2196F3)
                )
                NavigationRow(
                    icon = Icons.Outlined.Wifi,
                    title = "Share via Wi-Fi Direct",
                    subtitle = "Share AGAP without internet",
                    iconColor = Color(0xFF2196F3)
                )
                NavigationRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "Download Offline Maps",
                    subtitle = "Save maps for offline use",
                    iconColor = Color(0xFF4CAF50)
                )
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

            // Emergency Targets
            SettingsSection(
                title = "Emergency Targets",
                actionLabel = "Manage",
                onActionClick = {}
            ) {
                TargetRow(name = "DRRMO Legazpi City", id = "DRRMO-LGZ-001", icon = Icons.Outlined.TrackChanges)
                TargetRow(name = "PNP Legazpi", id = "PNP-LGZ-002", icon = Icons.Outlined.Shield)
                TargetRow(name = "BFP Legazpi", id = "BFP-LGZ-003", icon = Icons.Outlined.LocalFireDepartment)
                TargetRow(name = "Bicol Regional Hospital", id = "HOSP-LGZ-004", icon = Icons.Outlined.AddCircleOutline)
            }

            // Network Server Configuration
            SettingsSection(title = "Network Server Configuration") {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Dns, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Server API Endpoint", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(
                        value = uiState.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // About
            SettingsSection(title = "About") {
                NavigationRow(icon = Icons.Outlined.Info, title = "About AGAP")
                NavigationRow(icon = Icons.Outlined.Shield, title = "Privacy Policy")
                NavigationRow(icon = Icons.Outlined.Description, title = "Terms of Service")
                InfoRow(icon = Icons.Outlined.SettingsSuggest, title = "Version", value = "1.0.0")
            }

            // Logout Button
            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(80.dp)) // Extra space for floating bottom nav
        }
    }
}

@Composable
fun ProfileTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun UserHeaderCard(name: String, citizenId: String, onClick: () -> Unit = {}) {
    Surface(
        color = PaletteSurfaceDark,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image Placeholder
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Citizen ID", color = Color.Gray, fontSize = 12.sp)
                Text(citizenId, color = Color(0xFFE53935), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Outlined.QrCode2,
                        contentDescription = "QR Code",
                        tint = Color.Black,
                        modifier = Modifier.padding(4.dp)
                    )
                }
                Text("Tap to view", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun EmergencyContactSection() {
    var contacts by remember {
        mutableStateOf(
            listOf(
                Triple("Maria Cruz", "0917 123 4567", "Spouse"),
                Triple("Juan Dela Cruz Sr.", "0918 987 6543", "Parent")
            )
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRelation by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Emergency Contacts (${contacts.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("+ Add Contact", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showAddDialog = true })
        }
        Spacer(modifier = Modifier.height(10.dp))

        contacts.forEachIndexed { index, (name, phone, relation) ->
            Surface(
                color = PaletteSurfaceDark,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Call, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("$phone • $relation", color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        contacts = contacts.filterIndexed { i, _ -> i != index }
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove Contact", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Emergency Contact", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2196F3))
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2196F3))
                    )
                    OutlinedTextField(
                        value = newRelation,
                        onValueChange = { newRelation = it },
                        label = { Text("Relationship (e.g. Parent, Sibling)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2196F3))
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            contacts = contacts + Triple(newName, newPhone, newRelation.ifBlank { "Relative" })
                            newName = ""
                            newPhone = ""
                            newRelation = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("ADD CONTACT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = PaletteSurfaceDark,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
fun HealthStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, iconColor: Color = Color.Gray) {
    Surface(
        color = PaletteSurfaceDark,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, color = Color.Gray, fontSize = 10.sp)
                Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (actionLabel != null) {
                Text(
                    text = actionLabel,
                    color = Color(0xFF2196F3),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onActionClick?.invoke() }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = PaletteSurfaceDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE53935),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
fun NavigationRow(icon: ImageVector, title: String, subtitle: String? = null, iconColor: Color = Color.Gray) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TargetRow(name: String, id: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Target ID: $id", color = Color.Gray, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Active", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = Color.Gray, fontSize = 14.sp)
    }
}
