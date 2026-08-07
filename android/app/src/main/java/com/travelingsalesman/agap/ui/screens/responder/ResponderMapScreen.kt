package com.travelingsalesman.agap.ui.screens.responder

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LocalShipping
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
    val activeIncidents = uiState.incidents.filter { it.status == "ACTIVE" || it.status == "DISPATCHED" }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

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
            // Full-screen OSMDroid map with theme-adaptive tiles
            OsmdroidMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation,
                incidents = uiState.incidents,
                zoomLevel = 14.5,
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
                                text = "Current Status: ${uiState.responderStatus} • ${activeIncidents.size} Active SOS Calls",
                                color = subTextColor,
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
                        val statuses = listOf("IDLE", "EN ROUTE", "ON SCENE", "RESOLVED")
                        statuses.forEach { st ->
                            val isSelected = uiState.responderStatus == st
                            Button(
                                onClick = { viewModel.updateMyStatus(st) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) RedPrimary else borderCol
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
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

            // Bottom Latest Incident Banner with ETA & Distance Overlay
            activeIncidents.firstOrNull()?.let { latest ->
                // Calculate distance and ETA dynamically from GPS coordinates or base coordinates
                val currentLat = uiState.userLocation?.latitude
                    ?: uiState.myUnit?.currentLat?.takeIf { it != 0.0 }
                    ?: 13.1391
                val currentLon = uiState.userLocation?.longitude
                    ?: uiState.myUnit?.currentLon?.takeIf { it != 0.0 }
                    ?: 123.7438

                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLat, currentLon,
                    latest.latitude, latest.longitude,
                    results
                )
                val meters = results[0]
                val km = meters / 1000.0
                val mins = kotlin.math.max(1, (km / 35.0 * 60.0).toInt())
                val distanceStr = if (km < 1.0) "Distance: %d m".format(meters.toInt()) else "Distance: %.1f km".format(km)
                val etaStr = "Est. ETA: $mins mins"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
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
                                text = "PRIORITY ASSIGNMENT: ${latest.emergencyType}",
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            )
                            StatusBadge(status = latest.status)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target ID: ${latest.targetId.take(16)}...",
                            color = subTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Dynamic Distance & ETA Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(borderCol.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = distanceStr, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = etaStr, color = GreenOnline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigateToDetail(latest.incidentId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("VIEW DETAILS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.updateMyStatus("EN ROUTE") },
                                enabled = uiState.responderStatus != "EN ROUTE",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ACCEPT DISPATCH", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
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
                containerColor = cardBg,
                contentColor = textColor
            ) {
                Text(msg)
            }
        }
    }
}


