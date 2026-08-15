package com.travelingsalesman.agap.ui.screens.responder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
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
                    title = "RESPONDER COMMAND",
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
                        ResponderHeader(
                            unitCallsign = uiState.myUnit?.unitCallsign,
                            isOnline = uiState.isOnline,
                            agencyName = uiState.myUnit?.agencyId?.let { "Agency #$it" } ?: "AGAP RESPONDER"
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                        ) {
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                label = "ASSIGNED",
                                value = activeDispatches.size.toString(),
                                icon = Icons.AutoMirrored.Outlined.Assignment,
                                accent = RedPrimary
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                label = "DISTANCE",
                                value = navDistText ?: "—",
                                icon = Icons.Outlined.NearMe,
                                accent = AmberWarning
                            )
                            MetricCard(
                                modifier = Modifier.weight(1f),
                                label = "EST. ARRIVAL",
                                value = navEtaText ?: "—",
                                icon = Icons.Outlined.AccessTime,
                                accent = GreenOnline
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
                            eyebrow = "TASK QUEUE",
                            title = "Active Assignments",
                            subtitle = "Incidents assigned to your unit by Command",
                            count = activeDispatches.size,
                            accent = RedPrimary
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
                            onSelect = { viewModel.selectDispatch(dispatch.id) },
                            onUpdateStatus = { status -> viewModel.updateDispatchStatus(dispatch, status) }
                        )
                    }
                    }

                    item {
                        SectionTitle(
                            eyebrow = "FIELD AWARENESS",
                            title = "Nearby Activity",
                            subtitle = "Emergency alerts detected in your operational area",
                            count = nearbyIncidents.size,
                            accent = BlueMedical
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
private fun ResponderHeader(
    unitCallsign: String?,
    isOnline: Boolean,
    agencyName: String
) {
    val now = remember { Calendar.getInstance() }
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val timeFormatted = remember { SimpleDateFormat("h:mm a • EEE, MMM d", Locale.getDefault()).format(now.time) }
    
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(BlueMedical.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, ${unitCallsign ?: "Unit"}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = agencyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = AgapShape.pill,
                    color = BlueMedical.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BlueMedical.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(BlueMedical))
                        Text("RESPONDER", color = BlueMedical, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    val accent = if (isOnline) GreenOnline else AmberWarning
                    Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(92.dp)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AgapSpacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(11.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
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
            text = "TACTICAL NAVIGATION",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.8.sp
        )

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
                isDarkTheme = isSystemInDarkTheme(),
                isOnline = uiState.isOnline
            )

            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(AgapSpacing.sm),
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
                    Text("EXPAND", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
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
        Text(eyebrow, color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
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
    val accent = if (isSelected) RedPrimary else if (isNew) AmberWarning else MaterialTheme.colorScheme.outline
    
    Card(
        modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = accent.copy(alpha = 0.4f), shape = AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = dispatch.status)
                    if (isNew) {
                        Spacer(Modifier.width(AgapSpacing.sm))
                        Surface(color = AmberWarning.copy(alpha = 0.2f), shape = AgapShape.xs) {
                            Text("NEW", color = AmberWarning, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = AgapSpacing.xs))
                        }
                    }
                }
                Text(text = "#${dispatch.incidentId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(AgapSpacing.sm))

            if (incident != null) {
                Text(
                    text = EmergencyType.fromCode(incident.emergencyType).label.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text("Loading Incident Details...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(AgapSpacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                val nextStatus = when (dispatch.status.uppercase()) {
                    "ASSIGNED" -> "ACCEPTED"
                    "ACCEPTED" -> "EN_ROUTE"
                    "EN_ROUTE" -> "ON_SCENE"
                    "ON_SCENE" -> "COMPLETED"
                    else -> null
                }

                if (nextStatus != null) {
                    Button(
                        onClick = { onUpdateStatus(nextStatus) },
                        modifier = Modifier.weight(1f),
                        shape = AgapShape.md,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) RedPrimary else MaterialTheme.colorScheme.primary)
                    ) {
                        Text(nextStatus.replace('_', ' '), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.weight(1f),
                    shape = AgapShape.md,
                    border = BorderStroke(1.dp, if (isSelected) RedPrimary else MaterialTheme.colorScheme.outline)
                ) {
                    Text(if (isSelected) "ROUTING ACTIVE" else "START ROUTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) RedPrimary else MaterialTheme.colorScheme.onSurface)
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
                title = "TACTICAL MAP",
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
            isDarkTheme = isSystemInDarkTheme(),
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
