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
    val activeDispatches = uiState.assignedDispatches.filter { it.status !in listOf("COMPLETED", "CANCELLED") }

    val bgColor = MaterialTheme.colorScheme.background
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val enRouteDispatch = activeDispatches.firstOrNull { it.status == "EN_ROUTE" }
    val targetIncident = enRouteDispatch?.let { incidentById[it.incidentId] }
        ?: uiState.incidents.firstOrNull { it.status == "EN_ROUTE" }
        ?: activeDispatches.firstOrNull()?.let { incidentById[it.incidentId] }
        ?: uiState.incidents.firstOrNull { it.status != "RESOLVED" }

    val (navDistText, navEtaText) = if (targetIncident != null) {
        val curLat = uiState.userLocation?.latitude ?: uiState.myUnit?.currentLat?.takeIf { it != 0.0 } ?: 13.1391
        val curLon = uiState.userLocation?.longitude ?: uiState.myUnit?.currentLon?.takeIf { it != 0.0 } ?: 123.7438
        val results = FloatArray(1)
        android.location.Location.distanceBetween(curLat, curLon, targetIncident.latitude, targetIncident.longitude, results)
        val meters = results[0]
        val km = meters / 1000.0
        val distStr = if (km < 1.0) "%d m".format(meters.toInt()) else "%.1f km".format(km)
        val speedMps = 11.11
        val mins = Math.max(1, (meters / speedMps).toInt() / 60)
        val etaStr = if (mins < 60) "~$mins min ETA" else "~${mins / 60}h ${mins % 60}m ETA"
        Pair(distStr, etaStr)
    } else {
        Pair(null, null)
    }

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
                    showIncidents = true,
                    showResponders = true,
                    zoomLevel = 15.0,
                    isDarkTheme = isSystemInDarkTheme()
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
                                    text = if (targetIncident != null && (uiState.responderStatus == "EN_ROUTE" || enRouteDispatch != null)) {
                                        "NAVIGATING • $navDistText ($navEtaText)"
                                    } else {
                                        "OPERATIONAL • ${activeDispatches.size} ASSIGNMENTS"
                                    },
                                    color = if (uiState.responderStatus == "EN_ROUTE") AmberWarning else Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            StatusBadge(status = uiState.responderStatus)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Premium Status Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statuses = listOf("IDLE", "EN_ROUTE", "ON_SCENE")
                            statuses.forEach { st ->
                                val isSelected = uiState.responderStatus == st
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateMyStatus(st) },
                                    label = { Text(st, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f),
                                    shape = AgapShape.md,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RedPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        labelColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.White.copy(alpha = 0.1f),
                                        selectedBorderColor = RedPrimary
                                    )
                                )
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
