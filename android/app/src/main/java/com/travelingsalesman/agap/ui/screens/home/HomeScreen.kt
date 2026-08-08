package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import com.travelingsalesman.agap.ui.components.ReportIncidentDialog
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.EmergencyTypePicker
import com.travelingsalesman.agap.ui.components.SosButton
import com.travelingsalesman.agap.ui.components.SystemStatusCard
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun HomeScreen(
    onNavigateToIncidentList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReport: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showReportDialog by remember { mutableStateOf(false) }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "CITIZEN SOS",
                onProfileClick = onNavigateToProfile,
                notificationCount = if (uiState.pendingPacketCount > 0) uiState.pendingPacketCount else 0,
                bleMeshState = uiState.bleMeshState
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // User Greeting Header & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${uiState.username ?: "Citizen"}${uiState.userDisplayId?.let { " (ID: $it)" } ?: ""}",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Your Lifetime. Always.",
                        color = subTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = onNavigateToIncidentList) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("FEED", color = RedPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }

            // --- HERO SECTION: INSTANT EMERGENCY ACCESS (Zero Scroll Required) ---
            EmergencyTypePicker(
                selectedTypeCode = uiState.selectedEmergencyTypeCode,
                onTypeSelected = viewModel::selectEmergencyType,
                emergencyTypes = uiState.emergencyTypes
            )

            SosButton(
                onClick = viewModel::triggerSos,
                isLoading = uiState.isSendingSos
            )

            // Active SOS Incident Status Card (if emergency active)
            uiState.activeIncident?.let { incident ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(width = 1.5.dp, color = RedPrimary, shape = RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MY ACTIVE EMERGENCY SOS",
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp
                            )
                            com.travelingsalesman.agap.ui.components.StatusBadge(status = incident.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incident #${incident.incidentId} • ${incident.emergencyType}",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (incident.status) {
                                "RECEIVED" -> "Alert received. Awaiting admin acknowledgment..."
                                "ACKNOWLEDGED" -> "Emergency report acknowledged by command."
                                "DISPATCHED" -> "Emergency units dispatched to your location!"
                                "EN_ROUTE" -> "Emergency units are en route."
                                "ON_SCENE" -> "Emergency units are on scene."
                                "RESOLVED" -> "Incident marked resolved by emergency response team."
                                else -> "Status: ${incident.status}"
                            },
                            color = subTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Detailed Incident Report Button
            OutlinedButton(
                onClick = onNavigateToReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "REPORT DETAILED HAZARD / INCIDENT",
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (showReportDialog) {
                ReportIncidentDialog(
                    emergencyTypes = uiState.emergencyTypes,
                    onDismissRequest = { showReportDialog = false },
                    onStartDetailedReport = { typeStr ->
                        viewModel.selectEmergencyType(typeStr)
                        onNavigateToReport()
                    }
                )
            }

            // --- SECONDARY SYSTEM & BLE MESH STATUS SECTION ---
            SystemStatusCard(isOnline = uiState.isOnline)

            // Bluetooth Low Energy Mesh Relay Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val statusText = when {
                        uiState.bleMeshState.isAdvertising -> "BROADCASTING"
                        uiState.bleMeshState.isScanning -> "SCANNING"
                        !uiState.bleMeshState.isEnabled -> "BLUETOOTH OFF"
                        else -> "PERMISSIONS REQUIRED"
                    }
                    val statusColor = when {
                        uiState.bleMeshState.isAdvertising -> RedPrimary
                        uiState.bleMeshState.isScanning -> com.travelingsalesman.agap.ui.theme.GreenOnline
                        !uiState.bleMeshState.isEnabled -> com.travelingsalesman.agap.ui.theme.AmberWarning
                        else -> RedPrimary
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Bluetooth,
                                contentDescription = null,
                                tint = if (uiState.bleMeshState.isScanning || uiState.bleMeshState.isAdvertising) com.travelingsalesman.agap.ui.theme.BlueMedical else subTextColor,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "BLUETOOTH MESH RELAY",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    if (!uiState.bleMeshState.isScanning && !uiState.bleMeshState.isAdvertising) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        if (!uiState.bleMeshState.isEnabled) {
                            TextButton(
                                onClick = {
                                    (context as? com.travelingsalesman.agap.MainActivity)?.requestBluetoothEnable()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("TURN ON BLUETOOTH", color = com.travelingsalesman.agap.ui.theme.AmberWarning, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    (context as? com.travelingsalesman.agap.MainActivity)?.checkAndRequestPermissions()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("GRANT BLUETOOTH & LOCATION PERMISSIONS", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nearby Peers Detected: ${uiState.bleMeshState.nearbyDeviceCount}",
                            color = subTextColor,
                            fontSize = 11.sp
                        )

                        Text(
                            text = "Relayed Packets: ${uiState.bleMeshState.relayedPacketCount}",
                            color = subTextColor,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wi-Fi Open AP Relay:",
                            color = subTextColor,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (uiState.isWifiHotspotBroadcasting) "BROADCASTING OPEN AP" else "HOTSPOT OFF",
                            color = if (uiState.isWifiHotspotBroadcasting) com.travelingsalesman.agap.ui.theme.GreenOnline else subTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    uiState.bleMeshState.activeAdPacketHash?.let { hash ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Broadcasting P2P: ${hash.take(16)}...",
                            color = RedPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Transmission Strategy Toggle Chips
                    Text(
                        text = "NETWORK TRANSMISSION STRATEGY",
                        color = subTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val activeMode = uiState.transmissionMode
                        val autoSelected = activeMode == com.travelingsalesman.agap.data.local.TransmissionMode.AUTO
                        val meshSelected = activeMode == com.travelingsalesman.agap.data.local.TransmissionMode.FORCE_BLE_MESH
                        val internetSelected = activeMode == com.travelingsalesman.agap.data.local.TransmissionMode.FORCE_INTERNET

                        FilterChip(
                            selected = autoSelected,
                            onClick = { viewModel.selectTransmissionMode(com.travelingsalesman.agap.data.local.TransmissionMode.AUTO) },
                            label = { Text("AUTO / HYBRID", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RedPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = meshSelected,
                            onClick = { viewModel.selectTransmissionMode(com.travelingsalesman.agap.data.local.TransmissionMode.FORCE_BLE_MESH) },
                            label = { Text("BLE MESH ONLY", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.travelingsalesman.agap.ui.theme.BlueMedical,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = internetSelected,
                            onClick = { viewModel.selectTransmissionMode(com.travelingsalesman.agap.data.local.TransmissionMode.FORCE_INTERNET) },
                            label = { Text("INTERNET DIRECT", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.travelingsalesman.agap.ui.theme.GreenOnline,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }
            }

            // Feedback Snackbar
            uiState.sosFeedbackMessage?.let { msg ->
                Snackbar(
                    action = {
                        TextButton(onClick = viewModel::clearFeedbackMessage) {
                            Text("DISMISS", color = RedPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = cardBg,
                    contentColor = textColor
                ) {
                    Text(msg)
                }
            }
        }
    }
}


