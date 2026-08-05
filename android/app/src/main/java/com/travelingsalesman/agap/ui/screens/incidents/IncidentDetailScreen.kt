package com.travelingsalesman.agap.ui.screens.incidents

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateSurface
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun IncidentDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val incident = uiState.incident

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "INCIDENT DETAILS",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = SlateBackground
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
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Map preview card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            OsmdroidMapView(
                                modifier = Modifier.fillMaxSize(),
                                incidents = listOf(incident),
                                zoomLevel = 16.0
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Detail Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = incident.emergencyType.uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RedPrimary
                                )
                                StatusBadge(status = incident.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            DetailRow("Incident ID", incident.incidentId)
                            DetailRow("Target Spatial ID", incident.targetId)
                            DetailRow("Latitude", "%.6f".format(incident.latitude))
                            DetailRow("Longitude", "%.6f".format(incident.longitude))
                            DetailRow("Time", java.util.Date(incident.timestamp).toString())
                        }
                    }
                }

                // Responder action controls (Only visible for RESPONDER or DISPATCHER/ADMIN role)
                if (uiState.userRole == "RESPONDER" || uiState.userRole == "DISPATCHER" || uiState.userRole == "ADMIN") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RESPONDER ACTIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { viewModel.updateStatus("DISPATCHED") },
                                enabled = !uiState.isUpdatingStatus && incident.status != "DISPATCHED",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("DISPATCH UNIT", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = { viewModel.updateStatus("RESOLVED") },
                                enabled = !uiState.isUpdatingStatus && incident.status != "RESOLVED",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenResolved),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("RESOLVE", color = Color.White, fontWeight = FontWeight.Bold)
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
                containerColor = SlateSurface,
                contentColor = SlateTextPrimary
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = SlateTextSecondary, fontSize = 13.sp)
        Text(text = value, color = SlateTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
