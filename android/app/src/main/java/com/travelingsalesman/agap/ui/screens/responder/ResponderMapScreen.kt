package com.travelingsalesman.agap.ui.screens.responder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ResponderMapScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponderMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incidentById = uiState.incidents.associateBy { it.incidentId.toLongOrNull() }
    val activeDispatches = uiState.assignedDispatches.filter { it.status !in listOf("COMPLETED", "CANCELLED") }
    val activeIncidents = activeDispatches

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
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
        val speedMps = 11.11 // ~40 km/h avg speed
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
                bleMeshState = uiState.bleMeshState
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = RedPrimary
                )
            }
            // Full-screen OSMDroid map showing active accident & emergency incident locations with route line & camera target
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

            // Top Status Bar Overlay with Full Status Workflow Chips
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(16.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = uiState.myUnit?.unitCallsign?.let { "UNIT CALLSIGN: $it" } ?: "UNIT CALLSIGN: RES-UNIT-${uiState.userId ?: 0}",
                                color = textColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (targetIncident != null && (uiState.responderStatus == "EN_ROUTE" || enRouteDispatch != null)) {
                                    "NAVIGATING • $navDistText ($navEtaText)"
                                } else {
                                    "Current Status: ${uiState.responderStatus} • ${activeIncidents.size} Active SOS Calls"
                                },
                                color = if (uiState.responderStatus == "EN_ROUTE") AmberWarning else subTextColor,
                                fontWeight = if (uiState.responderStatus == "EN_ROUTE") FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                        StatusBadge(status = uiState.responderStatus)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "UPDATE UNIT RESPONDER STATUS",
                        color = subTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val statuses = listOf("IDLE", "EN_ROUTE", "ON_SCENE", "RESOLVED")
                        statuses.forEach { st ->
                            val isSelected = uiState.responderStatus == st
                            Button(
                                onClick = { viewModel.updateMyStatus(st) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) RedPrimary else borderCol
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = st,
                                    color = if (isSelected) Color.White else textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            ResponderAssignmentQueue(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                dispatches = activeDispatches,
                incidentById = incidentById,
                currentLat = uiState.userLocation?.latitude
                    ?: uiState.myUnit?.currentLat?.takeIf { it != 0.0 }
                    ?: 13.1391,
                currentLon = uiState.userLocation?.longitude
                    ?: uiState.myUnit?.currentLon?.takeIf { it != 0.0 }
                    ?: 123.7438,
                isLoading = uiState.isLoadingDispatches,
                cardBg = cardBg,
                borderCol = borderCol,
                textColor = textColor,
                subTextColor = subTextColor,
                onNavigateToDetail = onNavigateToDetail,
                onDispatchStatus = viewModel::updateDispatchStatus,
                onBleAcknowledgment = viewModel::sendBleAcknowledgment
            )
        }

        uiState.feedbackMessage?.let { msg ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearFeedbackMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                containerColor = cardBg,
                contentColor = textColor
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun ResponderAssignmentQueue(
    modifier: Modifier,
    dispatches: List<DispatchDto>,
    incidentById: Map<Long?, IncidentEntity>,
    currentLat: Double,
    currentLon: Double,
    isLoading: Boolean,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onNavigateToDetail: (String) -> Unit,
    onDispatchStatus: (DispatchDto, String) -> Unit,
    onBleAcknowledgment: (DispatchDto, IncidentEntity?) -> Unit
) {
    Card(
        modifier = modifier
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
                    text = "ASSIGNED DISPATCH QUEUE",
                    color = RedPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isLoading) "SYNCING" else "${dispatches.size} ACTIVE",
                    color = subTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (dispatches.isEmpty()) {
                Text(
                    text = "No active dispatch assignments",
                    color = subTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                return@Column
            }

            dispatches.take(3).forEach { dispatch ->
                val incident = incidentById[dispatch.incidentId]
                AssignmentRow(
                    dispatch = dispatch,
                    incident = incident,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    isLoading = isLoading,
                    borderCol = borderCol,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    onNavigateToDetail = onNavigateToDetail,
                    onDispatchStatus = onDispatchStatus,
                    onBleAcknowledgment = onBleAcknowledgment
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AssignmentRow(
    dispatch: DispatchDto,
    incident: IncidentEntity?,
    currentLat: Double,
    currentLon: Double,
    isLoading: Boolean,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onNavigateToDetail: (String) -> Unit,
    onDispatchStatus: (DispatchDto, String) -> Unit,
    onBleAcknowledgment: (DispatchDto, IncidentEntity?) -> Unit
) {
    val (distanceText, etaText) = if (incident != null) {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(currentLat, currentLon, incident.latitude, incident.longitude, results)
        val meters = results[0]
        val km = meters / 1000.0
        val distStr = if (km < 1.0) "%d m".format(meters.toInt()) else "%.1f km".format(km)
        val speedMps = 11.11
        val mins = Math.max(1, (meters / speedMps).toInt() / 60)
        val etaStr = if (mins < 60) "~$mins min ETA" else "~${mins / 60}h ${mins % 60}m ETA"
        Pair(distStr, etaStr)
    } else {
        Pair("Location pending", "--")
    }
    val nextStatus = when (dispatch.status) {
        "ASSIGNED" -> "ACCEPTED"
        "ACCEPTED" -> "EN_ROUTE"
        "EN_ROUTE" -> "ON_SCENE"
        "ON_SCENE" -> "COMPLETED"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(borderCol.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident?.emergencyType ?: "Incident #${dispatch.incidentId}",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${dispatch.unitCallsign ?: "Unit #${dispatch.unitId}"} • $distanceText ($etaText)",
                    color = subTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StatusBadge(status = dispatch.status)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onNavigateToDetail(dispatch.incidentId.toString()) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("DETAILS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }

            nextStatus?.let { status ->
                Button(
                    onClick = { onDispatchStatus(dispatch, status) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status == "EN_ROUTE") AmberWarning else GreenOnline
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(status, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }

            Button(
                onClick = { onBleAcknowledgment(dispatch, incident) },
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = borderCol),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("BLE ACK", color = textColor, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
    }
}


