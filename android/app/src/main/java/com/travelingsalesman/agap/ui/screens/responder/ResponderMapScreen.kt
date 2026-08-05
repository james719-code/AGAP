package com.travelingsalesman.agap.ui.screens.responder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateSurface
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun ResponderMapScreen(
    onNavigateToIncidentList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResponderMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeIncidents = uiState.incidents.filter { it.status == "ACTIVE" || it.status == "DISPATCHED" }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "RESPONDER DASHBOARD",
                onProfileClick = onNavigateToProfile,
                actions = {
                    IconButton(onClick = onNavigateToIncidentList) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = "Incident List",
                            tint = SlateTextPrimary
                        )
                    }
                }
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full-screen OSMDroid map
            OsmdroidMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation,
                incidents = uiState.incidents,
                zoomLevel = 14.5
            )

            // Top Status Bar Overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.95f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Unit Status: ${uiState.responderStatus}",
                            color = SlateTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${activeIncidents.size} Active Incidents nearby",
                            color = SlateTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row {
                        Button(
                            onClick = {
                                val nextStatus = if (uiState.responderStatus == "IDLE") "DISPATCHED" else "IDLE"
                                viewModel.updateMyStatus(nextStatus)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.responderStatus == "IDLE") GreenOnline else AmberWarning
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(if (uiState.responderStatus == "IDLE") "SET DISPATCHED" else "SET IDLE")
                        }
                    }
                }
            }

            // Bottom Latest Incident Banner
            activeIncidents.firstOrNull()?.let { latest ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LATEST INCIDENT: ${latest.emergencyType}",
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            StatusBadge(status = latest.status)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target ID: ${latest.targetId}",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onNavigateToDetail(latest.incidentId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("VIEW & DISPATCH", color = Color.White, fontWeight = FontWeight.Bold)
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
                containerColor = SlateSurface,
                contentColor = SlateTextPrimary
            ) {
                Text(msg)
            }
        }
    }
}
