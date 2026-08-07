package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun CitizenProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showEditContactDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContactDto?>(null) }

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactRelation by remember { mutableStateOf("") }
    var contactIsPrimary by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf(uiState.userProfile?.fullName ?: "") }
    var editEmail by remember { mutableStateOf(uiState.userProfile?.email ?: "") }
    var editPhone by remember { mutableStateOf(uiState.userProfile?.phone ?: "") }
    var editMedical by remember { mutableStateOf(uiState.medicalInfo?.conditions ?: "") }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let {
            editName = it.fullName
            editEmail = it.email
            editPhone = it.phone
        }
        uiState.medicalInfo?.let {
            editMedical = it.conditions
        }
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "CITIZEN PROFILE",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
        ) {
            // Header Section
            DetailedUserHeader(
                name = uiState.userProfile?.fullName?.ifBlank { null } ?: uiState.username ?: "Citizen User",
                citizenId = uiState.userProfile?.citizenId?.ifBlank { null } ?: "AGAP-${uiState.userId ?: 0}"
            )

            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusInfoCard(
                    icon = Icons.Outlined.CalendarToday,
                    label = "Member Since",
                    value = uiState.userProfile?.memberSince?.take(10)?.ifBlank { null } ?: "Recent",
                    modifier = Modifier.weight(1f)
                )
                StatusInfoCard(
                    icon = Icons.Outlined.VerifiedUser,
                    label = "Account Status",
                    value = "Active",
                    isStatus = true,
                    modifier = Modifier.weight(1f)
                )
                StatusInfoCard(
                    icon = Icons.Outlined.AccessTime,
                    label = "Last Sync",
                    value = "Online",
                    modifier = Modifier.weight(1f)
                )
            }

            // Personal Information
            SectionWrapper(title = "Personal Information", onEditClick = { showEditProfileDialog = true }) {
                uiState.userProfile?.let { profile ->
                    InfoItem(Icons.Outlined.Person, "Full Name", profile.fullName)
                    InfoItem(Icons.Outlined.Phone, "Phone Number", profile.phone)
                    InfoItem(Icons.Outlined.Email, "Email Address", profile.email)
                } ?: run {
                    InfoItem(Icons.Outlined.Person, "Full Name", uiState.username ?: "Citizen")
                    InfoItem(Icons.Outlined.Phone, "Phone Number", "Not Provided")
                    InfoItem(Icons.Outlined.Email, "Email Address", "Not Provided")
                }
            }

            // Medical Information
            SectionWrapper(title = "Medical Information", onEditClick = { showEditProfileDialog = true }) {
                uiState.medicalInfo?.let { info ->
                    InfoItem(Icons.Outlined.WaterDrop, "Blood Type", info.bloodType, valueColor = RedPrimary)
                    InfoItem(Icons.Outlined.MonitorHeart, "Medical Conditions / Allergies", info.conditions)
                } ?: run {
                    InfoItem(Icons.Outlined.WaterDrop, "Blood Type", "O+", valueColor = RedPrimary)
                    InfoItem(Icons.Outlined.MonitorHeart, "Medical Conditions", "None")
                }
            }

            // Emergency Contacts
            SectionWrapper(title = "Emergency Contacts", onEditClick = null) {
                if (uiState.emergencyContacts.isEmpty()) {
                    Text("No emergency contacts added.", color = subTextColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                } else {
                    uiState.emergencyContacts.forEach { contact ->
                        EmergencyContactItem(
                            name = contact.contactName,
                            phone = contact.phoneNumber,
                            relationship = contact.relationship,
                            isPrimary = contact.isPrimary,
                            onEdit = {
                                editingContact = contact
                                contactName = contact.contactName
                                contactPhone = contact.phoneNumber
                                contactRelation = contact.relationship
                                contactIsPrimary = contact.isPrimary
                                showEditContactDialog = true
                            },
                            onSetPrimary = { viewModel.setPrimaryEmergencyContact(contact.id) },
                            onDelete = { viewModel.deleteEmergencyContact(contact.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AgapSpacing.xs))
                TextButton(
                    onClick = { showAddContactDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(AgapSpacing.sm))
                    Text("Add Emergency Contact", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Emergency Preferences
            SectionWrapper(title = "Emergency Preferences", onEditClick = null) {
                PreferenceSwitch(Icons.Outlined.NearMe, "Auto Send GPS", "Attach location when sending alert", uiState.preferences.autoSendGps, viewModel::updateAutoSendGps)
                PreferenceSwitch(Icons.Outlined.Bluetooth, "Enable BLE Fallback", "Use BLE mesh when offline", uiState.preferences.enableBleFallback, viewModel::updateBleFallback)
                PreferenceSwitch(Icons.Outlined.Vibration, "Vibration Alert", "Vibrate on alert updates", uiState.preferences.vibrationAlert, viewModel::updateVibrationAlert)
                PreferenceSwitch(Icons.AutoMirrored.Outlined.VolumeOff, "Silent Mode", "Alerts will be silent", uiState.preferences.silentMode, viewModel::updateSilentMode)
                PreferenceSwitch(Icons.Outlined.Share, "Offline Sharing", "Allow sharing app offline", uiState.preferences.offlineSharing, viewModel::updateOfflineSharing)
            }

            // Logout Button
            TextButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgapSpacing.md),
                colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(AgapSpacing.sm))
                Text("Log Out", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Add Emergency Contact Dialog
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Emergency Contact", color = textColor, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = contactRelation,
                        onValueChange = { contactRelation = it },
                        label = { Text("Relationship (e.g. Spouse, Parent)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = contactIsPrimary, onCheckedChange = { contactIsPrimary = it })
                        Text("Set as primary emergency contact", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            viewModel.addEmergencyContact(contactName, contactPhone, contactRelation, contactIsPrimary)
                            contactName = ""
                            contactPhone = ""
                            contactRelation = ""
                            contactIsPrimary = false
                            showAddContactDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("ADD CONTACT", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("CANCEL", color = subTextColor)
                }
            },
            containerColor = cardBg,
            titleContentColor = textColor,
            textContentColor = textColor
        )
    }

    if (showEditContactDialog) {
        AlertDialog(
            onDismissRequest = { showEditContactDialog = false },
            title = { Text("Edit Emergency Contact", color = textColor, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = contactRelation,
                        onValueChange = { contactRelation = it },
                        label = { Text("Relationship") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = contactIsPrimary, onCheckedChange = { contactIsPrimary = it })
                        Text("Primary emergency contact", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val contact = editingContact
                        if (contact != null && contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            viewModel.updateEmergencyContact(contact.id, contactName, contactPhone, contactRelation, contactIsPrimary)
                            editingContact = null
                            contactName = ""
                            contactPhone = ""
                            contactRelation = ""
                            contactIsPrimary = false
                            showEditContactDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("SAVE CONTACT", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditContactDialog = false }) {
                    Text("CANCEL", color = subTextColor)
                }
            },
            containerColor = cardBg,
            titleContentColor = textColor,
            textContentColor = textColor
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile & Medical Info", color = textColor, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                    OutlinedTextField(
                        value = editMedical,
                        onValueChange = { editMedical = it },
                        label = { Text("Medical Info / Allergies") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(editName, editEmail, editPhone, editMedical)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("SAVE CHANGES", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("CANCEL", color = subTextColor)
                }
            },
            containerColor = cardBg,
            titleContentColor = textColor,
            textContentColor = textColor
        )
    }
}

@Composable
fun DetailedUserHeader(name: String, citizenId: String) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RedPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(46.dp))
        }

        Spacer(modifier = Modifier.width(AgapSpacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = textColor, style = MaterialTheme.typography.headlineMedium)
            Text("Citizen ID", color = subTextColor, style = MaterialTheme.typography.bodySmall)
            Text(citizenId, color = RedPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Surface(
                color = GreenActive.copy(alpha = 0.1f),
                shape = AgapShape.xs
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = GreenActive, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verified", color = GreenActive, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun StatusInfoCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, isStatus: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.padding(AgapSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = subTextColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = subTextColor, style = MaterialTheme.typography.labelSmall)
        if (isStatus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(GreenActive))
                Spacer(modifier = Modifier.width(4.dp))
                Text(value, color = textColor, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text(value, color = textColor, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun SectionWrapper(
    title: String,
    actionLabel: String? = null,
    onEditClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val cardBg = MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = textColor, style = MaterialTheme.typography.titleMedium)
            if (actionLabel != null) {
                Text(actionLabel, color = RedPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { })
            } else if (onEditClick != null) {
                Text("Edit", color = RedPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onEditClick() })
            }
        }
        Spacer(modifier = Modifier.height(AgapSpacing.md))
        Surface(
            color = cardBg,
            shape = AgapShape.lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(AgapSpacing.md)) {
                content()
            }
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, title: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AgapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = subTextColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Text(title, color = subTextColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmergencyContactItem(
    name: String,
    phone: String,
    relationship: String = "",
    isPrimary: Boolean = false,
    onEdit: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = cardBg,
        shape = AgapShape.md,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(RedPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(AgapSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = textColor, style = MaterialTheme.typography.titleMedium)
                Text(if (relationship.isNotBlank()) "$phone • $relationship" else phone, color = subTextColor, style = MaterialTheme.typography.bodySmall)
                if (isPrimary) {
                    Text("PRIMARY", color = GreenActive, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!isPrimary) {
                IconButton(onClick = onSetPrimary) {
                    Icon(Icons.Outlined.Star, contentDescription = "Set Primary Contact", tint = subTextColor, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit Contact", tint = subTextColor, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete Contact", tint = subTextColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PreferenceSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AgapSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = subTextColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(AgapSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = subTextColor, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedPrimary,
                uncheckedThumbColor = subTextColor,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

