package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.CameraAlt
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
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun CitizenProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
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
            CitizenProfileTopBar(
                onBackClick = onNavigateBack
            )
        },
        containerColor = PaletteBgDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Section
            DetailedUserHeader(
                name = uiState.userProfile?.fullName ?: "Juan Dela Cruz",
                citizenId = uiState.userProfile?.citizenId ?: "AGAP-10284"
            )

            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusInfoCard(
                    icon = Icons.Outlined.CalendarToday,
                    label = "Member Since",
                    value = uiState.userProfile?.memberSince ?: "May 17, 2024",
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
                    label = "Last Login",
                    value = "May 17, 2026\n9:21 AM",
                    modifier = Modifier.weight(1f)
                )
            }

            // Personal Information
            SectionWrapper(title = "Personal Information", onEditClick = {}) {
                uiState.userProfile?.let { profile ->
                    InfoItem(Icons.Outlined.Person, "Full Name", profile.fullName)
                    InfoItem(Icons.Outlined.CalendarMonth, "Date of Birth", profile.dob)
                    InfoItem(Icons.Outlined.Wc, "Gender", profile.gender)
                    InfoItem(Icons.Outlined.Phone, "Phone Number", profile.phone)
                    InfoItem(Icons.Outlined.Email, "Email Address", profile.email)
                    InfoItem(Icons.Outlined.LocationOn, "Address", profile.address)
                } ?: run {
                    InfoItem(Icons.Outlined.Person, "Full Name", "Juan Dela Cruz")
                    InfoItem(Icons.Outlined.CalendarMonth, "Date of Birth", "January 1, 1998")
                    InfoItem(Icons.Outlined.Wc, "Gender", "Male")
                    InfoItem(Icons.Outlined.Phone, "Phone Number", "0917 123 4567")
                    InfoItem(Icons.Outlined.Email, "Email Address", "juan.delacruz@email.com")
                    InfoItem(Icons.Outlined.LocationOn, "Address", "Rawis, Legazpi City, Albay")
                }
            }

            // Medical Information
            SectionWrapper(title = "Medical Information", onEditClick = {}) {
                uiState.medicalInfo?.let { info ->
                    InfoItem(Icons.Outlined.WaterDrop, "Blood Type", info.bloodType, valueColor = RedPrimary)
                    InfoItem(Icons.Outlined.AcUnit, "Allergies", info.allergies)
                    InfoItem(Icons.Outlined.MonitorHeart, "Medical Conditions", info.conditions)
                    InfoItem(Icons.Outlined.Medication, "Medications", info.medications)
                    InfoItem(Icons.AutoMirrored.Outlined.Assignment, "Emergency Notes", info.emergencyNotes)
                } ?: run {
                    InfoItem(Icons.Outlined.WaterDrop, "Blood Type", "O+", valueColor = RedPrimary)
                    InfoItem(Icons.Outlined.AcUnit, "Allergies", "No known allergies")
                    InfoItem(Icons.Outlined.MonitorHeart, "Medical Conditions", "None")
                    InfoItem(Icons.Outlined.Medication, "Medications", "None")
                    InfoItem(Icons.AutoMirrored.Outlined.Assignment, "Emergency Notes", "N/A")
                }
            }

            // Emergency Contacts
            SectionWrapper(title = "Emergency Contacts", onEditClick = {}) {
                if (uiState.emergencyContacts.isEmpty()) {
                    EmergencyContactItem(
                        name = "Maria Cruz (Mother)",
                        phone = "0917 123 4567",
                        isPrimary = true
                    )
                } else {
                    uiState.emergencyContacts.forEach { contact ->
                        EmergencyContactItem(
                            name = contact.name,
                            phone = contact.phone,
                            isPrimary = contact.isPrimary
                        )
                    }
                }
                TextButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Emergency Contact", fontWeight = FontWeight.Bold)
                }
            }

            // Emergency Preferences
            SectionWrapper(title = "Emergency Preferences", onEditClick = {}) {
                PreferenceSwitch(Icons.Outlined.NearMe, "Auto Send GPS", "Attach location when sending alert", uiState.preferences.autoSendGps, viewModel::updateAutoSendGps)
                PreferenceSwitch(Icons.Outlined.Bluetooth, "Enable BLE Fallback", "Use BLE mesh when offline", uiState.preferences.enableBleFallback, viewModel::updateBleFallback)
                PreferenceSwitch(Icons.Outlined.Vibration, "Vibration Alert", "Vibrate on alert updates", uiState.preferences.vibrationAlert, viewModel::updateVibrationAlert)
                PreferenceSwitch(Icons.AutoMirrored.Outlined.VolumeOff, "Silent Mode", "Alerts will be silent", uiState.preferences.silentMode, viewModel::updateSilentMode)
                PreferenceSwitch(Icons.Outlined.Share, "Offline Sharing", "Allow sharing app offline", uiState.preferences.offlineSharing, viewModel::updateOfflineSharing)
            }

            // Connected Devices
            SectionWrapper(title = "Connected Devices", actionLabel = "Manage") {
                DeviceItem(
                    model = "Xiaomi 11T Pro",
                    status = "This device",
                    lastActive = "May 17, 2026 • 9:21 AM"
                )
                TextButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Link Another Device", fontWeight = FontWeight.Bold)
                }
            }

            // Activity Summary
            SectionWrapper(title = "Activity Summary", actionLabel = "View All") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Alerts Sent", "12", RedPrimary, Modifier.weight(1f))
                        StatBox("Alerts Received", "8", Color(0xFF2196F3), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Relays Completed", "25", Color(0xFF4CAF50), Modifier.weight(1f))
                        StatBox("Lives Impacted", "3", Color(0xFFFFC107), Modifier.weight(1f))
                    }
                }
            }

            // Security
            SectionWrapper(title = "Security") {
                NavigationItem(Icons.Outlined.Lock, "Change Password")
                NavigationItem(Icons.Outlined.VerifiedUser, "Two-Factor Authentication", "Enabled", Color(0xFF4CAF50))
                NavigationItem(Icons.Outlined.Devices, "Active Sessions", "2 active")
            }

            // App & Legal
            SectionWrapper(title = "App & Legal") {
                NavigationItem(Icons.Outlined.Info, "About AGAP")
                NavigationItem(Icons.Outlined.Security, "Privacy Policy")
                NavigationItem(Icons.Outlined.Description, "Terms of Service")
                NavigationItem(Icons.AutoMirrored.Outlined.HelpOutline, "Help & Support")
            }

            // Logout
            TextButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CitizenProfileTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text("Profile", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        // Placeholder to keep title centered if needed, or just leave as is
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
fun DetailedUserHeader(name: String, citizenId: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(50.dp))
            }
            Surface(
                color = Color(0xFF334155),
                shape = CircleShape,
                modifier = Modifier.size(28.dp).offset(x = 4.dp, y = 4.dp),
                border = BorderStroke(2.dp, PaletteBgDark)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.LightGray, modifier = Modifier.padding(6.dp))
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            Text("Citizen ID", color = Color.Gray, fontSize = 12.sp)
            Text(citizenId, color = RedPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verified", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Outlined.QrCode2, contentDescription = "QR Code", tint = Color.Black, modifier = Modifier.padding(4.dp))
            }
            Text("Tap to view", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun StatusInfoCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, isStatus: Boolean = false) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
        if (isStatus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (actionLabel != null) {
                Text(actionLabel, color = RedPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { })
            } else if (onEditClick != null) {
                Text("Edit", color = RedPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onEditClick() })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = PaletteSurfaceDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, title: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun EmergencyContactItem(name: String, phone: String, isPrimary: Boolean) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(RedPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(phone, color = Color.Gray, fontSize = 12.sp)
                    if (isPrimary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RedPrimary.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text("Primary", color = RedPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {}) {
                Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PreferenceSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedPrimary,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
fun DeviceItem(model: String, status: String, lastActive: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            }
            Text(status, color = Color.Gray, fontSize = 12.sp)
            Text("Last active: $lastActive", color = Color.Gray, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("This year", color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

@Composable
fun NavigationItem(icon: ImageVector, title: String, value: String? = null, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}
