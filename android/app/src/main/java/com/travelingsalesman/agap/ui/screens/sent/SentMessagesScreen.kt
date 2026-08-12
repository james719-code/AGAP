package com.travelingsalesman.agap.ui.screens.sent

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.core.bluetooth.BleMeshPacketCodec
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.ui.components.*
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SentTab(val title: String) {
    MY_SOS("MY SOS"),
    QUEUE("QUEUE"),
    RELAYED("RELAYED")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentMessagesScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: (() -> Unit)? = null,
    showBackButton: Boolean = false,
    viewModel: SentMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(SentTab.MY_SOS) }

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

    val isCitizen = uiState.userRole.uppercase() == "CITIZEN"

    Scaffold(
        topBar = {
            AgapTopBar(
                title = if (isCitizen) "OFFICIAL ALERTS" else "ALERTS & TELEMETRY",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack,
                onNotificationClick = onNavigateToNotifications,
                onMessageClick = onNavigateToMessages,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingDeliveryCount
            )
        },
        bottomBar = {
            if (isCitizen) {
                DisabledMessageBar(
                    backgroundColor = cardBg,
                    borderColor = borderCol,
                    textColor = subTextColor
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AgapSpacing.lg)
            ) {
                if (isCitizen) {
                    Spacer(modifier = Modifier.height(AgapSpacing.md))
                    Text(
                        text = "Broadcast Feed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = RedPrimary
                    )
                    Text(
                        text = "Official emergency broadcasts and notifications from the Central Command Center.",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor,
                        modifier = Modifier.padding(bottom = AgapSpacing.md)
                    )
                }

                // Header Metrics Card (Hidden for Citizens as they only see Announcements)
                if (!isCitizen) {
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
                                        text = "MESSAGE TELEMETRY METRICS",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = textColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(AgapSpacing.md))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                MetricItem("My SOS", "${uiState.mySosHistory.size}", RedPrimary, subTextColor)
                                MetricItem("Queue", "${uiState.meshQueue.size}", AmberWarning, subTextColor)
                                MetricItem("Relayed", "${uiState.relayedEvents.size}", GreenOnline, subTextColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AgapSpacing.md))
                }

                if (!isCitizen) {
                    // Primary Tab Row for Responders & Admins
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
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) RedPrimary else subTextColor
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AgapSpacing.md))
                }

                // Tab Content
                when {
                    isCitizen -> {
                        if (uiState.announcements.isEmpty()) {
                            EmptyStatePlaceholder(
                                message = "No admin announcements yet.",
                                subMessage = "When the Central Command Center sends an official broadcast, it will appear here.",
                                cardBg = cardBg,
                                borderCol = borderCol,
                                textColor = textColor,
                                subTextColor = subTextColor
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                item {
                                    LatestAnnouncementsHeader(
                                        count = uiState.announcements.size,
                                        textColor = textColor
                                    )
                                }

                                items(uiState.announcements, key = { "announcement_${it.id}" }) { announcement ->
                                    AnnouncementCard(
                                        announcement = announcement,
                                        textColor = textColor,
                                        subTextColor = subTextColor
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(AgapSpacing.sm))
                                    AdminInfoBox(
                                        subTextColor = subTextColor
                                    )
                                }
                            }
                        }
                    }
                    selectedTab == SentTab.MY_SOS -> {
                        if (uiState.mySosHistory.isEmpty() && uiState.sentPackets.isEmpty()) {
                            EmptyStatePlaceholder("No emergency SOS alerts in your local history.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                            ) {
                                items(uiState.mySosHistory, key = { "my_sos_${it.reportUuid}" }) { report ->
                                    LocalSosCard(
                                        report = report,
                                        isCitizen = false,
                                        cardBg = cardBg,
                                        borderCol = borderCol,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        onOpenIncidentTimeline = report
                                            .serverIncidentId
                                            ?.takeIf { incidentId ->
                                                incidentId > 0L &&
                                                    report.syncState.equals(
                                                        "SYNCED",
                                                        ignoreCase = true
                                                    )
                                            }
                                            ?.let { incidentId ->
                                                { onNavigateToDetail(incidentId.toString()) }
                                            }
                                    )
                                }
                                items(uiState.sentPackets, key = { "sent_pkt_${it.packetHash}" }) { packet ->
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
                    selectedTab == SentTab.QUEUE -> {
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
                    selectedTab == SentTab.RELAYED -> {
                        if (uiState.relayedEvents.isEmpty() && uiState.relayedPackets.isEmpty()) {
                            EmptyStatePlaceholder("No mesh packets relayed by this device.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                            ) {
                                items(uiState.relayedEvents, key = { "relayed_evt_${it.id}" }) { event ->
                                    RelayedEventCard(
                                        event = event,
                                        cardBg = cardBg,
                                        borderCol = borderCol,
                                        textColor = textColor,
                                        subTextColor = subTextColor
                                    )
                                }
                                items(uiState.relayedPackets, key = { "relayed_pkt_${it.packetHash}" }) { packet ->
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
                }
            }
        }
    }
}

@Composable
private fun EmptyStatePlaceholder(
    message: String,
    subMessage: String = "",
    cardBg: Color = Color.Unspecified,
    borderCol: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    subTextColor: Color = Color.Unspecified
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = AgapSpacing.section + AgapSpacing.md),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerShape = AgapShape.xxl)
                .padding(vertical = AgapSpacing.xxl, horizontal = AgapSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RadioTowerIllustration()

            Spacer(modifier = Modifier.height(AgapSpacing.xxl))

            Text(
                text = message,
                color = textColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            if (subMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AgapSpacing.sm))
                Text(
                    text = subMessage,
                    color = subTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AgapSpacing.md)
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            AdminInfoBox(
                subTextColor = subTextColor
            )
        }
    }
}

@Composable
private fun DisabledMessageBar(
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(AgapSpacing.lg)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(
                        cornerShape = AgapShape.pill, 
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        borderColor = borderColor.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                            tint = RedPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Direct replies are disabled for admin announcements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
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
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (packet.isOriginLocal) "MY LOCAL SOS QUEUE" else "PEER MESH RELAY QUEUE",
                    color = if (packet.isOriginLocal) RedPrimary else BlueMedical,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(
                text = "Token: ${packet.transportToken}",
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${packet.packetType} • Hop ${packet.hopCount} • Priority ${packet.priority} • Attempts ${packet.advertiseCount}",
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Queued: $created • Expiry: $expires",
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
                    modifier = Modifier.width(12.dp).padding(end = AgapSpacing.xs)
                )
                Text("RETRY GATT RELAY", style = MaterialTheme.typography.labelSmall, color = RedPrimary)
            }
        }
    }
}

@Composable
private fun RelayedEventCard(
    event: com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color
) {
    val recordedStr = remember(event.recordedAt) {
        SimpleDateFormat("HH:mm:ss - MMM dd, yyyy", Locale.getDefault()).format(Date(event.recordedAt))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Success",
                        tint = GreenOnline,
                        modifier = Modifier.size(16.dp).padding(end = AgapSpacing.xs)
                    )
                    Text(
                        text = "SUCCESSFUL RELAY",
                        color = GreenOnline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = event.packetType.uppercase(),
                    color = BlueMedical,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xs))

            Text(
                text = "Packet Hash: ${event.packetHash.take(20)}...",
                color = textColor,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Destination Peer: ${event.deviceIdentifier ?: "Known Gateway Peer"}",
                color = subTextColor,
                style = MaterialTheme.typography.bodySmall
            )

            val hopText = event.hopCount?.let { "Hop Count: $it" } ?: "Relayed via GATT"
            val locText = if (event.latitude != null && event.longitude != null) {
                " | Lat: %.4f, Lon: %.4f".format(event.latitude, event.longitude)
            } else ""
            Text(text = "$hopText$locText", color = subTextColor, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(text = "Relayed at: $recordedStr", color = subTextColor, style = MaterialTheme.typography.bodySmall)
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
                val badgeColor = EmergencyType.fromCode(packet.emergencyType).color
                Text(
                    text = packet.emergencyType.uppercase(),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when {
                        deliveredToPeer -> "DELIVERED TO PEER"
                        packet.hopCount > 0 -> "GATT RELAY (Hop ${packet.hopCount})"
                        else -> "QUEUED FOR DELIVERY"
                    },
                    color = if (deliveredToPeer) GreenOnline else BlueMedical,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
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

            Spacer(modifier = Modifier.height(AgapSpacing.xs))

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
                        modifier = Modifier.width(12.dp).padding(end = AgapSpacing.xs)
                    )
                        Text("RETRY GATT RELAY", style = MaterialTheme.typography.labelSmall, color = RedPrimary)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = GreenOnline,
                            modifier = Modifier.size(14.dp).padding(end = AgapSpacing.xs)
                        )
                        Text(
                            text = "Peer ACK confirmed",
                            color = GreenOnline,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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
