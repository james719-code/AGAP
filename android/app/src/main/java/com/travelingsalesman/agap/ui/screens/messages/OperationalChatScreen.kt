package com.travelingsalesman.agap.ui.screens.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OperationalChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isAdmin = uiState.userRole.uppercase() == "ADMIN"
    val isResponder = uiState.userRole.uppercase() == "RESPONDER"

    val activeContact = uiState.selectedContact

    Scaffold(
        topBar = {
            AgapTopBar(
                title = activeContact?.title ?: "OPERATIONAL CONVERSATION",
                showBackButton = true,
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
        ) {
            // Header: Field Responder Channel Selector
            if (uiState.contacts.isNotEmpty()) {
                Surface(
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = borderCol)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AgapSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AgapSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = AgapSpacing.xs)
                            )
                            Text(
                                text = "FIELD RESPONDER CHANNELS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        Text(
                            text = "Select a channel below to view conversation timeline & send operational updates:",
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor,
                            modifier = Modifier.padding(
                                horizontal = AgapSpacing.lg,
                                vertical = AgapSpacing.xs
                            )
                        )

                        Spacer(modifier = Modifier.height(AgapSpacing.xs))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                            contentPadding = PaddingValues(horizontal = AgapSpacing.lg)
                        ) {
                            items(uiState.contacts, key = { it.id }) { contact ->
                                val isSelected = contact.id == uiState.selectedContactId
                                OperationalChannelChipCard(
                                    contact = contact,
                                    isSelected = isSelected,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    onClick = { viewModel.selectContact(contact) }
                                )
                            }
                        }
                    }
                }
            }

            // Active Channel Details Header
            activeContact?.let { contact ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                if (contact.incidentType != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${contact.incidentType.uppercase()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RedPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = contact.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (contact.incidentId != null) {
                            Button(
                                onClick = { onNavigateToDetail(contact.incidentId) },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                shape = AgapShape.xs,
                                contentPadding = PaddingValues(
                                    horizontal = AgapSpacing.sm,
                                    vertical = AgapSpacing.xs
                                )
                            ) {
                                Text(
                                    text = "DETAILS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Conversation Chat View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AgapSpacing.lg)
            ) {
                if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = subTextColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(AgapSpacing.sm))
                            Text(
                                text = if (activeContact?.incidentId != null) {
                                    "No operational messages transmitted on this channel yet."
                                } else {
                                    "No active incident assigned to this responder channel."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = subTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                        contentPadding = PaddingValues(vertical = AgapSpacing.md)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatBubble(message = message, currentUserId = uiState.userId)
                        }
                    }
                }
            }

            // Bottom Quick-Reply Bar
            Surface(
                color = cardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = borderCol)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AgapSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PREDEFINED OPERATIONAL REPLY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (uiState.isSendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = RedPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AgapSpacing.xs))

                    val canSend = uiState.activeIncident != null && (isAdmin || isResponder)

                    if (!canSend) {
                        Text(
                            text = if (uiState.activeIncident == null) {
                                "Channel read-only: assign a unit to an active incident to enable operational chat."
                            } else {
                                "Operational messaging is reserved for command & field responders."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    } else if (uiState.replyChoices.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = AgapShape.md
                        ) {
                            Row(
                                modifier = Modifier.padding(AgapSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = subTextColor)
                                Spacer(Modifier.width(AgapSpacing.sm))
                                Text(
                                    text = "No cached reply catalog available offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subTextColor
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                            contentPadding = PaddingValues(end = AgapSpacing.sm)
                        ) {
                            items(uiState.replyChoices, key = { it.id }) { choice ->
                                OutlinedButton(
                                    onClick = { viewModel.sendReply(choice) },
                                    enabled = !uiState.isSendingMessage,
                                    modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
                                    shape = AgapShape.md,
                                    contentPadding = PaddingValues(
                                        horizontal = AgapSpacing.md,
                                        vertical = AgapSpacing.sm
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = choice.messageText,
                                            maxLines = 2,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${choice.messageType} • Code ${choice.protocolCode}",
                                            fontSize = 9.sp,
                                            color = subTextColor
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
}

@Composable
private fun OperationalChannelChipCard(
    contact: OperationalContactItem,
    isSelected: Boolean,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    val activeBorder = if (isSelected) RedPrimary else borderCol
    val containerBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else cardBg

    Card(
        modifier = Modifier
            .width(220.dp)
            .clip(AgapShape.md)
            .border(width = if (isSelected) 2.dp else 1.dp, color = activeBorder, shape = AgapShape.md)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (contact.role == "COMMAND") Icons.Outlined.Shield else Icons.Outlined.Person,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(AgapSpacing.xs))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            StatusBadge(status = contact.status)
        }
    }
}

@Composable
private fun ChatBubble(message: IncidentMessageEntity, currentUserId: Long?) {
    val isSystem = message.isSystem
    val isOwn = message.senderName.equals("You", ignoreCase = true) ||
        (currentUserId != null && message.senderId == currentUserId)
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
                    color = if (isMesh) Color(0xFF673AB7).copy(alpha = 0.25f) else GreenOnline.copy(alpha = 0.2f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (isMesh) Color(0xFF9C27B0) else GreenOnline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMesh) Icons.AutoMirrored.Outlined.BluetoothSearching else Icons.Outlined.Cloud,
                            contentDescription = if (isMesh) "BLE Mesh" else "Online",
                            tint = if (isMesh) Color(0xFFE040FB) else GreenOnline,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isMesh) "BLE MESH" else "ONLINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMesh) Color(0xFFE040FB) else GreenOnline
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
