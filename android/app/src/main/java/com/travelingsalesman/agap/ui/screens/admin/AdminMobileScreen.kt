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
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun AdminMobileScreen(
    onNavigateToIncidentList: () -> Unit,
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

    val activeIncidents = uiState.incidents.filter { it.status == "ACTIVE" }
    val dispatchedIncidents = uiState.incidents.filter { it.status == "DISPATCHED" }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "ADMIN MOBILE COMMAND",
                onProfileClick = onNavigateToProfile,
                actions = {
                    IconButton(onClick = onNavigateToIncidentList) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = "Incident Registry",
                            tint = textColor
                        )
                    }
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Legazpi Mesh GIS Triage & Dispatch",
                            color = subTextColor,
                            fontSize = 12.sp
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedPrimary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(width = 1.dp, color = RedPrimary.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "ADMIN",
                            color = RedPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Stat Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active SOS
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = RedPrimary, modifier = Modifier.padding(end = 4.dp))
                                Text("ACTIVE", color = RedPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "${activeIncidents.size}", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Dispatched
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = AmberWarning, modifier = Modifier.padding(end = 4.dp))
                                Text("DISPATCHED", color = AmberWarning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "${dispatchedIncidents.size}", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Responders
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = BlueMedical, modifier = Modifier.padding(end = 4.dp))
                                Text("UNITS", color = BlueMedical, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "${uiState.responders.size}", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Triage Section Header
            item {
                Text(
                    text = "IMMEDIATE TRIAGE ACTION REQUIRED (${activeIncidents.size})",
                    color = RedPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }

            if (activeIncidents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No pending active emergency SOS requests.", color = subTextColor, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(activeIncidents, key = { it.incidentId }) { incident ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "INCIDENT #${incident.incidentId}",
                                    color = textColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                                StatusBadge(status = incident.status)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Type: ${incident.emergencyType} | Target: ${incident.targetId.take(16)}...",
                                color = subTextColor,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "GPS: %.4f, %.4f".format(incident.latitude, incident.longitude),
                                color = subTextColor.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.dispatchResponder(incident.incidentId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("DISPATCH UNITS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { viewModel.resolveIncident(incident.incidentId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenOnline),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("MARK RESOLVED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Field Responder Units Section Header
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "FIELD RESPONDER ROSTER (${uiState.responders.size})",
                    color = BlueMedical,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }

            items(uiState.responders, key = { it.id }) { responder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = responder.unitCallsign, color = textColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text(text = "Agency #${responder.agencyId} • Target: ${responder.targetId.take(14)}...", color = subTextColor, fontSize = 11.sp)
                        }

                        StatusBadge(status = responder.status)
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
                containerColor = cardBg,
                contentColor = textColor
            ) {
                Text(msg, color = textColor)
            }
        }
    }
}


