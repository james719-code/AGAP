package com.travelingsalesman.agap.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.remote.dto.UserDeviceDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.PaletteBlueDark
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showEditContactDialog by remember { mutableStateOf(false) }
    var showEditPersonalDialog by remember { mutableStateOf(false) }
    var showEditMedicalDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var contactPendingDelete by remember { mutableStateOf<EmergencyContactDto?>(null) }
    var devicePendingDelete by remember { mutableStateOf<UserDeviceDto?>(null) }
    var editingContact by remember { mutableStateOf<EmergencyContactDto?>(null) }

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactRelation by remember { mutableStateOf("") }
    var contactIsPrimary by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editMedical by remember { mutableStateOf("") }

    val profile = uiState.userProfile
    val medicalNotes = uiState.medicalInfo?.conditions.orEmpty().trim()
    val fullName = profile?.fullName?.takeIf { it.isNotBlank() }
        ?: uiState.username?.takeIf { it.isNotBlank() }
        ?: "AGAP User"
    val role = uiState.role?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: "CITIZEN"

    val readinessChecks = listOf(
        !profile?.phone.isNullOrBlank(),
        !profile?.email.isNullOrBlank(),
        medicalNotes.isNotBlank(),
        uiState.emergencyContacts.isNotEmpty(),
        uiState.preferences.autoSendGps,
        uiState.preferences.enableBleFallback
    )
    val readinessCount = readinessChecks.count { it }
    val readinessProgress = readinessCount / readinessChecks.size.toFloat()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLogout()
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedbackMessage()
        }
    }

    LaunchedEffect(profile, uiState.medicalInfo) {
        editName = profile?.fullName.orEmpty()
        editEmail = profile?.email.orEmpty()
        editPhone = profile?.phone.orEmpty()
        editMedical = medicalNotes
    }

    val resetContactEditor: () -> Unit = {
        editingContact = null
        contactName = ""
        contactPhone = ""
        contactRelation = ""
        contactIsPrimary = false
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = when (role) {
                    "ADMIN" -> "ADMIN PROFILE"
                    "RESPONDER" -> "RESPONDER PROFILE"
                    else -> "CITIZEN PROFILE"
                },
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshProfile,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AgapSpacing.lg,
                    end = AgapSpacing.lg,
                    top = AgapSpacing.md,
                    bottom = AgapSpacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                item {
                    PremiumProfileHeader(
                        name = fullName,
                        role = role,
                        accountStatus = uiState.accountStatus,
                        isVerified = uiState.isVerified,
                        memberSince = profile?.memberSince,
                        lastLoginAt = uiState.lastLoginAt
                    )
                }

                item {
                    EmergencyReadinessCard(
                        progress = readinessProgress,
                        completed = readinessCount,
                        total = readinessChecks.size,
                        emergencyContacts = uiState.emergencyContacts.size,
                        autoGps = uiState.preferences.autoSendGps,
                        bleFallback = uiState.preferences.enableBleFallback,
                        pendingSync = uiState.pendingPacketCount
                    )
                }

                item {
                    ProfileSection(
                        eyebrow = "IDENTITY",
                        title = "Personal information",
                        subtitle = "Information used to identify and contact you.",
                        icon = Icons.Outlined.AccountCircle,
                        accent = PaletteBlueDark,
                        actionLabel = "EDIT",
                        onAction = { showEditPersonalDialog = true }
                    ) {
                        ProfileInfoRow(
                            icon = Icons.Outlined.Person,
                            label = "Full name",
                            value = profile?.fullName.orFallback(fullName)
                        )
                        SectionDivider()
                        ProfileInfoRow(
                            icon = Icons.Outlined.Phone,
                            label = "Phone number",
                            value = profile?.phone.orNotProvided()
                        )
                        SectionDivider()
                        ProfileInfoRow(
                            icon = Icons.Outlined.Email,
                            label = "Email address",
                            value = profile?.email.orNotProvided()
                        )

                        AnimatedVisibility(visible = !profile?.dob.isNullOrBlank()) {
                            Column {
                                SectionDivider()
                                ProfileInfoRow(
                                    icon = Icons.Outlined.CalendarToday,
                                    label = "Birthday",
                                    value = profile?.dob.orNotProvided()
                                )
                            }
                        }

                        AnimatedVisibility(visible = !profile?.gender.isNullOrBlank()) {
                            Column {
                                SectionDivider()
                                ProfileInfoRow(
                                    icon = Icons.Outlined.Badge,
                                    label = "Gender",
                                    value = profile?.gender.orNotProvided()
                                )
                            }
                        }
                    }
                }

                item {
                    MedicalProfileCard(
                        medicalNotes = medicalNotes,
                        onEdit = { showEditMedicalDialog = true }
                    )
                }

                item {
                    EmergencyContactsSection(
                        contacts = uiState.emergencyContacts,
                        isBusy = uiState.isSavingContact,
                        onAdd = {
                            resetContactEditor()
                            showAddContactDialog = true
                        },
                        onCall = { phone ->
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${Uri.encode(phone)}")
                                    )
                                )
                            }
                        },
                        onEdit = { contact ->
                            editingContact = contact
                            contactName = contact.contactName
                            contactPhone = contact.phoneNumber
                            contactRelation = contact.relationship
                            contactIsPrimary = contact.isPrimary
                            showEditContactDialog = true
                        },
                        onSetPrimary = viewModel::setPrimaryEmergencyContact,
                        onDelete = { contactPendingDelete = it }
                    )
                }

                item {
                    ProfileSection(
                        eyebrow = "SOS BEHAVIOR",
                        title = "Emergency preferences",
                        subtitle = "Control how AGAP prepares and relays emergency alerts.",
                        icon = Icons.Outlined.Security,
                        accent = RedPrimary
                    ) {
                        PreferenceRow(
                            icon = Icons.Outlined.NearMe,
                            title = "Auto-send GPS",
                            subtitle = "Attach your current location to new SOS alerts.",
                            checked = uiState.preferences.autoSendGps,
                            accent = RedPrimary,
                            onCheckedChange = viewModel::updateAutoSendGps
                        )
                        SectionDivider()
                        PreferenceRow(
                            icon = Icons.Outlined.Bluetooth,
                            title = "BLE fallback",
                            subtitle = "Allow store-and-forward mesh relay when internet is unavailable.",
                            checked = uiState.preferences.enableBleFallback,
                            accent = PaletteBlueDark,
                            onCheckedChange = viewModel::updateBleFallback
                        )
                        SectionDivider()
                        PreferenceRow(
                            icon = Icons.Outlined.Vibration,
                            title = "Vibration alerts",
                            subtitle = "Use haptic feedback for important status changes.",
                            checked = uiState.preferences.vibrationAlert,
                            accent = PaletteOrangeDark,
                            onCheckedChange = viewModel::updateVibrationAlert
                        )
                        SectionDivider()
                        PreferenceRow(
                            icon = Icons.AutoMirrored.Outlined.VolumeOff,
                            title = "Silent mode",
                            subtitle = "Suppress alert sounds while keeping visual updates.",
                            checked = uiState.preferences.silentMode,
                            accent = PaletteOrangeDark,
                            onCheckedChange = viewModel::updateSilentMode
                        )
                        SectionDivider()
                        PreferenceRow(
                            icon = Icons.Outlined.Share,
                            title = "Offline sharing",
                            subtitle = "Allow supported AGAP data to be shared while offline.",
                            checked = uiState.preferences.offlineSharing,
                            accent = PaletteBlueDark,
                            onCheckedChange = viewModel::updateOfflineSharing
                        )
                    }
                }
                item {
                    RegisteredDevicesSection(
                        devices = uiState.userDevices,
                        currentDeviceId = uiState.currentDeviceId,
                        isBusy = uiState.isDeletingDevice,
                        onDeleteDevice = { devicePendingDelete = it }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                        shape = AgapShape.lg
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(AgapSpacing.sm))
                        Text(
                            text = "LOG OUT OF AGAP",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        EmergencyContactEditorDialog(
            title = "Add emergency contact",
            confirmLabel = "ADD CONTACT",
            name = contactName,
            onNameChange = { contactName = it },
            phone = contactPhone,
            onPhoneChange = { contactPhone = it },
            relationship = contactRelation,
            onRelationshipChange = { contactRelation = it },
            isPrimary = contactIsPrimary,
            onPrimaryChange = { contactIsPrimary = it },
            isSaving = uiState.isSavingContact,
            onDismiss = {
                showAddContactDialog = false
                resetContactEditor()
            },
            onConfirm = {
                viewModel.addEmergencyContact(
                    name = contactName,
                    phone = contactPhone,
                    relation = contactRelation,
                    isPrimary = contactIsPrimary
                )
                showAddContactDialog = false
                resetContactEditor()
            }
        )
    }

    if (showEditContactDialog) {
        EmergencyContactEditorDialog(
            title = "Edit emergency contact",
            confirmLabel = "SAVE CONTACT",
            name = contactName,
            onNameChange = { contactName = it },
            phone = contactPhone,
            onPhoneChange = { contactPhone = it },
            relationship = contactRelation,
            onRelationshipChange = { contactRelation = it },
            isPrimary = contactIsPrimary,
            onPrimaryChange = { contactIsPrimary = it },
            isSaving = uiState.isSavingContact,
            onDismiss = {
                showEditContactDialog = false
                resetContactEditor()
            },
            onConfirm = {
                editingContact?.let { contact ->
                    viewModel.updateEmergencyContact(
                        contactId = contact.id,
                        name = contactName,
                        phone = contactPhone,
                        relation = contactRelation,
                        isPrimary = contactIsPrimary
                    )
                }
                showEditContactDialog = false
                resetContactEditor()
            }
        )
    }

    if (showEditPersonalDialog) {
        EditPersonalInformationDialog(
            name = editName,
            onNameChange = { editName = it },
            email = editEmail,
            onEmailChange = { editEmail = it },
            phone = editPhone,
            onPhoneChange = { editPhone = it },
            isSaving = uiState.isSavingProfile,
            onDismiss = { showEditPersonalDialog = false },
            onConfirm = {
                viewModel.updateProfile(
                    name = editName,
                    email = editEmail,
                    phone = editPhone,
                    medicalInfo = editMedical
                )
                showEditPersonalDialog = false
            }
        )
    }

    if (showEditMedicalDialog) {
        EditMedicalNotesDialog(
            value = editMedical,
            onValueChange = { editMedical = it },
            isSaving = uiState.isSavingProfile,
            onDismiss = { showEditMedicalDialog = false },
            onConfirm = {
                viewModel.updateProfile(
                    name = editName,
                    email = editEmail,
                    phone = editPhone,
                    medicalInfo = editMedical
                )
                showEditMedicalDialog = false
            }
        )
    }

    contactPendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactPendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = RedPrimary
                )
            },
            title = { Text("Remove emergency contact?") },
            text = {
                Text(
                    "${contact.contactName} will no longer appear in your emergency contact list."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEmergencyContact(contact.id)
                        contactPendingDelete = null
                    }
                ) {
                    Text("REMOVE", color = RedPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactPendingDelete = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    devicePendingDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { devicePendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = RedPrimary
                )
            },
            title = { Text("Unlink device?") },
            text = {
                Text(
                    "Device ${device.deviceHardwareId} (${device.deviceName.ifBlank { "Android Device" }}) will no longer be linked to your account."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserDevice(device.id)
                        devicePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("UNLINK DEVICE")
                }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingDelete = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = RedPrimary
                )
            },
            title = { Text("Log out of AGAP?") },
            text = {
                Text("You will need to sign in again to access your account and synchronized profile.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("LOG OUT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}
