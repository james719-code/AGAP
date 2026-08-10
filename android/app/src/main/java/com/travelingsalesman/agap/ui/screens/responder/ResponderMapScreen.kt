package com.travelingsalesman.agap.ui.screens.responder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.PremiumIncidentCard
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderMapScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponderMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incidentById = uiState.incidents.associateBy { it.incidentId.toLongOrNull() }
    val activeDispatches = uiState.assignedDispatches.filter {
        it.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
    }

    val bgColor = MaterialTheme.colorScheme.background

    val enRouteDispatch = activeDispatches.firstOrNull { it.status.equals("EN_ROUTE", ignoreCase = true) }
    val targetDispatch = enRouteDispatch ?: activeDispatches.firstOrNull()
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
                bleMeshState = uiState.bleMeshState,
                showStatusDot = true,
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                }
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    shape = AgapShape.xl,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E).copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.myUnit?.unitCallsign ?: "UNIT OFFLINE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = when {
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
                                    color = if (targetDispatch?.status?.equals("EN_ROUTE", ignoreCase = true) == true) AmberWarning else Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            StatusBadge(status = uiState.responderStatus)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = AgapShape.md,
                                    color = AmberWarning.copy(alpha = 0.13f),
                                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f))
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "DISPATCH STATUS",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dispatchStatus.replace('_', ' '),
                                            color = AmberWarning,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        nextDispatchStatus?.let { next ->
                                            viewModel.updateDispatchStatus(targetDispatch, next)
                                        }
                                    },
                                    enabled = nextDispatchStatus != null && !uiState.isLoadingDispatches,
                                    modifier = Modifier.weight(1.25f),
                                    shape = AgapShape.md,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RedPrimary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = nextLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            labelColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = Color.White.copy(alpha = 0.1f),
                                            selectedBorderColor = if (st == "IDLE") GreenOnline else RedPrimary
                                        )
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = targetIncident != null) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AgapShape.md,
                                    color = Color.White.copy(alpha = 0.05f),
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
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
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
                                                    Icons.Default.WarningAmber
                                                } else {
                                                    Icons.Default.NearMe
                                                },
                                                contentDescription = null,
                                                tint = if (activeRoute?.isRoadRoute == false) RedPrimary else AmberWarning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(9.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when {
                                                    uiState.isCalculatingRoute -> "CALCULATING SHORTEST PATH"
                                                    activeRoute?.isRoadRoute == true -> "OFFLINE-CAPABLE SHORTEST ROAD ROUTE"
                                                    activeRoute != null -> "DIRECT FALLBACK — NOT A ROAD ROUTE"
                                                    else -> "ROUTE WAITING FOR LOCATION"
                                                },
                                                color = Color.White,
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
                                                color = Color.White.copy(alpha = 0.65f),
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
                        .padding(bottom = 16.dp)
                ) {
                    AnimatedVisibility(visible = activeDispatches.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "ACTIVE DISPATCHES",
                                style = MaterialTheme.typography.labelLarge,
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeDispatches, key = { it.id }) { dispatch ->
                                    val incident = incidentById[dispatch.incidentId]
                                    if (incident != null) {
                                        PremiumIncidentCard(
                                            incident = incident,
                                            onClick = { onNavigateToDetail(incident.incidentId) }
                                        )
                                    }
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
