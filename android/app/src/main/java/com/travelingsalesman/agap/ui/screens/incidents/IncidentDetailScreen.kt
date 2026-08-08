package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.data.remote.dto.DispatchDto
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incident = uiState.incident

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
            val content = @Composable {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        // Map preview card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.dp,
                                    color = borderCol,
                                    shape = RoundedCornerShape(18.dp)
                                ),
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
                                .border(
                                    width = 1.dp,
                                    color = borderCol,
                                    shape = RoundedCornerShape(18.dp)
                                ),
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
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = borderCol.copy(alpha = 0.5f)
                                )
                                DetailRow("Target Spatial ID", incident.targetId, textColor, subTextColor)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = borderCol.copy(alpha = 0.5f)
                                )
                                DetailRow(
                                    "Target Agency",
                                    targetAgencyTypeForIncident(incident),
                                    textColor,
                                    subTextColor
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = borderCol.copy(alpha = 0.5f)
                                )
                                DetailRow(
                                    "Latitude",
                                    "%.6f".format(incident.latitude),
                                    textColor,
                                    subTextColor
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = borderCol.copy(alpha = 0.5f)
                                )
                                DetailRow(
                                    "Longitude",
                                    "%.6f".format(incident.longitude),
                                    textColor,
                                    subTextColor
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = borderCol.copy(alpha = 0.5f)
                                )
                                DetailRow(
                                    "Time",
                                    java.util.Date(incident.timestamp).toString(),
                                    textColor,
                                    subTextColor
                                )
                            }
                        }
                    }

                    // Responder action controls (visible for operational responder/admin roles)
                    if (uiState.userRole == "RESPONDER" || uiState.userRole == "ADMIN") {
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "RESPONDER ACTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = subTextColor,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.isLoadingDispatches) {
                                CircularProgressIndicator(color = RedPrimary)
                            } else if (uiState.dispatches.isEmpty()) {
                                Text(
                                    text = "No assigned dispatches",
                                    color = subTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                }
            }

            if (uiState.userRole == "CITIZEN") {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content()
                }
            } else {
                Box(modifier = Modifier.padding(innerPadding)) {
                    content()
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
                containerColor = cardBg,
                contentColor = textColor
            ) {
                Text(msg, color = textColor)
            }
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


