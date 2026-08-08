package com.travelingsalesman.agap.ui.screens.sent

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
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketCodec
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

enum class SentTab {
    QUEUE, SENT, RELAYED, RECEIVED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentMessagesScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    showBackButton: Boolean = false,
    viewModel: SentMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(SentTab.QUEUE) }

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
            AgapTopBar(
                title = "ALERTS & TELEMETRY",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AgapSpacing.lg)
        ) {
            // Outbox Header Statistics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AgapShape.lg)
                    .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(AgapSpacing.md)) {
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
                                modifier = Modifier.padding(end = AgapSpacing.xs)
                            )
                            Text(
                                text = "MESSAGE TELEMETRY METRICS",
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor
                            )
                        }

                        Button(
                            onClick = { viewModel.flushOutboxNow() },
                            enabled = !uiState.isFlushing && (uiState.sentPackets.isNotEmpty() || uiState.relayedPackets.isNotEmpty()),
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = AgapShape.xs,
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
                                Text("FLUSH OUTBOX", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AgapSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem("Pending", "${uiState.pendingDeliveryCount}", RedPrimary, subTextColor)
                        MetricItem("My SOS", "${uiState.sentPackets.size}", RedPrimary, subTextColor)
                        MetricItem("Relayed", "${uiState.relayedCount}", BlueMedical, subTextColor)
                        MetricItem("Received", "${uiState.receivedIncidents.size}", GreenOnline, subTextColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.md))

            // Primary Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = bgColor,
                contentColor = RedPrimary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        color = RedPrimary
                    )
                },
                divider = { HorizontalDivider(color = borderCol.copy(alpha = 0.5f)) }
            ) {
                SentTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) RedPrimary else subTextColor
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.md))

            // Tab Content
            when (selectedTab) {
                SentTab.QUEUE -> {
                    if (uiState.meshQueue.isEmpty()) {
                        EmptyStatePlaceholder("No BLE mesh messages waiting for peer delivery.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            items(uiState.meshQueue, key = { "mesh_queue_${it.queueId}" }) { queued ->
                                MeshQueueCard(
                                    packet = queued,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    onRelayNow = { viewModel.retryMeshQueueNow(queued.queueId) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                }
                SentTab.SENT -> {
                    if (uiState.sentPackets.isEmpty()) {
                        EmptyStatePlaceholder("No personal SOS alerts sent.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            items(uiState.sentPackets, key = { "sent_${it.packetHash}" }) { packet ->
                                PendingPacketCard(
                                    packet = packet,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    onRelay = { viewModel.retryViaMesh(packet) },
                                    onDelete = { viewModel.deletePendingPacket(packet.packetHash) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                }
                SentTab.RELAYED -> {
                    if (uiState.relayedPackets.isEmpty()) {
                        EmptyStatePlaceholder("No mesh packets relayed by this device.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            items(uiState.relayedPackets, key = { "relayed_${it.packetHash}" }) { packet ->
                                PendingPacketCard(
                                    packet = packet,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    deliveredToPeer = true,
                                    onRelay = {},
                                    onDelete = { viewModel.deletePendingPacket(packet.packetHash) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                }
                SentTab.RECEIVED -> {
                    if (uiState.receivedIncidents.isEmpty()) {
                        EmptyStatePlaceholder("No incidents received in your feed.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            items(uiState.receivedIncidents, key = { "received_${it.incidentId}" }) { incident ->
                                ConfirmedIncidentCard(
                                    incident = incident,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    textColor = textColor,
                                    subTextColor = subTextColor
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStatePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

@Composable
private fun MeshQueueCard(
    packet: RelayQueueEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onRelayNow: () -> Unit
) {
    val decoded = remember(packet.payloadBytesHex) {
        BleMeshPacketCodec.decode(packet.payloadBytesHex.hexToBytesOrNull())
    }
    val emergencyLabel = decoded?.emergencyType?.takeIf { it.isNotBlank() } ?: packet.packetType
    val created = remember(packet.createdAtMs) {
        SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(packet.createdAtMs))
    }
    val expires = remember(packet.expiryMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(packet.expiryMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.md)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = emergencyLabel.uppercase(),
                    color = RedPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = if (packet.isOriginLocal) "LOCAL BLE QUEUE" else "MESH RELAY QUEUE",
                    color = BlueMedical,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(
                text = "Token: ${packet.transportToken}",
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${packet.packetType} • Hop ${packet.hopCount} • Attempts ${packet.advertiseCount}",
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Queued: $created • expires $expires",
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))
            OutlinedButton(
                onClick = onRelayNow,
                shape = AgapShape.xs,
                modifier = Modifier.height(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BluetoothSearching,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.width(12.dp).padding(end = 4.dp)
                )
                Text("RETRY GATT RELAY", style = MaterialTheme.typography.labelSmall, color = RedPrimary)
            }
        }
    }
}

private fun String.hexToBytesOrNull(): ByteArray {
    if (length % 2 != 0 || !matches(Regex("^[0-9a-fA-F]+$"))) return ByteArray(0)
    return runCatching {
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrDefault(ByteArray(0))
}

@Composable
private fun PendingPacketCard(
    packet: PendingPacketEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    deliveredToPeer: Boolean = false,
    onRelay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.md)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
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
                    style = MaterialTheme.typography.labelMedium
                )

                // Status tag
                Text(
                    text = when {
                        deliveredToPeer -> "DELIVERED TO PEER"
                        packet.hopCount > 0 -> "GATT RELAY (Hop ${packet.hopCount})"
                        else -> "QUEUED FOR DELIVERY"
                    },
                    color = if (deliveredToPeer) GreenOnline else BlueMedical,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))

            Text(
                text = "Target ID: ${packet.targetId}",
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Hash: ${packet.packetHash.take(18)}... | Lat: %.4f, Lon: %.4f".format(packet.latitude, packet.longitude),
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateStr = remember(packet.timestamp) {
                SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(packet.timestamp))
            }
            Text(text = "Created: $dateStr", color = subTextColor, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!deliveredToPeer) {
                    OutlinedButton(
                        onClick = onRelay,
                        shape = AgapShape.xs,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BluetoothSearching,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.width(12.dp).padding(end = 4.dp)
                        )
                        Text("RETRY GATT RELAY", style = MaterialTheme.typography.labelSmall, color = RedPrimary)
                    }
                } else {
                    Text(
                        text = "Peer ACK confirmed",
                        color = GreenOnline,
                        style = MaterialTheme.typography.labelSmall
                    )
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
            .clip(AgapShape.md)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
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
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    text = "CONFIRMED (${incident.status})",
                    color = GreenOnline,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))

            Text(
                text = "Incident ID: ${incident.incidentId}",
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Target ID: ${incident.targetId}",
                color = subTextColor,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Lat: %.4f, Lon: %.4f".format(incident.latitude, incident.longitude),
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateStr = remember(incident.timestamp) {
                SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(incident.timestamp))
            }
            Text(text = "Logged: $dateStr", color = subTextColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

