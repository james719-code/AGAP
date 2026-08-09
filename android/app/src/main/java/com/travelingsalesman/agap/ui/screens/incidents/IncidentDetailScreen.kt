package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
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
                                OsmdroidMapView(
                                    modifier = Modifier.fillMaxSize(),
                                    incidents = listOf(incident),
                                    isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                                    zoomLevel = 16.0
                                )
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
                        isSending = uiState.isSendingMessage,
                        onSendMessage = viewModel::sendMessage,
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
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }

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
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type an operational message...") },
                    shape = AgapShape.lg,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    containerColor = RedPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: IncidentMessageEntity) {
    val isSystem = message.messageType != "CHAT"
    val alignment = if (isSystem) Alignment.CenterHorizontally else Alignment.Start
    val bubbleColor = if (isSystem) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!isSystem) {
            Text(
                text = "${message.senderName} • ${message.senderRole}",
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
