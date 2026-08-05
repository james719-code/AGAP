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
    onNavigateToIncidentList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: (() -> Unit)? = null,
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
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                actions = {
                    IconButton(onClick = onNavigateToIncidentList) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = "Incident List",
                            tint = textColor
                        )
                    }
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full-screen OSMDroid map with theme-adaptive tiles
            OsmdroidMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = uiState.userLocation,
                incidents = uiState.incidents,
                zoomLevel = 14.5,
                isDarkTheme = isDarkTheme
            )

            // Top Status Bar Overlay
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Unit Status: ${uiState.responderStatus}",
                            color = textColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${activeIncidents.size} Active Incidents nearby",
                            color = subTextColor,
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
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = if (uiState.responderStatus == "IDLE") Color.White else Color.Black,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = if (uiState.responderStatus == "IDLE") "SET DISPATCHED" else "SET IDLE",
                                color = if (uiState.responderStatus == "IDLE") Color.White else Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
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
                                text = "LATEST INCIDENT: ${latest.emergencyType}",
                                color = RedPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            )
                            StatusBadge(status = latest.status)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target ID: ${latest.targetId}",
                            color = subTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onNavigateToDetail(latest.incidentId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("VIEW & DISPATCH", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
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


