package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary

import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun AdminMobileScreen(
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminMobileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val activeIncidents = uiState.incidents.filter { it.status !in listOf("RESOLVED", "CANCELLED", "FALSE_ALARM") }
    val dispatchedIncidents = uiState.incidents.filter { it.status in listOf("DISPATCHED", "EN_ROUTE", "ON_SCENE") }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "ADMIN MOBILE COMMAND",
                onProfileClick = onNavigateToProfile,
                bleMeshState = uiState.bleMeshState
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            if (uiState.isRefreshing) {
                item {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = RedPrimary
                    )
                }
            }

            // Header Banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Admin Operator Control",
                            color = textColor,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Legazpi Mesh GIS Triage & Dispatch",
                            color = subTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedPrimary.copy(alpha = 0.15f)),
                        shape = AgapShape.xs,
                        modifier = Modifier.border(width = 1.dp, color = RedPrimary.copy(alpha = 0.3f), shape = AgapShape.xs)
                    ) {
                        Text(
                            text = "ADMIN",
                            color = RedPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Stat Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    // Active SOS
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = AgapShape.lg
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = RedPrimary, modifier = Modifier.padding(end = 4.dp))
                                Text("OPEN", color = RedPrimary, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(AgapSpacing.xs))
                            Text(text = "${activeIncidents.size}", color = textColor, style = MaterialTheme.typography.headlineMedium)
                        }
                    }

                    // Dispatched
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = AgapShape.lg
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = AmberWarning, modifier = Modifier.padding(end = 4.dp))
                                Text("DISPATCHED", color = AmberWarning, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(AgapSpacing.xs))
                            Text(text = "${dispatchedIncidents.size}", color = textColor, style = MaterialTheme.typography.headlineMedium)
                        }
                    }

                    // Responders
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = AgapShape.lg
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = BlueMedical, modifier = Modifier.padding(end = 4.dp))
                                Text("UNITS", color = BlueMedical, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(AgapSpacing.xs))
                            Text(text = "${uiState.responders.size}", color = textColor, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                    Text(
                        text = "FIELD INCIDENT MAP (${activeIncidents.size})",
                        color = BlueMedical,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg)
                            .background(cardBg)
                    ) {
                        OsmdroidMapView(
                            modifier = Modifier.fillMaxSize(),
                            userLocation = uiState.userLocation,
                            incidents = activeIncidents,
                            zoomLevel = 13.5,
                            isDarkTheme = isSystemInDarkTheme()
                        )
                    }
                }
            }

            // Triage Section Header
            item {
                Text(
                    text = "IMMEDIATE TRIAGE ACTION REQUIRED (${activeIncidents.size})",
                    color = RedPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (activeIncidents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = AgapShape.lg
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AgapSpacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No pending active emergency SOS requests.", color = subTextColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(activeIncidents, key = { it.incidentId }) { incident ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AgapShape.lg)
                            .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = AgapShape.lg
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "INCIDENT #${incident.incidentId}",
                                    color = textColor,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatusBadge(status = incident.status)
                            }

                            Spacer(modifier = Modifier.height(AgapSpacing.xs))

                            Text(
                                text = "Type: ${incident.emergencyType} | Target: ${incident.targetId.take(16)}...",
                                color = subTextColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "GPS: %.4f, %.4f".format(incident.latitude, incident.longitude),
                                color = subTextColor.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(AgapSpacing.sm))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                            ) {
                                Button(
                                    onClick = { viewModel.openDispatchSheet(incident.incidentId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = AgapShape.sm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("DISPATCH UNITS", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = { viewModel.resolveIncident(incident.incidentId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenOnline),
                                    shape = AgapShape.sm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("MARK RESOLVED", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Field Responder Units Section Header
            item {
                Spacer(modifier = Modifier.height(AgapSpacing.xs))
                Text(
                    text = "FIELD RESPONDER ROSTER (${uiState.responders.size})",
                    color = BlueMedical,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            items(uiState.responders, key = { it.id }) { responder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AgapShape.lg)
                        .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = AgapShape.lg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AgapSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = responder.unitCallsign, color = textColor, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Agency #${responder.agencyId} • Target: ${responder.targetId.take(14)}...", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                        }

                        StatusBadge(status = responder.status)
                    }
                }
            }
        }

        uiState.selectedIncidentIdForDispatch?.let { incId ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = viewModel::closeDispatchSheet,
                title = { Text("Dispatch Responder Unit", color = textColor, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                        Text("Select a responder unit to dispatch to Incident #$incId:", color = subTextColor, style = MaterialTheme.typography.bodyMedium)
                        if (uiState.responders.isEmpty()) {
                            Text("No responder units available.", color = RedPrimary, style = MaterialTheme.typography.bodySmall)
                        } else {
                            uiState.responders.forEach { responder ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AgapShape.sm)
                                        .border(width = 1.dp, color = borderCol, shape = AgapShape.sm),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    onClick = { viewModel.dispatchResponder(incId, responder.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(AgapSpacing.md),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(responder.unitCallsign, style = MaterialTheme.typography.titleMedium, color = textColor)
                                            Text("Status: ${responder.status}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
                                        }
                                        StatusBadge(status = responder.status)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::closeDispatchSheet) {
                        Text("CANCEL", color = subTextColor)
                    }
                },
                containerColor = cardBg
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
                Text(msg, color = textColor)
            }
        }
    }
}


