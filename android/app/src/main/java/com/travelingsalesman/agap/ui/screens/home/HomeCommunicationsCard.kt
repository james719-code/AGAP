package com.travelingsalesman.agap.ui.screens.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.glassCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunicationsCard(
    uiState: HomeUiState,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onTransmissionModeSelected: (TransmissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = communicationModeAccent(uiState)
    val activeNode = uiState.bleMeshState.activePeerNodeId?.let(::relayNodeLabel)

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerShape = AgapShape.lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandedChange)
                    .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accent.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.bleMeshState.activeTransport == "BLE_GATT" -> Icons.Outlined.Bluetooth
                                uiState.transmissionMode == TransmissionMode.FORCE_INTERNET -> Icons.Outlined.Wifi
                                uiState.bleMeshState.nearbyTransportActive -> Icons.Outlined.Wifi
                                else -> Icons.Outlined.Security
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(Modifier.size(AgapSpacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = communicationModeTitle(uiState.transmissionMode),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = communicationsSummary(uiState),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse communication mode" else "Open communication mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val isMeshTransferActive = uiState.bleMeshState.isTransferring
                val isSendingEmergency = uiState.isSendingSos
                val isReceivingEmergency = uiState.isReceivingEmergency
                if (isSendingEmergency || isReceivingEmergency || isMeshTransferActive) {
                    Spacer(Modifier.height(AgapSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSendingEmergency || isReceivingEmergency) {
                            Text(
                                text = if (isSendingEmergency && uiState.isOnline) {
                                    "Uploading SOS to emergency command"
                                } else if (isSendingEmergency) {
                                    "Preparing SOS for nearby relay"
                                } else if (uiState.isOnline) {
                                    "Receiving emergency update online"
                                } else {
                                    "Receiving emergency from nearby relay"
                                },
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accent
                            )
                        } else Text(
                            text = "${transferStageLabel(uiState.bleMeshState.transferStage)}${activeNode?.let { " → $it" }.orEmpty()}",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isSendingEmergency && !isReceivingEmergency) {
                            Text(
                                text = "${(uiState.bleMeshState.transferProgress.coerceIn(0f, 1f) * 100).toInt()}%",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(AgapSpacing.xs))
                    if (isSendingEmergency || isReceivingEmergency) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { uiState.bleMeshState.transferProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else if (uiState.pendingPacketCount > 0 && uiState.transmissionMode != TransmissionMode.FORCE_INTERNET) {
                    Spacer(Modifier.height(AgapSpacing.sm))
                    Text(
                        text = "${uiState.pendingPacketCount} packet(s) queued • waiting for an eligible relay node",
                        color = AgapStatus.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = onExpandedChange,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CommunicationOptions(
                    uiState = uiState,
                    context = context,
                    onTransmissionModeSelected = onTransmissionModeSelected
                )
            }
        }
    }
}
@Composable
internal fun CommunicationOptions(
    uiState: HomeUiState,
    context: Context,
    onTransmissionModeSelected: (TransmissionMode) -> Unit
) {
    val accent = communicationModeAccent(uiState)
    val connectedNodes = buildSet {
        addAll(uiState.bleMeshState.nearbyConnectedPeerIds)
        uiState.bleMeshState.activePeerNodeId?.let(::add)
    }.toList().sorted()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(AgapSpacing.lg)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AgapShape.md,
            color = accent.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
        ) {
            Column(modifier = Modifier.padding(AgapSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE COMMUNICATION POLICY",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp
                        )
                        Spacer(Modifier.height(AgapSpacing.xs))
                        Text(
                            text = communicationModeTitle(uiState.transmissionMode),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(shape = AgapShape.pill, color = accent.copy(alpha = 0.14f)) {
                        Text(
                            text = if (uiState.bleMeshState.isTransferring) "TRANSFERRING" else "ACTIVE",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = AgapSpacing.sm,
                                vertical = AgapSpacing.xs
                            )
                        )
                    }
                }
                Spacer(Modifier.height(AgapSpacing.xs))
                Text(
                    text = communicationModeRuntimeSummary(uiState),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(AgapSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            CommunicationMetric(
                modifier = Modifier.weight(1f),
                label = "NEARBY",
                value = uiState.bleMeshState.nearbyDeviceCount.toString(),
                accent = AgapStatus.info
            )
            CommunicationMetric(
                modifier = Modifier.weight(1f),
                label = "CONNECTED",
                value = uiState.bleMeshState.nearbyConnectedCount.toString(),
                accent = if (uiState.bleMeshState.nearbyConnectedCount > 0) AgapStatus.success else AgapStatus.warning
            )
            CommunicationMetric(
                modifier = Modifier.weight(1f),
                label = "PENDING",
                value = uiState.pendingPacketCount.toString(),
                accent = if (uiState.pendingPacketCount > 0) AgapStatus.warning else AgapStatus.success
            )
        }

        Spacer(Modifier.height(AgapSpacing.md))
        RelayTransferPanel(uiState = uiState, accent = accent)

        if (connectedNodes.isNotEmpty()) {
            Spacer(Modifier.height(AgapSpacing.md))
            Text(
                text = "CONNECTED RELAY NODES",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(AgapSpacing.sm))
            connectedNodes.take(4).forEach { nodeId ->
                val isActive = uiState.bleMeshState.activePeerNodeId == nodeId && uiState.bleMeshState.isTransferring
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AgapSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isActive) accent else AgapStatus.success, CircleShape)
                    )
                    Spacer(Modifier.size(AgapSpacing.sm))
                    Text(
                        text = relayNodeLabel(nodeId),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isActive) "ACTIVE HOP" else "CONNECTED",
                        color = if (isActive) accent else AgapStatus.success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (connectedNodes.size > 4) {
                Text(
                    text = "+${connectedNodes.size - 4} more connected node(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(AgapSpacing.md))

        if (!uiState.bleMeshState.isEnabled && !uiState.bleMeshState.nearbyTransportActive) {
            OutlinedButton(
                onClick = { (context as? MainActivity)?.requestBluetoothEnable() },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, AgapStatus.warning.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AgapStatus.warning),
                shape = AgapShape.md
            ) {
                Text("TURN ON BLUETOOTH", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(AgapSpacing.sm))
        } else if (uiState.bleMeshState.needsPermissionsPrompt) {
            OutlinedButton(
                onClick = { (context as? MainActivity)?.checkAndRequestPermissions() },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = AgapShape.md
            ) {
                Text("GRANT NEARBY DEVICES PERMISSION", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(AgapSpacing.sm))
        }

        Text(
            text = "TRANSMISSION MODE",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )
        Spacer(Modifier.height(AgapSpacing.sm))

        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            TransmissionModeChip(
                label = "AUTO / HYBRID",
                subtitle = if (uiState.isOnline) {
                    "Internet preferred • Nearby P2P + native BLE remain ready"
                } else {
                    "Internet unavailable • automatically using Nearby/BLE relay"
                },
                selected = uiState.transmissionMode == TransmissionMode.AUTO,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { onTransmissionModeSelected(TransmissionMode.AUTO) }
            )
            TransmissionModeChip(
                label = "OFFLINE RELAY",
                subtitle = "No cloud sending • Nearby P2P preferred • native BLE fallback",
                selected = uiState.transmissionMode == TransmissionMode.FORCE_BLE_MESH,
                accent = AgapStatus.info,
                onClick = {
                    onTransmissionModeSelected(TransmissionMode.FORCE_BLE_MESH)
                    (context as? MainActivity)?.checkAndRequestPermissions()
                }
            )
            TransmissionModeChip(
                label = "INTERNET DIRECT",
                subtitle = "New citizen reports go to server directly • relay listener stays available",
                selected = uiState.transmissionMode == TransmissionMode.FORCE_INTERNET,
                accent = AgapStatus.success,
                onClick = { onTransmissionModeSelected(TransmissionMode.FORCE_INTERNET) }
            )
        }

        if (uiState.bleMeshState.nearbyLastError != null) {
            Spacer(Modifier.height(AgapSpacing.sm))
            Text(
                text = "Nearby: ${uiState.bleMeshState.nearbyLastError}",
                color = AgapStatus.warning,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun RelayTransferPanel(uiState: HomeUiState, accent: Color) {
    val state = uiState.bleMeshState
    val activeNode = state.activePeerNodeId?.let(::relayNodeLabel)
    val transport = when (state.activeTransport) {
        "NEARBY" -> "Nearby P2P Cluster"
        "BLE_GATT" -> "Native BLE/GATT"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerShape = AgapShape.md,
                borderColor = MaterialTheme.colorScheme.surfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.background
            )
            .padding(AgapSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PACKET RELAY",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
            Text(
                text = transport ?: if (uiState.pendingPacketCount > 0) "WAITING" else "IDLE",
                color = if (state.isTransferring) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(AgapSpacing.sm))

        if (state.isTransferring) {
            Text(
                text = "${transferStageLabel(state.transferStage)}${activeNode?.let { " to $it" }.orEmpty()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(AgapSpacing.sm))
            LinearProgressIndicator(
                progress = { state.transferProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(AgapSpacing.xs))
            Text(
                text = "${(state.transferProgress.coerceIn(0f, 1f) * 100).toInt()}% • counted relayed only after receiver acceptance",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            Text(
                text = when {
                    uiState.pendingPacketCount > 0 && uiState.transmissionMode != TransmissionMode.FORCE_INTERNET ->
                        "${uiState.pendingPacketCount} packet(s) waiting for the next eligible relay node."
                    state.transferStage == "DELIVERED" && activeNode != null ->
                        "Last packet delivered to $activeNode."
                    else -> "No packet is currently crossing to another node."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun CommunicationMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        shape = AgapShape.md
    ) {
        Column(
            modifier = Modifier.padding(vertical = AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun TransmissionModeChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.md,
        color = if (selected) accent.copy(alpha = 0.09f)
        else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.md,
                vertical = AgapSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            )
            Spacer(Modifier.size(AgapSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

internal fun communicationModeTitle(mode: TransmissionMode): String = when (mode) {
    TransmissionMode.AUTO -> "Connection settings"
    TransmissionMode.FORCE_BLE_MESH -> "Offline relay"
    TransmissionMode.FORCE_INTERNET -> "Internet direct"
}

@Composable
internal fun communicationModeAccent(uiState: HomeUiState): Color = when {
    uiState.bleMeshState.isTransferring -> AgapStatus.info
    uiState.transmissionMode == TransmissionMode.FORCE_INTERNET -> AgapStatus.success
    uiState.transmissionMode == TransmissionMode.FORCE_BLE_MESH -> AgapStatus.info
    uiState.isOnline -> AgapStatus.success
    else -> AgapStatus.warning
}

internal fun communicationModeRuntimeSummary(uiState: HomeUiState): String {
    val state = uiState.bleMeshState
    if (state.isTransferring) {
        val transport = when (state.activeTransport) {
            "NEARBY" -> "Nearby P2P"
            "BLE_GATT" -> "BLE fallback"
            else -> "Relay"
        }
        val node = state.activePeerNodeId?.let(::relayNodeLabel)
        return "$transport • ${transferStageLabel(state.transferStage)}${node?.let { " • $it" }.orEmpty()}"
    }

    return when (uiState.transmissionMode) {
        TransmissionMode.AUTO -> when {
            uiState.isOnline && state.nearbyConnectedCount > 0 ->
                "Internet preferred • ${state.nearbyConnectedCount} relay node(s) connected"
            uiState.isOnline -> "Internet preferred • offline relay standing by"
            state.nearbyConnectedCount > 0 ->
                "Offline • Nearby relay active with ${state.nearbyConnectedCount} node(s)"
            else -> "Offline • scanning for Nearby/BLE relay nodes"
        }
        TransmissionMode.FORCE_BLE_MESH -> when {
            state.nearbyConnectedCount > 0 ->
                "Cloud disabled • ${state.nearbyConnectedCount} relay node(s) connected"
            else -> "Cloud disabled • scanning Nearby + BLE"
        }
        TransmissionMode.FORCE_INTERNET ->
            if (uiState.isOnline) "Server route active • relay listener on standby"
            else "No Internet • direct-send packets wait for connectivity"
    }
}

internal fun transferStageLabel(stage: String): String = when (stage) {
    "CONNECTING" -> "Connecting"
    "NEGOTIATING" -> "Negotiating link"
    "DISCOVERING" -> "Preparing service"
    "SENDING" -> "Sending packet"
    "AWAITING_ACK" -> "Awaiting receiver ACK"
    "DELIVERED" -> "Delivered"
    "FALLBACK" -> "Switching to fallback"
    "RETRYING" -> "Retrying another node"
    "FAILED" -> "Transfer failed"
    else -> "Ready"
}

internal fun relayNodeLabel(nodeId: String): String {
    val clean = nodeId.trim().uppercase(Locale.ROOT)
    val suffix = if (clean.length <= 8) clean else clean.takeLast(8)
    return "NODE-$suffix"
}

internal fun communicationsSummary(uiState: HomeUiState): String {
    if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) {
        return "Local preview • messages stay on this device"
    }
    val route = when (uiState.transmissionMode) {
        TransmissionMode.AUTO -> if (uiState.isOnline) {
            "Internet available • nearby relay on standby"
        } else {
            if (uiState.bleMeshState.isEnabled || uiState.bleMeshState.nearbyTransportActive)
                "Offline • looking for nearby relay devices"
            else "Offline • Bluetooth is unavailable"
        }

        TransmissionMode.FORCE_BLE_MESH -> "Using nearby devices for delivery"
        TransmissionMode.FORCE_INTERNET -> "Internet direct only"
    }

    return if (uiState.pendingPacketCount > 0) "$route • ${uiState.pendingPacketCount} waiting to send" else route
}
