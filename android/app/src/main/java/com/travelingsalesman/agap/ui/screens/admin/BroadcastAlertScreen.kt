package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.ui.screens.notifications.NotificationsViewModel
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BroadcastAlertScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isEmergencyMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    // Auto-scroll to bottom when new alerts arrive
    LaunchedEffect(uiState.adminAlerts.size) {
        if (uiState.adminAlerts.isNotEmpty()) {
            listState.animateScrollToItem(uiState.adminAlerts.size - 1)
        }
    }

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chat History Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AgapSpacing.lg)
            ) {
                if (uiState.adminAlerts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = subTextColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(AgapSpacing.sm))
                            Text(
                                text = "No broadcasts sent yet.\nSelect a predefined message below to start.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = subTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                        contentPadding = PaddingValues(vertical = AgapSpacing.md)
                    ) {
                        items(uiState.adminAlerts, key = { it.id }) { alert ->
                        BroadcastBubble(
                            alert = alert,
                            subTextColor = subTextColor,
                            onStopClick = { viewModel.stopBroadcast(alert) }
                        )
                    }
                    }
                }
            }

            // Bottom Selector Area
            Surface(
                color = cardBg,
                tonalElevation = 2.dp,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PREDEFINED BROADCASTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isEmergencyMode) RedPrimary else textColor
                            )
                            if (isEmergencyMode) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = RedPrimary,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        "EMERGENCY",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Emergency Mode", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isEmergencyMode,
                                onCheckedChange = { isEmergencyMode = it },
                                modifier = Modifier.scale(0.7f),
                                colors = SwitchDefaults.colors(checkedThumbColor = RedPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AgapSpacing.xs))

                    if (uiState.broadcastChoices.isEmpty()) {
                        Text(
                            "Loading message catalog...",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                            contentPadding = PaddingValues(bottom = AgapSpacing.xs)
                        ) {
                            items(uiState.broadcastChoices, key = { it.id }) { choice ->
                                OutlinedButton(
                                    onClick = { viewModel.broadcastPredefined(choice, isEmergencyMode) },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.widthIn(min = 120.dp, max = 200.dp),
                                    shape = AgapShape.md,
                                    border = if (isEmergencyMode) BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f)) else BorderStroke(1.dp, borderCol),
                                    contentPadding = PaddingValues(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm)
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = choice.messageText,
                                            maxLines = 2,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEmergencyMode) RedPrimary else textColor
                                        )
                                        Text(
                                            text = "Code ${choice.protocolCode}",
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
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
    }
}

@Composable
private fun BroadcastBubble(
    alert: AdminAlertEntity,
    subTextColor: Color,
    onStopClick: () -> Unit
) {
    val isEmergency = alert.severity == "EMERGENCY" || alert.severity == "CRITICAL"
    val bubbleColor = if (isEmergency) RedPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val borderColor = if (isEmergency) RedPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = AgapSpacing.xs)
        ) {
            if (alert.isActive) {
                Text(
                    text = "STOP BROADCAST",
                    style = MaterialTheme.typography.labelSmall,
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onStopClick)
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Text(
                    text = "TERMINATED",
                    style = MaterialTheme.typography.labelSmall,
                    color = subTextColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = "${alert.authorName ?: "Command"} • ${alert.severity}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            
            // Channel Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ChannelBadge(label = "ONLINE", icon = Icons.Outlined.Cloud, color = GreenOnline)
                ChannelBadge(label = "BLE MESH", icon = Icons.AutoMirrored.Outlined.BluetoothSearching, color = Color(0xFF9C27B0))
            }
        }

        Surface(
            color = bubbleColor,
            shape = AgapShape.md,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(AgapSpacing.md)) {
                if (isEmergency) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, tint = RedPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            alert.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = RedPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = alert.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(Modifier.height(4.dp))
                val timeStr = remember(alert.publishedAt) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.publishedAt))
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ChannelBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
