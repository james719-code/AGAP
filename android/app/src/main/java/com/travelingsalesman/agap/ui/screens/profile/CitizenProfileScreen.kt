package com.travelingsalesman.agap.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.PaletteBlueDark
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard

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
    val displayId = profile?.citizenId?.takeIf { it.isNotBlank() }
        ?: "AGAP-${uiState.userId ?: 0}"
    val role = uiState.role?.uppercase()?.takeIf { it.isNotBlank() } ?: "CITIZEN"

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
                onBackClick = onNavigateBack,
                actions = {
                    if (uiState.isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = RedPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = viewModel::refreshProfile) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh profile",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    displayId = displayId,
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
                AccountSessionCard(
                    accountStatus = uiState.accountStatus,
                    isVerified = uiState.isVerified,
                    lastLoginAt = uiState.lastLoginAt,
                    pendingPackets = uiState.pendingPacketCount
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "LOG OUT OF AGAP",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
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

@Composable
private fun PremiumProfileHeader(
    name: String,
    displayId: String,
    role: String,
    accountStatus: String?,
    isVerified: Boolean,
    memberSince: String?,
    lastLoginAt: String?
) {
    val statusColor = accountStatusColor(accountStatus)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl),
        color = MaterialTheme.colorScheme.surface,
        shape = AgapShape.xl,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(RedPrimary.copy(alpha = 0.12f))
                        .border(1.dp, RedPrimary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(3.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactStatusChip(
                            label = role,
                            color = PaletteBlueDark,
                            icon = Icons.Outlined.Badge
                        )
                        if (isVerified) {
                            CompactStatusChip(
                                label = "VERIFIED",
                                color = GreenActive,
                                icon = Icons.Outlined.VerifiedUser
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                text = "AGAP ACCOUNT ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = displayId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "STATUS",
                    value = accountStatus?.replace("_", " ")?.uppercase() ?: "UNKNOWN",
                    color = statusColor
                )
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "MEMBER SINCE",
                    value = formatDateCompact(memberSince),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HeaderMetric(
                    modifier = Modifier.weight(1f),
                    label = "LAST LOGIN",
                    value = formatDateCompact(lastLoginAt),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HeaderMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.md,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactStatusChip(
    label: String,
    color: Color,
    icon: ImageVector? = null
) {
    Surface(
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        shape = AgapShape.pill
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = color,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun EmergencyReadinessCard(
    progress: Float,
    completed: Int,
    total: Int,
    emergencyContacts: Int,
    autoGps: Boolean,
    bleFallback: Boolean,
    pendingSync: Int
) {
    val readinessColor = when {
        progress >= 0.85f -> GreenActive
        progress >= 0.60f -> PaletteOrangeDark
        else -> RedPrimary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(readinessColor.copy(alpha = 0.12f), AgapShape.md),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = readinessColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMERGENCY PROFILE READINESS",
                        color = readinessColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = "$completed of $total essentials configured",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = readinessColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(AgapShape.pill),
                color = readinessColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(13.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Call,
                    value = emergencyContacts.toString(),
                    label = "CONTACTS",
                    active = emergencyContacts > 0,
                    activeColor = GreenActive
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.LocationOn,
                    value = if (autoGps) "ON" else "OFF",
                    label = "AUTO GPS",
                    active = autoGps,
                    activeColor = PaletteBlueDark
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Bluetooth,
                    value = if (bleFallback) "READY" else "OFF",
                    label = "BLE RELAY",
                    active = bleFallback,
                    activeColor = PaletteBlueDark
                )
                ReadinessMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Sync,
                    value = pendingSync.toString(),
                    label = "PENDING",
                    active = pendingSync == 0,
                    activeColor = if (pendingSync == 0) GreenActive else PaletteOrangeDark
                )
            }
        }
    }
}

@Composable
private fun ReadinessMetric(
    icon: ImageVector,
    value: String,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.07f),
        shape = AgapShape.md
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileSection(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AgapShape.xl,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(AgapSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(accent.copy(alpha = 0.10f), AgapShape.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
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
private fun MedicalProfileCard(
    medicalNotes: String,
    onEdit: () -> Unit
) {
    val configured = medicalNotes.isNotBlank()
    val accent = if (configured) PaletteBlueDark else PaletteOrangeDark

    ProfileSection(
        eyebrow = "RESPONDER CONTEXT",
        title = "Medical profile",
        subtitle = "Keep important conditions, allergies, or medical notes concise and current.",
        icon = Icons.Outlined.MedicalServices,
        accent = accent,
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
                modifier = Modifier.padding(12.dp),
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
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (configured) "MEDICAL NOTES" else "MEDICAL INFORMATION NOT SET",
                        color = accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
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
private fun EmergencyContactsSection(
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
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EMERGENCY NETWORK",
                    color = RedPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Emergency contacts",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "People you may need to reach quickly during an emergency.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(onClick = onAdd, enabled = !isBusy) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("ADD", fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (contacts.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy, onClick = onAdd),
                shape = AgapShape.xl,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(RedPrimary.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "+ ADD EMERGENCY CONTACT",
                        color = RedPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContactDto,
    isBusy: Boolean,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (contact.isPrimary) GreenActive else PaletteBlueDark

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (contact.isPrimary) accent.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

                Spacer(Modifier.width(10.dp))

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
                            Spacer(Modifier.width(6.dp))
                            CompactStatusChip(
                                label = "PRIMARY",
                                color = GreenActive,
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

            Spacer(Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!contact.isPrimary) {
                    TextButton(onClick = onSetPrimary, enabled = !isBusy) {
                        Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PRIMARY", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onCall, enabled = contact.phoneNumber.isNotBlank()) {
                    Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CALL", fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                        tint = RedPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
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
            .padding(vertical = 7.dp),
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

        Spacer(Modifier.width(10.dp))

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

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun AccountSessionCard(
    accountStatus: String?,
    isVerified: Boolean,
    lastLoginAt: String?,
    pendingPackets: Int
) {
    ProfileSection(
        eyebrow = "ACCOUNT",
        title = "Session & synchronization",
        subtitle = "Current account and local synchronization state.",
        icon = Icons.Outlined.Sync,
        accent = GreenActive
    ) {
        ProfileInfoRow(
            icon = Icons.Outlined.Security,
            label = "Account status",
            value = accountStatus?.replace("_", " ")?.uppercase() ?: "UNKNOWN",
            valueColor = accountStatusColor(accountStatus)
        )
        SectionDivider()
        ProfileInfoRow(
            icon = Icons.Outlined.VerifiedUser,
            label = "Verification",
            value = if (isVerified) "Verified" else "Not verified",
            valueColor = if (isVerified) GreenActive else PaletteOrangeDark
        )
        SectionDivider()
        ProfileInfoRow(
            icon = Icons.Outlined.CalendarToday,
            label = "Last login",
            value = formatTimestamp(lastLoginAt)
        )
        SectionDivider()
        ProfileInfoRow(
            icon = Icons.Outlined.Sync,
            label = "Pending offline packets",
            value = pendingPackets.toString(),
            valueColor = if (pendingPackets == 0) GreenActive else PaletteOrangeDark
        )
    }
}

@Composable
private fun SectionDivider() {
    Divider(
        modifier = Modifier.padding(start = 28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    )
}

@Composable
private fun EmergencyContactEditorDialog(
    title: String,
    confirmLabel: String,
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    relationship: String,
    onRelationshipChange: (String) -> Unit,
    isPrimary: Boolean,
    onPrimaryChange: (Boolean) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val canSave = name.isNotBlank() && phone.isNotBlank() && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Call,
                contentDescription = null,
                tint = RedPrimary
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Use a person who can be contacted quickly if needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Contact name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = onRelationshipChange,
                    label = { Text("Relationship") },
                    placeholder = { Text("Parent, spouse, guardian, friend...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors()
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPrimaryChange(!isPrimary) },
                    color = if (isPrimary) GreenActive.copy(alpha = 0.07f)
                    else MaterialTheme.colorScheme.background,
                    shape = AgapShape.md,
                    border = BorderStroke(
                        1.dp,
                        if (isPrimary) GreenActive.copy(alpha = 0.30f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isPrimary,
                            onCheckedChange = onPrimaryChange
                        )
                        Column {
                            Text(
                                text = "Primary emergency contact",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mark the preferred first contact.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun EditPersonalInformationDialog(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = PaletteBlueDark
            )
        },
        title = { Text("Edit personal information") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors(PaletteBlueDark)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors(PaletteBlueDark)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors(PaletteBlueDark)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PaletteBlueDark)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun EditMedicalNotesDialog(
    value: String,
    onValueChange: (String) -> Unit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.MedicalServices,
                contentDescription = null,
                tint = RedPrimary
            )
        },
        title = { Text("Edit medical profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add concise information such as important conditions, allergies, or medical notes. Leave blank if you do not want to store any.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Medical notes") },
                    placeholder = { Text("Example: asthma; allergy to...") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileTextFieldColors(RedPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text("SAVE MEDICAL INFO", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun profileTextFieldColors(
    accent: Color = RedPrimary
) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    focusedLabelColor = accent,
    cursorColor = accent
)

@Composable
private fun accountStatusColor(status: String?): Color = when (status?.uppercase()) {
    "APPROVED", "ACTIVE" -> GreenActive
    "PENDING" -> PaletteOrangeDark
    "SUSPENDED", "REJECTED", "DISABLED" -> RedPrimary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatDateCompact(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return value.take(10)
}

private fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "Not available"
    return value
        .replace("T", " ")
        .replace("Z", "")
        .take(16)
}

private fun String?.orNotProvided(): String =
    this?.takeIf { it.isNotBlank() } ?: "Not provided"

private fun String?.orFallback(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback