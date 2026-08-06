package com.travelingsalesman.agap.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
    onNavigateToSentMessages: () -> Unit = {},
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                notificationCount = if (uiState.pendingPacketCount > 0) uiState.pendingPacketCount else 0
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
                        text = "Hello, ${uiState.username ?: "Citizen"}",
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

            // Telemetry System Status Card
            SystemStatusCard(isOnline = uiState.isOnline)

            // Sent Messages & Outbox Quick Access Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                onClick = onNavigateToSentMessages
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "SENT MESSAGES & MESH OUTBOX",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.pendingPacketCount} packets queued for store & forward uplink",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "VIEW >",
                        color = RedPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Bluetooth Low Energy Mesh Relay Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                            text = if (uiState.bleMeshState.isAdvertising) "BROADCASTING" else if (uiState.bleMeshState.isScanning) "SCANNING" else "PERMISSIONS REQUIRED",
                            color = if (uiState.bleMeshState.isAdvertising) RedPrimary else if (uiState.bleMeshState.isScanning) com.travelingsalesman.agap.ui.theme.GreenOnline else RedPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    if (!uiState.bleMeshState.isScanning && !uiState.bleMeshState.isAdvertising) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        TextButton(
                            onClick = {
                                (context as? com.travelingsalesman.agap.MainActivity)?.checkAndRequestPermissions()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("GRANT BLUETOOTH & LOCATION PERMISSIONS", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nearby Peers: ${uiState.bleMeshState.nearbyDeviceCount}",
                            color = subTextColor,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Relayed Packets: ${uiState.bleMeshState.relayedPacketCount}",
                            color = subTextColor,
                            fontSize = 11.sp
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

            // Quick Alerts 4-Box Emergency Selector
            EmergencyTypePicker(
                selectedType = uiState.selectedEmergencyType,
                onTypeSelected = viewModel::selectEmergencyType
            )

            // Central Glowing Pulsing SOS Button
            SosButton(
                onClick = viewModel::triggerSos,
                isLoading = uiState.isSendingSos
            )

            // Active SOS Incident Status Card for Citizen
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
                                "ACTIVE" -> "Alert sent. Awaiting admin dispatch..."
                                "DISPATCHED" -> "Emergency units dispatched to your location!"
                                "RESOLVED" -> "Incident marked resolved by emergency response team."
                                else -> "Status: ${incident.status}"
                            },
                            color = subTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Embedded OSMDroid Incident Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize(),
                        userLocation = uiState.userLocation,
                        incidents = uiState.incidents,
                        isDarkTheme = isDarkTheme
                    )
                }
            }

            // Feedback Snackbar
            uiState.sosFeedbackMessage?.let { msg ->
                Snackbar(
                    action = {
                        TextButton(onClick = viewModel::clearFeedbackMessage) {
                            Text("DISMISS", color = Color.White)
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


