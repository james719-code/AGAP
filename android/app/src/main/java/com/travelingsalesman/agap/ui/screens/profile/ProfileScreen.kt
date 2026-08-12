package com.travelingsalesman.agap.ui.screens.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onLogout: () -> Unit,
    showBackButton: Boolean = false,
    onNavigateToDetailedProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: (() -> Unit)? = null,
    onSwitchView: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    var showTerms by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    val closeLocalSubpage = {
        when {
            showTerms -> showTerms = false
            showPrivacy -> showPrivacy = false
            showAbout -> showAbout = false
        }
    }

    BackHandler(enabled = showTerms || showPrivacy || showAbout) {
        closeLocalSubpage()
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = when { showTerms -> "TERMS OF SERVICE"; showPrivacy -> "PRIVACY POLICY"; showAbout -> "ABOUT AGAP"; else -> "PROFILE" },
                showBackButton = showTerms || showPrivacy || showAbout || showBackButton,
                onBackClick = {
                    if (showTerms || showPrivacy || showAbout) {
                        closeLocalSubpage()
                    } else {
                        onNavigateBack()
                    }
                },
                onNotificationClick = onNavigateToNotifications,
                onMessageClick = onNavigateToMessages
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        val content = @Composable {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
            ) {
                // User Header Card
                UserHeaderCard(
                    name = uiState.userProfile?.fullName?.ifBlank { null } ?: uiState.username ?: "User",
                    role = uiState.role ?: "CITIZEN",
                    onClick = onNavigateToDetailedProfile
                )

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

                // Registered Devices Section
                SettingsSection(title = "Registered Devices") {
                    if (uiState.userDevices.isEmpty()) {
                        Text(
                            text = "No devices registered to this account",
                            color = subTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(AgapSpacing.md)
                        )
                    } else {
                        uiState.userDevices.forEach { device ->
                            val isCurrent = !uiState.currentDeviceId.isNullOrBlank() && device.deviceHardwareId == uiState.currentDeviceId
                            TargetRow(
                                name = device.deviceName.ifBlank { device.platform.ifBlank { "Android Device" } } + if (isCurrent) " (This Device)" else "",
                                id = device.deviceHardwareId,
                                icon = Icons.Outlined.Smartphone,
                                onDelete = if (!isCurrent) { { viewModel.deleteUserDevice(device.id) } } else null
                            )
                        }
                    }
                }

                // Emergency Agencies
                if (false) {
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
                }

                SettingsSection(title = "About AGAP", actionLabel = "VIEW DETAILS", onActionClick = { showAbout = true }) {
                    InfoRow(
                        icon = Icons.Outlined.Info,
                        title = "Built for the community",
                        value = "AGAP is a community-focused emergency response tool designed to help people reach responders when they need support most."
                    )
                    InfoRow(
                        icon = Icons.Outlined.Map,
                        title = "Maps",
                        value = "OpenStreetMap contributors and CARTO map tiles; Leaflet powers the web map and osmdroid powers Android maps."
                    )
                    InfoRow(
                        icon = Icons.Outlined.Cloud,
                        title = "Offline delivery",
                        value = "Cellular delivery with phone-to-phone Bluetooth relay fallback when internet service is unavailable."
                    )
                    InfoRow(
                        icon = Icons.Outlined.Security,
                        title = "Built for Ibalong, Legazpi City",
                        value = "Team Fellowship (Traveling Salesman), Heroes of Innovation Challenge 2026."
                    )
                }

                SettingsSection(title = "Legal") {
                    NavigationRow(
                        icon = Icons.Outlined.Gavel,
                        title = "Terms of Service",
                        subtitle = "Read how AGAP is intended to be used.",
                        onClick = { showTerms = true }
                    )
                    NavigationRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "See what data AGAP uses and why.",
                        onClick = { showPrivacy = true }
                    )
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

                Spacer(modifier = Modifier.height(AgapSpacing.sm))
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshProfile,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showTerms || showAbout || showPrivacy) {
                AboutLegalPage(isTerms = showTerms, isPrivacy = showPrivacy)
            } else {
                content()
            }
        }
    }
}

@Composable
private fun AboutLegalPage(isTerms: Boolean, isPrivacy: Boolean) {
    val scroll = rememberScrollState()
    val heading = when { isTerms -> "Using AGAP responsibly"; isPrivacy -> "Your information and safety"; else -> "Community-first emergency coordination" }
    val body = when {
        isTerms -> "AGAP supports community emergency communication and response coordination. It does not replace official emergency services. In a life-threatening emergency, contact local emergency services whenever possible.\n\nUse AGAP only for genuine emergencies and operational coordination. You must provide accurate information, avoid impersonation or harmful content, protect other people’s privacy, and follow directions from authorized responders and local authorities. Availability depends on device capability, connectivity, and participating responders; delivery cannot be guaranteed."
        isPrivacy -> "AGAP may process the information you provide for emergency coordination: account details (such as name, username, phone number and email), emergency contacts, optional medical information, incident reports, approximate or precise location when you grant location permission, device and operational identifiers, and responder status.\n\nWe use this information to authenticate accounts, deliver and route emergency alerts, display relevant map and response information, support offline/Bluetooth relay, and maintain safety and service operations. Medical information and precise location are sensitive: provide only what is necessary for emergency response.\n\nInformation is shared only with the AGAP service, authorized administrators/responders involved in coordination, and the map services needed to render map tiles. We do not sell personal information or use it for advertising. You can review or update profile information in the app and can request account or data assistance from the project administrator."
        else -> "AGAP is envisioned as a practical tool for the community: helping people report emergencies, share their location, and connect with responders even when connectivity is limited. It uses cellular delivery with a Bluetooth relay fallback, OpenStreetMap and CARTO map data, Leaflet on the web, and osmdroid on Android."
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(AgapSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
    ) {
        Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!isTerms && !isPrivacy) {
            SettingsSection(title = "Our vision") {
                InfoRow(Icons.Outlined.Groups, "Built with the community in mind", "We envision AGAP as a practical bridge between citizens, responders, and local emergency coordination—especially when every minute matters.")
            }
            SettingsSection(title = "How AGAP helps") {
                InfoRow(Icons.Outlined.Campaign, "Report and coordinate", "People can send emergency information and location details so the right response team has a clearer picture of what is happening.")
                InfoRow(Icons.Outlined.Route, "Route response information", "Administrators and responders can review incidents, agencies, responder units, and operational status from one system.")
            }
            SettingsSection(title = "Designed for resilience") {
                InfoRow(Icons.Outlined.Bluetooth, "Works beyond one connection", "When conventional connectivity is limited, AGAP can use nearby Bluetooth relay capabilities to help carry emergency messages between participating devices.")
                InfoRow(Icons.Outlined.LocationOn, "Location when it matters", "Location is used only to support emergency reporting, response awareness, and map-based coordination when permission is granted.")
            }
            SettingsSection(title = "Community responsibility") {
                InfoRow(Icons.Outlined.VolunteerActivism, "Use it responsibly", "AGAP is a support tool for real incidents and coordination. It complements—but never replaces—official emergency services and local authority guidance.")
            }
            SettingsSection(title = "Developers") {
                InfoRow(Icons.Outlined.Person, "James Ryan Sentelices Gallego", "Team Leader — Software Development, AI/ML, UI/UX, Disaster Risk Reduction, Education, and Community Development.")
                InfoRow(Icons.Outlined.Person, "Diomar Balasta Molina", "Team Member — Software Development and Research.")
                InfoRow(Icons.Outlined.Person, "Jay Virgil Alto Romero", "Team Member — Software Development, UI/UX, and Creative Industries.")
                InfoRow(Icons.Outlined.Person, "Gil IV Miguel Salvador I. Cea", "Team Member — Software Development, AI/ML, Data Analytics, Community Development, and UI/UX.")
            }
        }
        SettingsSection(title = when { isPrivacy -> "Your controls"; isTerms -> "Map attribution"; else -> "Technology used" }) {
            InfoRow(Icons.Outlined.Map, "OpenStreetMap & CARTO", "Map data and tile attribution.")
            InfoRow(Icons.Outlined.Cloud, "Offline-ready delivery", "Cellular synchronization and Bluetooth relay fallback.")
            InfoRow(Icons.Outlined.PhoneAndroid, "Android & web maps", "osmdroid for Android and Leaflet for the web command center.")
        }
    }
}

@Composable
fun UserHeaderCard(name: String, role: String, onClick: () -> Unit = {}) {
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
                Spacer(modifier = Modifier.height(AgapSpacing.xs))
                Text(role.replace('_', ' ').uppercase(), color = RedPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
fun NavigationRow(icon: ImageVector, title: String, subtitle: String? = null, iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                .padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
