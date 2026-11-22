package com.travelingsalesman.agap.ui.screens.map

import androidx.compose.material3.Surface
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.luminance
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
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.EmergencyType
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
                    isDarkTheme = bgColor.luminance() < 0.5f
                )

                // Top status pill: compact so the map stays visible.
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = AgapSpacing.lg, end = AgapSpacing.lg, top = AgapSpacing.xxl + AgapSpacing.sm),
                    shape = AgapShape.pill,
                    color = cardBg,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                ) {
                    val accent = if (uiState.isOnline) AgapStatus.success else AgapStatus.warning
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = AgapSpacing.lg, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, AgapShape.circle), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = if (uiState.activeIncident != null) "Tracking your SOS" else "Your safety map",
                                color = textColor,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                                Icon(if (uiState.isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, null, tint = accent, modifier = Modifier.size(12.dp))
                                Text(if (uiState.isOnline) "Online" else "No internet \u00b7 offline map", color = accent, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Bottom Active Incident Card Overlay (if present)
                uiState.activeIncident?.let { incident ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AgapSpacing.lg)
                            .align(Alignment.BottomCenter)
                            .clip(AgapShape.lg)
                            .border(width = 1.5.dp, color = MaterialTheme.colorScheme.primary, shape = AgapShape.lg),
                        shape = AgapShape.lg,
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
                            val type = EmergencyType.fromCode(incident.emergencyType)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, AgapShape.md), contentAlignment = Alignment.Center) {
                                    Icon(type.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                                    Text("Active SOS · ${type.label}", color = textColor, style = MaterialTheme.typography.titleMedium)
                                    StatusBadge(status = incident.status)
                                }
                            }

                            Spacer(modifier = Modifier.height(AgapSpacing.xs))
                            Text(
                                text = "Incident #${incident.incidentId}",
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
