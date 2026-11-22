@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.responder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.travelingsalesman.agap.ui.components.OperationalWorkspaceHeader
import com.travelingsalesman.agap.ui.components.OperationalMetric
import com.travelingsalesman.agap.ui.components.OperationalMetrics
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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
    val snackbarHostState = remember { SnackbarHostState() }

    val activeDispatches = remember(uiState.assignedDispatches) {
        uiState.assignedDispatches.filter {
            it.status.uppercase() !in setOf("COMPLETED", "CANCELLED")
        }
    }

    val incidentById = uiState.incidents.mapNotNull { incident ->
        (incident.serverIncidentId ?: incident.incidentId.toLongOrNull())?.let { id ->
            id to incident
        }
    }.toMap()

    val targetDispatch = uiState.selectedDispatchId?.let { selectedId ->
        activeDispatches.firstOrNull { it.id == selectedId }
    }
    val targetIncident = targetDispatch?.let { incidentById[it.incidentId] }

    val nearbyIncidents = remember(uiState.incidents, activeDispatches) {
        val assignedIds = activeDispatches.map { it.incidentId }.toSet()
        uiState.incidents.filter { 
            (it.serverIncidentId ?: it.incidentId.toLongOrNull()) !in assignedIds &&
            it.status.uppercase() !in setOf("RESOLVED", "CANCELLED")
        }
    }

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

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedbackMessage()
        }
    }

    var showFullMap by remember { mutableStateOf(false) }
    BackHandler(enabled = showFullMap) { showFullMap = false }

    if (showFullMap) {
        ResponderFullMapOverlay(
            uiState = uiState,
            targetIncident = targetIncident,
            activeRoute = activeRoute,
            onDismiss = { showFullMap = false }
        )
    } else {
        Scaffold(
            topBar = {
                AgapTopBar(
                    title = "Responder workspace",
                    onProfileClick = onNavigateToProfile,
                    notificationCount = uiState.unreadNotificationCount,
                    pendingPacketCount = uiState.pendingPacketCount,
                    bleMeshState = uiState.bleMeshState,
                    onNotificationClick = onNavigateToNotifications,
                    onMessageClick = onNavigateToMessages,
                    showStatusDot = true
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AgapSpacing.lg,
                        end = AgapSpacing.lg,
                        top = AgapSpacing.md,
                        bottom = AgapSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
                ) {
                    item {
                        OperationalWorkspaceHeader(
                            title = uiState.myUnit?.unitCallsign ?: "Responder unit",
                            subtitle = uiState.myUnit?.agencyId?.let { "Agency #$it" } ?: "Awaiting unit information",
                            role = "Field responder",
                            icon = Icons.Outlined.Shield,
                            isOnline = uiState.isOnline,
                            mapAction = "Open field map",
                            onOpenMap = { showFullMap = true }
                        )
                    }

                    item {
                        OperationalMetrics(listOf(
                            OperationalMetric("Assigned", activeDispatches.size.toString(), Icons.AutoMirrored.Outlined.Assignment, MaterialTheme.colorScheme.primary),
                            OperationalMetric("Distance", navDistText ?: "—", Icons.Outlined.NearMe, AgapStatus.info),
                            OperationalMetric("ETA", navEtaText ?: "—", Icons.Outlined.AccessTime, AgapStatus.success)
                        ))
                    }

                    item {
                        SectionTitle(
                            eyebrow = "Task queue",
                            title = "Active assignments",
                            subtitle = "Incidents assigned to your unit by Command",
                            count = activeDispatches.size,
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (activeDispatches.isEmpty()) {
                        item {
                            EmptyCard(
                                title = "No active tasks",
                                message = "Command has not assigned any incidents to your unit yet."
                            )
                        }
                    } else {
                        items(activeDispatches, key = { it.id }) { dispatch ->
                        val incident = incidentById[dispatch.incidentId]
                        DispatchDashboardCard(
                            dispatch = dispatch,
                            incident = incident,
                            isSelected = dispatch.id == uiState.selectedDispatchId,
                            isNew = dispatch.id in uiState.newAssignmentDispatchIds,
                            onSelect = {
                                viewModel.selectDispatch(dispatch.id)
                                showFullMap = true
                            },
                            onUpdateStatus = { status -> viewModel.updateDispatchStatus(dispatch, status) }
                        )
                    }
                    }

                    item {
                        ResponderMapSection(
                            uiState = uiState,
                            targetIncident = targetIncident,
                            activeRoute = activeRoute,
                            onExpand = { showFullMap = true }
                        )
                    }

                    item {
                        SectionTitle(
                            eyebrow = "Field awareness",
                            title = "Nearby activity",
                            subtitle = "Emergency alerts detected in your operational area",
                            count = nearbyIncidents.size,
                            accent = AgapStatus.info
                        )
                    }

                    if (nearbyIncidents.isEmpty()) {
                        item {
                            EmptyCard(
                                title = "Area is clear",
                                message = "No other incidents detected nearby at this time."
                            )
                        }
                    } else {
                        items(nearbyIncidents, key = { it.incidentId }) { incident ->
                            IncidentCard(
                                incident = incident,
                                onClick = { onNavigateToDetail(incident.incidentId) },
                                variant = IncidentCardVariant.Compact
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponderMapSection(
    uiState: ResponderMapUiState,
    targetIncident: IncidentEntity?,
    activeRoute: com.travelingsalesman.agap.core.routing.OfflineRouteResult?,
    onExpand: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Text(
            text = "Field map",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Text(if (uiState.isOnline) "Cached map · online connection" else "Cached map · offline data",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(AgapShape.xl)
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant, shape = AgapShape.xl)
        ) {
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
                isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                isOnline = uiState.isOnline
            )

            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(AgapSpacing.sm).heightIn(min = 48.dp),
                shape = AgapShape.pill,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                onClick = onExpand
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Icon(Icons.Outlined.Fullscreen, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Text("Expand map", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    eyebrow: String,
    title: String,
    subtitle: String,
    count: Int,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        Text(eyebrow, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            Surface(
                shape = AgapShape.pill,
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
            ) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DispatchDashboardCard(
    dispatch: DispatchDto,
    incident: IncidentEntity?,
    isSelected: Boolean,
    isNew: Boolean,
    onSelect: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (isSelected) colors.primary else if (isNew) AgapStatus.warning else colors.outlineVariant
    
    Card(
        modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = accent.copy(alpha = 0.4f), shape = AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                    StatusBadge(status = dispatch.status)
                    if (isNew) {
                        Surface(color = AgapStatus.warning.copy(alpha = 0.12f), shape = AgapShape.xs) {
                            Text("New assignment", color = AgapStatus.warning, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs))
                        }
                    }
                Text(text = "#${dispatch.incidentId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(AgapSpacing.sm))

            if (incident != null) {
                val type = EmergencyType.fromCode(incident.emergencyType)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                    Box(Modifier.size(48.dp).background(type.color, AgapShape.md), contentAlignment = Alignment.Center) {
                        Icon(type.icon, null, tint = contentColorForAccent(type.color))
                    }
                    Text(type.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                }
            } else {
                Text("Loading incident details…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(AgapSpacing.md))

            val nextStatus = when (dispatch.status.uppercase()) {
                "ASSIGNED" -> "ACCEPTED" to "Accept task"
                "ACCEPTED" -> "EN_ROUTE" to "Mark en route"
                "EN_ROUTE" -> "ON_SCENE" to "Mark on scene"
                "ON_SCENE" -> "COMPLETED" to "Complete task"
                else -> null
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
                val statusAction: @Composable (Modifier) -> Unit = { actionModifier ->
                    if (nextStatus != null) {
                    Button(
                        onClick = { onUpdateStatus(nextStatus.first) },
                        modifier = actionModifier.heightIn(min = 48.dp),
                        shape = AgapShape.md,
                    ) {
                        Text(nextStatus.second, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                    }
                }
                val routeAction: @Composable (Modifier) -> Unit = { actionModifier ->
                    OutlinedButton(
                        onClick = onSelect,
                        modifier = actionModifier.heightIn(min = 48.dp),
                        shape = AgapShape.md,
                        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline)
                    ) {
                        Text(if (isSelected) "Route active" else "Start route", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                        statusAction(Modifier.fillMaxWidth())
                        routeAction(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                        statusAction(Modifier.weight(1f))
                        routeAction(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(AgapSpacing.sm))
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResponderFullMapOverlay(
    uiState: ResponderMapUiState,
    targetIncident: IncidentEntity?,
    activeRoute: com.travelingsalesman.agap.core.routing.OfflineRouteResult?,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            AgapTopBar(
                title = "Field map",
                showBackButton = true,
                onBackClick = onDismiss
            )
        }
    ) { padding ->
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize().padding(padding),
            userLocation = uiState.userLocation,
            incidents = uiState.incidents,
            responders = emptyList(),
            targetIncident = targetIncident,
            routePoints = activeRoute?.points.orEmpty(),
            routeIsRoadRoute = activeRoute?.isRoadRoute ?: true,
            autoFitRoute = activeRoute != null,
            showIncidents = true,
            showResponders = true,
            zoomLevel = 17.0,
            isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
            isOnline = uiState.isOnline
        )
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
