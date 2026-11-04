package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.*
import com.travelingsalesman.agap.ui.theme.AgapStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.startAutoSync()
        onDispose { viewModel.stopAutoSync() }
    }
    val incident = uiState.incident
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAssignDialog by remember { mutableStateOf(false) }
    val canUseMessages = uiState.canAccessMessages

    LaunchedEffect(canUseMessages) {
        if (!canUseMessages && selectedTab == 1) selectedTab = 0
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "INCIDENT DETAILS",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        if (incident == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else {
            val emergencyType = EmergencyType.fromCode(incident.emergencyType)
            val hasIncidentCoordinates =
                incident.latitude in -90.0..90.0 &&
                    incident.longitude in -180.0..180.0 &&
                    !(incident.latitude == 0.0 && incident.longitude == 0.0)

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabRow(
                    selectedTabIndex = if (canUseMessages) selectedTab else 0,
                    containerColor = cardBg,
                    contentColor = RedPrimary,
                    divider = { HorizontalDivider(color = borderCol.copy(alpha = 0.5f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("DETAILS", fontWeight = FontWeight.Bold) }
                    )
                    if (canUseMessages) {
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("MESSAGES", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                if (selectedTab == 0 || !canUseMessages) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(AgapSpacing.lg)
                    ) {
                        // Map preview card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(AgapShape.lg)
                                .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (hasIncidentCoordinates) {
                                    // Passing targetIncident is deliberate: OsmdroidMapView
                                    // both creates the focused incident marker and moves the
                                    // camera to these exact stored coordinates.
                                    OsmdroidMapView(
                                        modifier = Modifier.fillMaxSize(),
                                        incidents = listOf(incident),
                                        targetIncident = incident,
                                        isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                        zoomLevel = 17.0
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(AgapSpacing.sm),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        shape = AgapShape.sm,
                                        tonalElevation = 4.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = AgapSpacing.sm,
                                                vertical = AgapSpacing.sm
                                            ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = RedPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(AgapSpacing.xs))
                                            Text(
                                                text = "%.6f, %.6f".format(incident.latitude, incident.longitude),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(AgapSpacing.xxl),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.LocationOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(AgapSpacing.sm))
                                        Text(
                                            "Incident coordinates unavailable",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "The map will pin this incident as soon as valid GPS coordinates are stored.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AgapSpacing.lg))

                        // Detail Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AgapShape.lg)
                                .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                            shape = AgapShape.lg,
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Column(modifier = Modifier.padding(AgapSpacing.lg)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = emergencyType.label.uppercase(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = emergencyType.color,
                                        letterSpacing = 0.5.sp
                                    )
                                    StatusBadge(status = incident.status)
                                }

                                Spacer(modifier = Modifier.height(AgapSpacing.md))

                                DetailRow("Incident ID", incident.incidentId, textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Channel", incident.channel, textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Target Agency", targetAgencyTypeForIncident(incident), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Latitude", "%.6f".format(incident.latitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Longitude", "%.6f".format(incident.longitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Altitude", "%.2f m".format(incident.altitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("GPS Accuracy", "%.2f m".format(incident.gpsAccuracy), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = AgapSpacing.xs), color = borderCol.copy(alpha = 0.5f))
                                val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                                DetailRow("Time", timeFormat.format(Date(incident.timestamp)), textColor, subTextColor)
                            }
                        }

                        // Responder actions
                        if (uiState.userRole.uppercase() == "RESPONDER" || uiState.userRole.uppercase() == "ADMIN") {
                            Spacer(modifier = Modifier.height(AgapSpacing.xl))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AgapSpacing.xs, vertical = AgapSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OPERATIONAL DISPATCH & ACTIONS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = subTextColor,
                                    letterSpacing = 1.sp
                                )

                                if (uiState.userRole.uppercase() == "ADMIN") {
                                    Button(
                                        onClick = {
                                            viewModel.fetchAvailableResponders()
                                            showAssignDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                        shape = AgapShape.sm,
                                        contentPadding = PaddingValues(
                                            horizontal = AgapSpacing.md,
                                            vertical = AgapSpacing.xs
                                        )
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(AgapSpacing.xs))
                                        Text("ASSIGN UNIT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(AgapSpacing.sm))

                            // Incident Status Quick Actions Row
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AgapShape.md)
                                    .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
                                colors = CardDefaults.cardColors(containerColor = cardBg)
                            ) {
                                Column(modifier = Modifier.padding(AgapSpacing.md)) {
                                    Text(
                                        text = "Update incident status",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = subTextColor
                                    )
                                    Spacer(modifier = Modifier.height(AgapSpacing.sm))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                                    ) {
                                        val statusOptions = listOf(
                                            "ACKNOWLEDGED" to BlueMedical,
                                            "DISPATCHED" to AmberWarning,
                                            "EN_ROUTE" to AmberWarning,
                                            "ON_SCENE" to RedPrimary,
                                            "RESOLVED" to GreenOnline,
                                            "CANCELLED" to Color.Gray
                                        )
                                        items(statusOptions) { (st, col) ->
                                            OutlinedButton(
                                                onClick = { viewModel.updateStatus(st) },
                                                enabled = !uiState.isUpdatingStatus,
                                                shape = AgapShape.sm,
                                                border = BorderStroke(1.dp, col)
                                            ) {
                                                Text(st, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(AgapSpacing.md))

                            if (uiState.isLoadingDispatches) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = RedPrimary)
                            } else if (uiState.dispatches.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = cardBg,
                                    shape = AgapShape.md,
                                    border = BorderStroke(1.dp, borderCol)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(AgapSpacing.lg),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "No assigned responder units yet.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = subTextColor
                                        )
                                        if (uiState.userRole.uppercase() == "ADMIN") {
                                            Spacer(Modifier.height(AgapSpacing.sm))
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.fetchAvailableResponders()
                                                    showAssignDialog = true
                                                },
                                                shape = AgapShape.sm
                                            ) {
                                                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(AgapSpacing.sm))
                                                Text("Assign Available Field Unit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                uiState.dispatches.forEach { dispatch ->
                                    DispatchActionCard(
                                        dispatch = dispatch,
                                        isUpdating = uiState.isUpdatingStatus,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        borderCol = borderCol,
                                        cardBg = cardBg,
                                        onStatusClick = { status ->
                                            viewModel.updateDispatchStatus(dispatch.id, status)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(AgapSpacing.sm))
                                }
                            }
                        }
                    }
                } else if (canUseMessages) {
                    // Communications / Messaging Tab. Access is already
                    // assignment-gated by the ViewModel, including cached Room data.
                    CommunicationsTab(
                        messages = uiState.messages,
                        replyChoices = uiState.replyChoices,
                        userCanReply = true,
                        transmissionMode = uiState.transmissionMode,
                        isSending = uiState.isSendingMessage,
                        onSendReply = viewModel::sendReply,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Assign Unit Selection Dialog
            if (showAssignDialog) {
                AlertDialog(
                    onDismissRequest = { showAssignDialog = false },
                    title = {
                        Text(
                            text = "Assign responder unit",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Select an available field unit to dispatch to Incident #${incident.incidentId}:",
                                style = MaterialTheme.typography.bodySmall,
                                color = subTextColor
                            )
                            Spacer(Modifier.height(AgapSpacing.md))
                            if (uiState.availableResponders.isEmpty()) {
                                Text(
                                    text = "No available IDLE units found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AmberWarning
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                                ) {
                                    items(uiState.availableResponders) { unit ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = AgapShape.sm
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(AgapSpacing.sm),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = unit.unitCallsign.ifBlank { "Unit #${unit.id}" },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "Agency #${unit.agencyId} • ${unit.status.uppercase()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = subTextColor
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        viewModel.dispatchUnit(unit.id)
                                                        showAssignDialog = false
                                                    },
                                                    enabled = !uiState.isDispatchingUnit,
                                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                                    shape = AgapShape.xs,
                                                    contentPadding = PaddingValues(
                                                        horizontal = AgapSpacing.sm,
                                                        vertical = AgapSpacing.xs
                                                    )
                                                ) {
                                                    Text("DISPATCH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAssignDialog = false }) {
                            Text("CLOSE", color = textColor)
                        }
                    }
                )
            }
        }

        uiState.feedbackMessage?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearFeedbackMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                containerColor = SlateSurface,
                contentColor = SlateTextPrimary
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun CommunicationsTab(
    messages: List<IncidentMessageEntity>,
    replyChoices: List<PredefinedMessageEntity>,
    userCanReply: Boolean,
    transmissionMode: TransmissionMode,
    isSending: Boolean,
    onSendReply: (PredefinedMessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AgapSpacing.lg),
            contentPadding = PaddingValues(vertical = AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md),
            reverseLayout = true
        ) {
            items(messages.reversed(), key = { it.id }) { message ->
                MessageBubble(message)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Operational replies",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = when (transmissionMode) {
                                TransmissionMode.FORCE_BLE_MESH -> "Cached server catalog • Nearby/BLE relay only"
                                TransmissionMode.FORCE_INTERNET -> "Cached server catalog • Internet direct"
                                TransmissionMode.AUTO -> "Cached server catalog • Internet first, nearby relay fallback"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = RedPrimary
                        )
                    }
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                if (!userCanReply) {
                    Text(
                        text = "Operational replies from responders and command appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (replyChoices.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = AgapShape.md
                    ) {
                        Row(
                            modifier = Modifier.padding(AgapSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(AgapSpacing.sm))
                            Text(
                                "No cached reply catalog yet. Connect to the server once to download the fixed choices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                        contentPadding = PaddingValues(end = AgapSpacing.sm)
                    ) {
                        items(replyChoices, key = { it.id }) { choice ->
                            OutlinedButton(
                                onClick = { onSendReply(choice) },
                                enabled = !isSending,
                                modifier = Modifier.widthIn(min = 150.dp, max = 230.dp),
                                shape = AgapShape.md,
                                contentPadding = PaddingValues(
                                    horizontal = AgapSpacing.md,
                                    vertical = AgapSpacing.sm
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        choice.messageText,
                                        maxLines = 2,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${choice.messageType} • Code ${choice.protocolCode}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
private fun MessageBubble(message: IncidentMessageEntity) {
    val isSystem = message.isSystem
    val isOwn = message.senderName.equals("You", ignoreCase = true)
    val alignment = when {
        isSystem -> Alignment.CenterHorizontally
        isOwn -> Alignment.End
        else -> Alignment.Start
    }
    val bubbleColor = when {
        isSystem -> Color.Transparent
        isOwn -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    val isMesh = message.channel.contains("MESH", ignoreCase = true) ||
        message.channel.contains("BLE", ignoreCase = true)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!isSystem) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = AgapSpacing.sm, end = AgapSpacing.sm, bottom = AgapSpacing.xs)
            ) {
                Text(
                    text = buildString {
                        append(message.senderName)
                        append(" • ")
                        append(message.senderRole)
                        if (message.predefinedCode != null) {
                            append(" • Code ")
                            append(message.predefinedCode)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Transport origin badge: BLE MESH vs ONLINE
                Surface(
                    color = if (isMesh) AgapStatus.info.copy(alpha = 0.25f) else GreenOnline.copy(alpha = 0.2f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (isMesh) AgapStatus.info else GreenOnline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMesh) Icons.AutoMirrored.Outlined.BluetoothSearching else Icons.Outlined.Cloud,
                            contentDescription = if (isMesh) "BLE Mesh" else "Online",
                            tint = if (isMesh) AgapStatus.info else GreenOnline,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isMesh) "BLE MESH" else "ONLINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMesh) AgapStatus.info else GreenOnline
                        )
                    }
                }
            }
        }
        
        Surface(
            color = bubbleColor,
            shape = if (isSystem) RectangleShape else AgapShape.md,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.messageContent,
                modifier = Modifier.padding(if (isSystem) AgapSpacing.xs else AgapSpacing.md),
                style = if (isSystem) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = if (isSystem) TextAlign.Center else TextAlign.Start,
                fontWeight = if (isSystem) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (!isSystem && message.deliveryState !in setOf("RECEIVED", "DELIVERED")) {
            Text(
                text = when (message.deliveryState) {
                    "RELAY_QUEUED" -> "Queued for nearby relay"
                    "QUEUED_FOR_INTERNET" -> "Queued for Internet"
                    "SYNC_FAILED" -> "Waiting for manual retry"
                    "SENDING" -> "Sending…"
                    else -> message.deliveryState.replace('_', ' ').lowercase()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AgapSpacing.sm, top = AgapSpacing.xs)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, textColor: Color, subTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgapSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = subTextColor, fontSize = 12.sp)
        Text(text = value, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun targetAgencyTypeForIncident(incident: com.travelingsalesman.agap.data.local.entity.IncidentEntity): String {
    incident.description
        ?.substringAfter("Target agency: ", missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return when (incident.emergencyType.uppercase()) {
        "MEDICAL" -> "MEDICAL"
        "FIRE" -> "FIRE"
        "CRIME" -> "POLICE"
        "ACCIDENT" -> "RESCUE"
        "DISASTER" -> "DRRMO"
        else -> "UNASSIGNED"
    }
}

@Composable
private fun DispatchActionCard(
    dispatch: DispatchDto,
    isUpdating: Boolean,
    textColor: Color,
    subTextColor: Color,
    borderCol: Color,
    cardBg: Color,
    onStatusClick: (String) -> Unit
) {
    val nextStatuses = when (dispatch.status) {
        "ASSIGNED" -> listOf("ACCEPTED", "CANCELLED")
        "ACCEPTED" -> listOf("EN_ROUTE", "CANCELLED")
        "EN_ROUTE" -> listOf("ON_SCENE", "CANCELLED")
        "ON_SCENE" -> listOf("COMPLETED", "CANCELLED")
        else -> emptyList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md),
        shape = AgapShape.md,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dispatch.unitCallsign ?: "Unit #${dispatch.unitId}",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = listOfNotNull(dispatch.agencyName, dispatch.responderName).joinToString(" / ").ifBlank { "Responder assignment" },
                        color = subTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusBadge(status = dispatch.status)
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            if (nextStatuses.isEmpty()) {
                Text(
                    text = "Terminal dispatch state",
                    color = subTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    nextStatuses.forEach { status ->
                        Button(
                            onClick = { onStatusClick(status) },
                            enabled = !isUpdating,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status == "CANCELLED") RedPrimary else when (status) {
                                    "ACCEPTED", "EN_ROUTE" -> AmberWarning
                                    else -> GreenResolved
                                }
                            ),
                            shape = AgapShape.md
                        ) {
                            Text(status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
