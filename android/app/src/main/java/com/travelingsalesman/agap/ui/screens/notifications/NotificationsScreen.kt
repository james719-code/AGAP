package com.travelingsalesman.agap.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketCodec
import com.travelingsalesman.agap.data.local.entity.NotificationEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class NotificationTab {
    NOTIFICATIONS, QUEUE, SENT, RELAYED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToIncident: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(NotificationTab.NOTIFICATIONS) }

    DisposableEffect(Unit) {
        viewModel.startAutoSync()
        onDispose { viewModel.stopAutoSync() }
    }

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
                title = "NOTIFICATIONS & QUEUE",
                showBackButton = true,
                onBackClick = onNavigateBack,
                notificationCount = uiState.notifications.count { !it.isRead },
                pendingPacketCount = uiState.pendingPackets.size,
                actions = {
                    IconButton(onClick = viewModel::clearAll) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all notifications")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AgapSpacing.lg)
            ) {
                // Header Telemetry Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AgapSpacing.md)
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
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = null,
                                    tint = RedPrimary,
                                    modifier = Modifier.padding(end = AgapSpacing.xs)
                                )
                                Text(
                                    text = "PACKET & ALERT METRICS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AgapSpacing.md))

                        val totalQueued = uiState.meshQueue.size + uiState.pendingPackets.size
                        val totalRelayed = (uiState.relayedPackets.size + uiState.relayedPacketHashes.size).coerceAtLeast(uiState.relayedCount)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricItem("Unread", "${uiState.notifications.count { !it.isRead }}", RedPrimary, subTextColor)
                            MetricItem("Queued", "$totalQueued", AmberWarning, subTextColor)
                            MetricItem("My SOS", "${uiState.sentPackets.size}", RedPrimary, subTextColor)
                            MetricItem("Relayed", "$totalRelayed", BlueMedical, subTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                // Primary Tab Navigation
                val totalQueued = uiState.meshQueue.size + uiState.pendingPackets.size
                val totalRelayed = (uiState.relayedPackets.size + uiState.relayedPacketHashes.size).coerceAtLeast(uiState.relayedCount)

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
                    NotificationTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = when (tab) {
                                        NotificationTab.NOTIFICATIONS -> "NOTIFS (${uiState.notifications.size})"
                                        NotificationTab.QUEUE -> "QUEUE ($totalQueued)"
                                        NotificationTab.SENT -> "SENT SOS (${uiState.sentPackets.size})"
                                        NotificationTab.RELAYED -> "RELAYED ($totalRelayed)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) RedPrimary else subTextColor
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                // Tab Content Display
                when (selectedTab) {
                    NotificationTab.NOTIFICATIONS -> {
                        if (uiState.notifications.isEmpty()) {
                            EmptyStatePlaceholder("No new notifications available.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.notifications, key = { "notif_${it.id}" }) { notification ->
                                    NotificationCard(
                                        notification = notification,
                                        onClick = {
                                            viewModel.markAsRead(notification.id)
                                            if (notification.type == "DISPATCH" && notification.referenceId != null) {
                                                onNavigateToIncident(notification.referenceId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    NotificationTab.QUEUE -> {
                        if (uiState.meshQueue.isEmpty() && uiState.pendingPackets.isEmpty()) {
                            EmptyStatePlaceholder("No packets currently queued in mesh outbox.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (uiState.meshQueue.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "ACTIVE GATT MESH QUEUE",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AmberWarning
                                            )
                                            Text(
                                                "CLEAR QUEUE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RedPrimary,
                                                modifier = Modifier.clickable { viewModel.clearMeshQueue() }
                                            )
                                        }
                                    }

                                    items(uiState.meshQueue, key = { "mesh_queue_${it.queueId}" }) { queued ->
                                        MeshQueueCard(
                                            packet = queued,
                                            cardBg = cardBg,
                                            borderCol = borderCol,
                                            textColor = textColor,
                                            subTextColor = subTextColor,
                                            onRelayNow = { viewModel.retryMeshQueueNow(queued.queueId) },
                                            onDelete = { viewModel.deleteMeshQueueItem(queued.queueId) }
                                        )
                                    }
                                }

                                if (uiState.pendingPackets.isNotEmpty()) {
                                    item {
                                        Text(
                                            "PENDING OUTBOUND PACKETS",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BlueMedical,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(uiState.pendingPackets, key = { "packet_${it.packetHash}" }) { packet ->
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
                                }
                            }
                        }
                    }
                    NotificationTab.SENT -> {
                        if (uiState.sentPackets.isEmpty()) {
                            EmptyStatePlaceholder("No personal SOS emergency alerts sent.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            }
                        }
                    }
                    NotificationTab.RELAYED -> {
                        if (uiState.relayedPackets.isEmpty() && uiState.relayedPacketHashes.isEmpty()) {
                            EmptyStatePlaceholder("No mesh packets relayed by this device yet.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (uiState.relayedPackets.isNotEmpty()) {
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
                                }

                                if (uiState.relayedPacketHashes.isNotEmpty()) {
                                    item {
                                        Text(
                                            "RECENTLY RELAYED PACKET HASHES",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GreenOnline,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(uiState.relayedPacketHashes) { hash ->
                                        RelayedPacketItem(hash)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun RelayedPacketItem(hash: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, GreenOnline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = GreenOnline, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Packet #$hash",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MeshQueueCard(
    packet: RelayQueueEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onRelayNow: () -> Unit,
    onDelete: () -> Unit
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
                val badgeColor = when (emergencyLabel.uppercase()) {
                    "ACCIDENT" -> Color(0xFFF59E0B)
                    "CRIME" -> RedPrimary
                    "DISASTER" -> Color(0xFFA855F7)
                    "LOCATION_UPDATE" -> BlueMedical
                    else -> RedPrimary
                }
                Text(
                    text = emergencyLabel.uppercase(),
                    color = badgeColor,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRelayNow,
                    shape = AgapShape.xs,
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.width(12.dp).padding(end = 4.dp)
                    )
                    Text("RETRY GATT RELAY", style = MaterialTheme.typography.labelSmall, color = RedPrimary)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete queue item",
                        tint = subTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                            imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
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
private fun NotificationCard(
    notification: NotificationEntity,
    onClick: () -> Unit
) {
    val icon = when (notification.type) {
        "DISPATCH" -> Icons.Default.NearMe
        "STATUS_CHANGE" -> Icons.Default.Info
        "ALERT" -> Icons.Default.Warning
        else -> Icons.Default.Notifications
    }
    val color = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else RedPrimary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.xl,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (notification.isRead) null else androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val timeStr = remember(notification.createdAt) {
                    SimpleDateFormat("HH:mm • MMM d", Locale.getDefault()).format(Date(notification.createdAt))
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(RedPrimary)
                )
            }
        }
    }
}
