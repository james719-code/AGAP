package com.travelingsalesman.agap.ui.screens.map

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.screens.home.HomeViewModel
import com.travelingsalesman.agap.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenMapScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "EMERGENCY MAP",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState,
                onNotificationClick = onNavigateToNotifications
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
            ) {
                // Full Screen Interactive OSMDroid Map (Citizen view: OWN active report only)
                val ownActiveIncidents = uiState.activeIncident?.let { listOf(it) } ?: emptyList()
                OsmdroidMapView(
                    modifier = Modifier.fillMaxSize(),
                    userLocation = uiState.userLocation,
                    incidents = ownActiveIncidents,
                    responders = emptyList(),
                    showIncidents = true,
                    showResponders = false,
                    zoomLevel = 14.5,
                    isDarkTheme = isSystemInDarkTheme()
                )

                // Top Status Info Overlay Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(16.dp))
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.92f))
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
                                text = "OFFLINE EMERGENCY MAP",
                                color = textColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (uiState.activeIncident != null) "Tracking your active emergency report" else "No active report",
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (uiState.isOnline) "ONLINE" else "MESH P2P",
                            color = if (uiState.isOnline) com.travelingsalesman.agap.ui.theme.GreenOnline else RedPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Bottom Active Incident Card Overlay (if present)
                uiState.activeIncident?.let { incident ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(18.dp))
                            .border(width = 1.5.dp, color = RedPrimary, shape = RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTIVE SOS DISTRESS ALERT",
                                    color = RedPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.8.sp
                                )
                                StatusBadge(status = incident.status)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Incident #${incident.incidentId} • ${incident.emergencyType}",
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "GPS: %.4f, %.4f".format(incident.latitude, incident.longitude),
                                color = subTextColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
