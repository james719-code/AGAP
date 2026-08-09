package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.*
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
    val incident = uiState.incident
    var selectedTab by remember { mutableIntStateOf(0) }

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
                    selectedTabIndex = selectedTab,
                    containerColor = cardBg,
                    contentColor = RedPrimary,
                    divider = { HorizontalDivider(color = borderCol.copy(alpha = 0.5f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("DETAILS", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("MESSAGES", fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Map preview card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
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
                                        isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                                        zoomLevel = 17.0
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        shape = RoundedCornerShape(10.dp),
                                        tonalElevation = 4.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = RedPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
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
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Detail Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = incident.emergencyType.uppercase(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RedPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    StatusBadge(status = incident.status)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                DetailRow("Incident ID", incident.incidentId, textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Channel", incident.channel, textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Target Agency", targetAgencyTypeForIncident(incident), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Latitude", "%.6f".format(incident.latitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Longitude", "%.6f".format(incident.longitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("Altitude", "%.2f m".format(incident.altitude), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                DetailRow("GPS Accuracy", "%.2f m".format(incident.gpsAccuracy), textColor, subTextColor)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                                val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
                                DetailRow("Time", timeFormat.format(Date(incident.timestamp)), textColor, subTextColor)
                            }
                        }

                        // Responder actions
                        if (uiState.userRole == "RESPONDER" || uiState.userRole == "ADMIN") {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "RESPONDER ACTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = subTextColor,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            if (uiState.isLoadingDispatches) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = RedPrimary)
                            } else if (uiState.dispatches.isEmpty()) {
                                Text("No assigned dispatches", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = subTextColor)
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
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Communications / Messaging Tab
                    CommunicationsTab(
                        messages = uiState.messages,
                        replyChoices = uiState.replyChoices,
                        userCanReply = uiState.userRole == "ADMIN" || uiState.userRole == "RESPONDER",
                        transmissionMode = uiState.transmissionMode,
                        isSending = uiState.isSendingMessage,
                        onSendReply = viewModel::sendReply,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        uiState.feedbackMessage?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearFeedbackMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1C1E),
                contentColor = Color.White
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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FIXED OPERATIONAL REPLIES",
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

                Spacer(Modifier.height(10.dp))

                if (!userCanReply) {
                    Text(
                        text = "Operational replies from responders and command appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (replyChoices.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "No cached reply catalog yet. Connect to the server once to download the fixed choices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        items(replyChoices, key = { it.id }) { choice ->
                            OutlinedButton(
                                onClick = { onSendReply(choice) },
                                enabled = !isSending,
                                modifier = Modifier.widthIn(min = 150.dp, max = 230.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
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

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!isSystem) {
            Text(
                text = buildString {
                    append(message.senderName)
                    append(" • ")
                    append(message.senderRole)
                    if (message.predefinedCode != null) {
                        append(" • ")
                        append(message.channel)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        
        Surface(
            color = bubbleColor,
            shape = if (isSystem) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.messageContent,
                modifier = Modifier.padding(if (isSystem) 4.dp else 12.dp),
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
                modifier = Modifier.padding(start = 8.dp, top = 3.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, textColor: Color, subTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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

            Spacer(modifier = Modifier.height(10.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
