package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
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
import com.travelingsalesman.agap.core.util.QrCodeUtils
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onSwitchView: (String) -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    val displayId = uiState.userDisplayId ?: run {
        if (uiState.role == "ADMIN") "ADMIN-ACCESS"
        else if (uiState.role == "RESPONDER") "RES-UNIT-${uiState.userId ?: 0}"
        else "AGAP-${10000 + (uiState.userId ?: 0)}"
    }

    Scaffold(
        topBar = {
            ProfileTopBar(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onSettingsClick = {}
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                displayName = uiState.fullName?.ifBlank { null } ?: uiState.username ?: "Juan Dela Cruz",
                username = uiState.username ?: "",
                email = uiState.email,
                phone = uiState.phoneNumber,
                displayId = displayId,
                isVerified = uiState.isVerified,
                onQrClick = { showQrDialog = true },
                onEditClick = { viewModel.openEditProfileDialog() }
            )

            if (uiState.role == "CITIZEN" || uiState.role == null) {
                // Emergency Contact Section (API backed)
                EmergencyContactSection(
                    contacts = uiState.emergencyContacts,
                    isLoading = uiState.isLoadingContacts,
                    onAddContact = { name, phone, rel -> viewModel.addEmergencyContact(name, phone, rel) },
                    onDeleteContact = { id -> viewModel.deleteEmergencyContact(id) }
                )

                // Health Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HealthStatCard(
                        label = "National / Civil ID",
                        value = uiState.nationalId?.ifBlank { null } ?: "Verified ID",
                        icon = Icons.Outlined.Badge,
                        modifier = Modifier.weight(1f),
                        iconColor = RedPrimary
                    )
                    HealthStatCard(
                        label = "Medical Info & Notes",
                        value = uiState.medicalInfo?.ifBlank { null } ?: "No allergy notes",
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            } else {
                // Admin or Responder view specific profile items
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

            // Emergency Preferences
            SettingsSection(title = "Emergency Preferences") {
                PreferenceToggleRow(
                    icon = Icons.Outlined.NearMe,
                    title = "Auto Send GPS",
                    subtitle = "Attach location when sending alert",
                    checked = uiState.autoSendGps,
                    onCheckedChange = { viewModel.setAutoSendGps(it) }
                )
                PreferenceToggleRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Enable BLE Fallback",
                    subtitle = "Use BLE mesh when offline",
                    checked = uiState.enableBleFallback,
                    onCheckedChange = { viewModel.setEnableBleFallback(it) }
                )
                PreferenceToggleRow(
                    icon = Icons.Outlined.Vibration,
                    title = "Vibration Alert",
                    subtitle = "Vibrate on alert updates",
                    checked = uiState.vibrationAlert,
                    onCheckedChange = { viewModel.setVibrationAlert(it) }
                )
                PreferenceToggleRow(
                    icon = Icons.Outlined.Share,
                    title = "Offline Sharing",
                    subtitle = "Allow sharing app offline",
                    checked = uiState.offlineSharing,
                    onCheckedChange = { viewModel.setOfflineSharing(it) }
                )
            }

            // Offline Features
            SettingsSection(title = "Offline Features") {
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
                    subtitle = "Map tiles auto-cached for offline use",
                    iconColor = Color(0xFF4CAF50)
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
                        Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Server API Endpoint", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(
                        value = uiState.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(
            userDisplayId = displayId,
            userName = uiState.fullName?.ifBlank { null } ?: uiState.username ?: "AGAP User",
            onDismiss = { showQrDialog = false }
        )
    }

    // Edit Profile Dialog
    if (uiState.isEditingProfile) {
        EditProfileDialog(
            initialName = uiState.fullName ?: "",
            initialEmail = uiState.email ?: "",
            initialPhone = uiState.phoneNumber ?: "",
            initialMedicalInfo = uiState.medicalInfo ?: "",
            isSaving = uiState.isSavingProfile,
            onDismiss = viewModel::closeEditProfileDialog,
            onSave = { name, email, phone, med ->
                viewModel.updateProfile(name, email, phone, med)
            }
        )
    }

    // Feedback Snackbar for Profile operations
    uiState.profileMessage?.let { msg ->
        LaunchedEffect(msg) {
            // Auto-clear message after toast
        }
    }
}

@Composable
fun ProfileTopBar(
    isDarkTheme: Boolean,
    onToggleTheme: (() -> Unit)?,
    onSettingsClick: () -> Unit
) {
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
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            onToggleTheme?.let { toggle ->
                IconButton(onClick = toggle) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Light/Dark Theme",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun UserHeaderCard(
    displayName: String,
    username: String,
    email: String?,
    phone: String?,
    displayId: String,
    isVerified: Boolean,
    onQrClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar Box
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

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = "Verified User",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (!phone.isNullOrBlank()) {
                        Text(text = phone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else if (!email.isNullOrBlank()) {
                        Text(text = email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        Text(text = "@$username", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(text = displayId, color = RedPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onQrClick() }
                    ) {
                        Icon(
                            Icons.Outlined.QrCode2,
                            contentDescription = "QR Code",
                            tint = Color.Black,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                    Text("Tap to view", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun EmergencyContactSection(
    contacts: List<EmergencyContactDto>,
    isLoading: Boolean,
    onAddContact: (String, String, String) -> Unit,
    onDeleteContact: (Long) -> Unit
) {
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
            Text("Emergency Contacts (${contacts.size})", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("+ Add Contact", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showAddDialog = true })
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading && contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
            }
        } else if (contacts.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No emergency contacts added yet. Tap '+ Add Contact' to add trusted contacts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            contacts.forEach { contact ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
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
                                .background(RedPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Call, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.contactName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${contact.phoneNumber}${if (contact.relationship.isNotBlank()) " • " + contact.relationship else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDeleteContact(contact.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove Contact", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Emergency Contact", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
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
                        label = { Text("Relationship (e.g. Parent, Spouse)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2196F3))
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            onAddContact(newName, newPhone, newRelation.ifBlank { "Contact" })
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
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HealthStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, iconColor: Color = Color.Gray) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
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
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            color = MaterialTheme.colorScheme.surface,
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
fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
        Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Target ID: $id", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun QrCodeDialog(
    userDisplayId: String,
    userName: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(userDisplayId) {
        QrCodeUtils.generateQrCode(userDisplayId, width = 500, height = 500)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(userName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(userDisplayId, color = RedPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "User QR Code",
                        modifier = Modifier.size(220.dp)
                    )
                } else {
                    Text("Failed to generate QR code", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun EditProfileDialog(
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    initialMedicalInfo: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, phone: String, medicalInfo: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    var medicalInfo by remember { mutableStateOf(initialMedicalInfo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile Details", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                )
                OutlinedTextField(
                    value = medicalInfo,
                    onValueChange = { medicalInfo = it },
                    label = { Text("Medical Information / Allergies") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, phone, medicalInfo) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
                }
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
