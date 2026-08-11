package com.travelingsalesman.agap.ui.screens.responder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.IncidentCard
import com.travelingsalesman.agap.ui.components.IncidentCardVariant
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderMapScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponderMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incidentById = uiState.incidents.mapNotNull { incident ->
        (incident.serverIncidentId ?: incident.incidentId.toLongOrNull())?.let { id ->
            id to incident
        }
    }.toMap()
    val activeDispatches = uiState.assignedDispatches.filter {
        it.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
    }
    val newAssignmentCount = uiState.newAssignmentDispatchIds.count { dispatchId ->
        activeDispatches.any { it.id == dispatchId }
    }
    var pendingDispatchAction by remember { mutableStateOf<PendingDispatchAction?>(null) }

    val colorScheme = MaterialTheme.colorScheme
    val bgColor = colorScheme.background

    // The route must always follow a dispatch the responder explicitly selected,
    // never whichever active row happens to be first in a refreshed API result.
    val targetDispatch = uiState.selectedDispatchId?.let { selectedId ->
        activeDispatches.firstOrNull { it.id == selectedId }
    }
    val targetIncident = targetDispatch?.let { incidentById[it.incidentId] }

    LaunchedEffect(
        targetIncident?.incidentId,
        uiState.userLocation?.latitude,
        uiState.userLocation?.longitude,
        uiState.myUnit?.currentLat,
        uiState.myUnit?.currentLon,
        uiState.isOnline
    ) {
        viewModel.calculateRouteTo(targetIncident)
    }

    val activeRoute = uiState.activeRoute.takeIf {
        uiState.routingIncidentId == targetIncident?.incidentId
    }
    val navDistText = activeRoute?.let { formatResponderRouteDistance(it.distanceMeters) }
    val navEtaText = activeRoute?.let { formatResponderRouteEta(it.durationSeconds) }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "RESPONDER DASHBOARD",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState,
                onNotificationClick = onNavigateToNotifications,
                onMessageClick = onNavigateToMessages,
                showStatusDot = true
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Map
                OsmdroidMapView(
                    modifier = Modifier.fillMaxSize(),
                    userLocation = uiState.userLocation,
                    incidents = uiState.incidents,
                    responders = emptyList(),
                    targetIncident = targetIncident,
                    routePoints = activeRoute?.points.orEmpty(),
                    routeIsRoadRoute = activeRoute?.isRoadRoute ?: true,
                    autoFitRoute = activeRoute != null,
                    showIncidents = true,
                    showResponders = true,
                    zoomLevel = 15.0,
                    isDarkTheme = isSystemInDarkTheme(),
                    isOnline = uiState.isOnline
                )

                // Top Unit Control Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AgapSpacing.lg)
                        .align(Alignment.TopCenter)
                        .glassCard(
                            cornerShape = AgapShape.xl,
                            borderColor = colorScheme.outline.copy(alpha = 0.5f),
                            backgroundColor = colorScheme.surface.copy(alpha = 0.95f)
                        )
                ) {
                    Column(modifier = Modifier.padding(AgapSpacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.myUnit?.unitCallsign ?: "UNIT OFFLINE",
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = when {
                                        targetDispatch == null && activeDispatches.isNotEmpty() ->
                                            "${activeDispatches.size} ACTIVE - SELECT AN ASSIGNMENT BELOW"

                                        targetDispatch != null && targetIncident == null ->
                                            "ASSIGNMENT #${targetDispatch.incidentId} - LOADING INCIDENT DETAILS"

                                        targetIncident != null && uiState.isCalculatingRoute ->
                                            "ROUTING • CALCULATING SHORTEST ROAD PATH"

                                        targetIncident != null && activeRoute?.isRoadRoute == true ->
                                            "NAVIGATING • SHORTEST ROAD • $navDistText • $navEtaText"

                                        targetIncident != null && activeRoute != null ->
                                            "DIRECT FALLBACK • $navDistText • ROAD DATA NOT CACHED"

                                        targetIncident != null ->
                                            "ASSIGNED • WAITING FOR GPS / ROUTE DATA"

                                        else ->
                                            "OPERATIONAL • ${activeDispatches.size} ASSIGNMENTS"
                                    },
                                    color = if (targetDispatch?.status?.equals("EN_ROUTE", ignoreCase = true) == true) AmberWarning else colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            StatusBadge(status = uiState.responderStatus)
                        }

                        Spacer(modifier = Modifier.height(AgapSpacing.lg))

                        AnimatedVisibility(visible = newAssignmentCount > 0) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AgapShape.md,
                                color = AmberWarning.copy(alpha = 0.13f),
                                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(AgapSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AssignmentLate,
                                        contentDescription = null,
                                        tint = AmberWarning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (newAssignmentCount == 1) {
                                            "NEW ASSIGNMENT READY FOR REVIEW"
                                        } else {
                                            "$newAssignmentCount NEW ASSIGNMENTS READY FOR REVIEW"
                                        },
                                        modifier = Modifier.weight(1f),
                                        color = colorScheme.onSurface,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    TextButton(
                                        onClick = viewModel::reviewNextNewAssignment,
                                        contentPadding = PaddingValues(horizontal = AgapSpacing.sm)
                                    ) {
                                        Text(
                                            text = "REVIEW",
                                            color = AmberWarning,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = targetDispatch == null && activeDispatches.isNotEmpty()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AgapShape.md,
                                color = colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(AgapSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.TouchApp,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Choose an assignment from the queue before starting a route or changing its status.",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Unit availability and dispatch progress are separate domains.
                        // IDLE/OFFLINE updates the rescue unit itself; EN_ROUTE/ON_SCENE
                        // must update the active dispatch, otherwise the backend correctly
                        // rejects the request as an invalid responder-unit status.
                        if (targetDispatch != null) {
                            val dispatchStatus = targetDispatch.status.uppercase()
                            val nextDispatchStatus = when (dispatchStatus) {
                                "ASSIGNED" -> "ACCEPTED"
                                "ACCEPTED" -> "EN_ROUTE"
                                "EN_ROUTE" -> "ON_SCENE"
                                "ON_SCENE" -> "COMPLETED"
                                else -> null
                            }
                            val nextLabel = when (nextDispatchStatus) {
                                "ACCEPTED" -> "ACCEPT DISPATCH"
                                "EN_ROUTE" -> "START EN ROUTE"
                                "ON_SCENE" -> "MARK ON SCENE"
                                "COMPLETED" -> "COMPLETE RESPONSE"
                                else -> "DISPATCH ACTIVE"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = AgapShape.md,
                                    color = AmberWarning.copy(alpha = 0.13f),
                                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        Modifier.padding(
                                            horizontal = AgapSpacing.md,
                                            vertical = AgapSpacing.sm
                                        )
                                    ) {
                                        Text(
                                            text = "SELECTED ASSIGNMENT",
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "#${targetDispatch.incidentId} - ${dispatchStatus.replace('_', ' ')}",
                                            color = AmberWarning,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        nextDispatchStatus?.let { next ->
                                            if (next == "COMPLETED") {
                                                pendingDispatchAction = PendingDispatchAction(targetDispatch, next)
                                            } else {
                                                viewModel.updateDispatchStatus(targetDispatch, next)
                                            }
                                        }
                                    },
                                    enabled = nextDispatchStatus != null && !uiState.isLoadingDispatches,
                                    modifier = Modifier.weight(1.25f),
                                    shape = AgapShape.md,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RedPrimary,
                                        contentColor = colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = nextLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        pendingDispatchAction = PendingDispatchAction(targetDispatch, "CANCELLED")
                                    },
                                    enabled = !uiState.isLoadingDispatches,
                                    shape = AgapShape.md,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = RedPrimary
                                    ),
                                    border = BorderStroke(1.dp, RedPrimary)
                                ) {
                                    Text(
                                        text = "CANCEL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }
                            }

                            TextButton(
                                onClick = viewModel::clearSelectedDispatch,
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = AgapSpacing.xs)
                            ) {
                                Text(
                                    text = "CHANGE ASSIGNMENT",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                            ) {
                                listOf("IDLE", "OFFLINE").forEach { st ->
                                    val isSelected = uiState.responderStatus == st
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.updateMyStatus(st) },
                                        label = { Text(st, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f),
                                        shape = AgapShape.md,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (st == "IDLE") GreenOnline else RedPrimary,
                                            selectedLabelColor = colorScheme.onPrimary,
                                            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            labelColor = colorScheme.onSurfaceVariant
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = colorScheme.outline.copy(alpha = 0.5f),
                                            selectedBorderColor = if (st == "IDLE") GreenOnline else RedPrimary
                                        )
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = targetIncident != null) {
                            Column {
                                Spacer(modifier = Modifier.height(AgapSpacing.md))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AgapShape.md,
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (activeRoute?.isRoadRoute == false) {
                                            RedPrimary.copy(alpha = 0.5f)
                                        } else {
                                            AmberWarning.copy(alpha = 0.45f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = AgapSpacing.md,
                                            vertical = AgapSpacing.sm
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (uiState.isCalculatingRoute) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = AmberWarning
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (activeRoute?.isRoadRoute == false) {
                                                    Icons.Outlined.WarningAmber
                                                } else {
                                                    Icons.Outlined.NearMe
                                                },
                                                contentDescription = null,
                                                tint = if (activeRoute?.isRoadRoute == false) RedPrimary else AmberWarning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(AgapSpacing.sm))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when {
                                                    uiState.isCalculatingRoute -> "CALCULATING SHORTEST PATH"
                                                    activeRoute?.isRoadRoute == true -> "OFFLINE-CAPABLE SHORTEST ROAD ROUTE"
                                                    activeRoute != null -> "DIRECT FALLBACK — NOT A ROAD ROUTE"
                                                    else -> "ROUTE WAITING FOR LOCATION"
                                                },
                                                color = colorScheme.onSurface,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = when {
                                                    activeRoute?.isRoadRoute == true ->
                                                        "$navDistText • $navEtaText${activeRoute.message?.let { " • $it" } ?: ""}"
                                                    activeRoute != null ->
                                                        "${activeRoute.message ?: "Road graph unavailable"} • $navDistText"
                                                    else ->
                                                        "The route will appear when responder GPS and incident coordinates are available."
                                                },
                                                color = colorScheme.onSurfaceVariant,
                                                fontSize = 8.5.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Dispatch Queue
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = AgapSpacing.lg)
                ) {
                    AnimatedVisibility(visible = activeDispatches.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = AgapSpacing.lg)) {
                            Text(
                                text = "ACTIVE DISPATCHES",
                                style = MaterialTheme.typography.labelLarge,
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(
                                    start = AgapSpacing.xs,
                                    bottom = AgapSpacing.sm
                                )
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                            ) {
                                items(activeDispatches, key = { it.id }) { dispatch ->
                                    val incident = incidentById[dispatch.incidentId]
                                    DispatchQueueItem(
                                        dispatch = dispatch,
                                        incident = incident,
                                        isSelected = dispatch.id == uiState.selectedDispatchId,
                                        isNew = dispatch.id in uiState.newAssignmentDispatchIds,
                                        onSelect = { viewModel.selectDispatch(dispatch.id) },
                                        onNavigateToDetail = incident?.let { resolvedIncident ->
                                            { onNavigateToDetail(resolvedIncident.incidentId) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.feedbackMessage?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearFeedbackMessage) {
                        Text("DISMISS", color = colorScheme.inverseOnSurface)
                    }
                },
                containerColor = colorScheme.inverseSurface,
                contentColor = colorScheme.inverseOnSurface
            ) {
                Text(msg)
            }
        }
    }

    pendingDispatchAction?.let { action ->
        val isCancellation = action.newStatus == "CANCELLED"
        AlertDialog(
            onDismissRequest = { pendingDispatchAction = null },
            title = {
                Text(if (isCancellation) "CANCEL ASSIGNMENT?" else "COMPLETE RESPONSE?")
            },
            text = {
                Text(
                    if (isCancellation) {
                        "This tells Command that your unit will not respond to Incident #${action.dispatch.incidentId}. Confirm only after coordinating the handoff."
                    } else {
                        "This marks your unit's response complete for Incident #${action.dispatch.incidentId}. Command can still manage the overall incident."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDispatchStatus(action.dispatch, action.newStatus)
                        pendingDispatchAction = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCancellation) RedPrimary else GreenOnline
                    )
                ) {
                    Text("CONFIRM")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDispatchAction = null }) {
                    Text("GO BACK")
                }
            }
        )
    }
}

private data class PendingDispatchAction(
    val dispatch: DispatchDto,
    val newStatus: String
)

@Composable
private fun DispatchQueueItem(
    dispatch: DispatchDto,
    incident: IncidentEntity?,
    isSelected: Boolean,
    isNew: Boolean,
    onSelect: () -> Unit,
    onNavigateToDetail: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    val emphasisColor = when {
        isSelected -> RedPrimary
        isNew -> AmberWarning
        else -> colorScheme.outline
    }
    val selectionLabel = when {
        isSelected -> "ROUTE SELECTED"
        isNew -> "NEW ASSIGNMENT"
        else -> "ASSIGNMENT"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            Surface(
                color = emphasisColor.copy(alpha = 0.14f),
                shape = AgapShape.xs,
                border = BorderStroke(1.dp, emphasisColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = selectionLabel,
                    modifier = Modifier.padding(
                        horizontal = AgapSpacing.sm,
                        vertical = AgapSpacing.xs
                    ),
                    color = emphasisColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = dispatch.unitCallsign ?: "UNIT #${dispatch.unitId}",
                modifier = Modifier.weight(1f),
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusBadge(status = dispatch.status)
        }

        if (incident != null) {
            // Tapping the shared incident card selects it for routing. Details
            // remain an explicit secondary action so selection never navigates
            // away from the responder's operational map.
            IncidentCard(
                incident = incident,
                onClick = onSelect,
                modifier = Modifier.border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = emphasisColor.copy(alpha = if (isSelected) 0.9f else 0.45f),
                    shape = AgapShape.xl
                ),
                variant = IncidentCardVariant.Detailed
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, emphasisColor.copy(alpha = 0.45f), AgapShape.xl),
                shape = AgapShape.xl,
                color = colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier.padding(AgapSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = emphasisColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INCIDENT #${dispatch.incidentId}",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Assignment received. Loading the incident location and response details.",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = emphasisColor
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSelect,
                modifier = Modifier.weight(1f),
                shape = AgapShape.md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) RedPrimary else colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Text(
                    text = when {
                        isSelected -> "ROUTE SELECTED"
                        isNew -> "REVIEW ASSIGNMENT"
                        else -> "SELECT FOR ROUTE"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            if (onNavigateToDetail != null) {
                TextButton(onClick = onNavigateToDetail) {
                    Text(
                        text = "DETAILS",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


private fun formatResponderRouteDistance(distanceMeters: Double): String =
    if (distanceMeters < 1000.0) {
        "${distanceMeters.toInt()} m"
    } else {
        "%.1f km".format(distanceMeters / 1000.0)
    }

private fun formatResponderRouteEta(durationSeconds: Long): String {
    val minutes = (durationSeconds / 60L).coerceAtLeast(1L)
    return if (minutes < 60L) {
        "~$minutes min ETA"
    } else {
        "~${minutes / 60L}h ${minutes % 60L}m ETA"
    }
}
