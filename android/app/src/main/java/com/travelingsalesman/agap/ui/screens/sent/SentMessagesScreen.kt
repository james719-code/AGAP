package com.travelingsalesman.agap.ui.screens.sent

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SentFilter {
    ALL, PENDING_OUTBOX, CONFIRMED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentMessagesScreen(
    onNavigateBack: () -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
    viewModel: SentMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(SentFilter.ALL) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SENT MESSAGES & OUTBOX",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Outbox Header Statistics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "MESSAGE TELEMETRY METRICS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        Button(
                            onClick = { viewModel.flushOutboxNow() },
                            enabled = !uiState.isFlushing && uiState.pendingPackets.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (uiState.isFlushing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(14.dp)
                                        .padding(end = 4.dp)
                                )
                                Text("FLUSH OUTBOX", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem("Queued Outbox", "${uiState.pendingPackets.size}", BlueMedical, subTextColor)
                        MetricItem("Confirmed SOS", "${uiState.confirmedIncidents.size}", GreenOnline, subTextColor)
                        MetricItem("Total Sent", "${uiState.pendingPackets.size + uiState.confirmedIncidents.size}", RedPrimary, subTextColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == SentFilter.ALL,
                    onClick = { selectedFilter = SentFilter.ALL },
                    label = { Text("ALL LOGS (${uiState.pendingPackets.size + uiState.confirmedIncidents.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedFilter == SentFilter.PENDING_OUTBOX,
                    onClick = { selectedFilter = SentFilter.PENDING_OUTBOX },
                    label = { Text("PENDING (${uiState.pendingPackets.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BlueMedical,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedFilter == SentFilter.CONFIRMED,
                    onClick = { selectedFilter = SentFilter.CONFIRMED },
                    label = { Text("CONFIRMED (${uiState.confirmedIncidents.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenOnline,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Packet & Incident List
            val displayPending = if (selectedFilter != SentFilter.CONFIRMED) uiState.pendingPackets else emptyList()
            val displayConfirmed = if (selectedFilter != SentFilter.PENDING_OUTBOX) uiState.confirmedIncidents else emptyList()

            if (displayPending.isEmpty() && displayConfirmed.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No sent messages or pending outbox packets.",
                        color = subTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayPending, key = { "pending_${it.packetHash}" }) { packet ->
                        PendingPacketCard(
                            packet = packet,
                            cardBg = cardBg,
                            borderCol = borderCol,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            onBroadcast = { viewModel.broadcastViaMesh(packet) },
                            onDelete = { viewModel.deletePendingPacket(packet.packetHash) }
                        )
                    }

                    items(displayConfirmed, key = { "confirmed_${it.incidentId}" }) { incident ->
                        ConfirmedIncidentCard(
                            incident = incident,
                            cardBg = cardBg,
                            borderCol = borderCol,
                            textColor = textColor,
                            subTextColor = subTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
    }
}

@Composable
private fun PendingPacketCard(
    packet: PendingPacketEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onBroadcast: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emergency Type Badge
                val badgeColor = when (packet.emergencyType.uppercase()) {
                    "ACCIDENT" -> Color(0xFFF59E0B)
                    "CRIME" -> RedPrimary
                    "DISASTER" -> Color(0xFFA855F7)
                    else -> BlueMedical
                }
                Text(
                    text = packet.emergencyType.uppercase(),
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )

                // Status tag
                Text(
                    text = if (packet.hopCount > 0) "BLE MESH RELAY (Hop ${packet.hopCount})" else "QUEUED FOR UPLINK",
                    color = BlueMedical,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Target ID: ${packet.targetId}",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Hash: ${packet.packetHash.take(18)}... | Lat: %.4f, Lon: %.4f".format(packet.latitude, packet.longitude),
                color = subTextColor,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateStr = remember(packet.timestamp) {
                SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(packet.timestamp))
            }
            Text(text = "Created: $dateStr", color = subTextColor, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBroadcast,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.width(12.dp).padding(end = 4.dp)
                    )
                    Text("RE-BROADCAST P2P", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = subTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmedIncidentCard(
    incident: IncidentEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when (incident.emergencyType.uppercase()) {
                    "ACCIDENT" -> Color(0xFFF59E0B)
                    "CRIME" -> RedPrimary
                    "DISASTER" -> Color(0xFFA855F7)
                    else -> BlueMedical
                }
                Text(
                    text = incident.emergencyType.uppercase(),
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "CONFIRMED (${incident.status})",
                    color = GreenOnline,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Incident ID: ${incident.incidentId}",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Target ID: ${incident.targetId}",
                color = subTextColor,
                fontSize = 11.sp
            )

            Text(
                text = "Lat: %.4f, Lon: %.4f".format(incident.latitude, incident.longitude),
                color = subTextColor,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateStr = remember(incident.timestamp) {
                SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(incident.timestamp))
            }
            Text(text = "Logged: $dateStr", color = subTextColor, fontSize = 10.sp)
        }
    }
}
