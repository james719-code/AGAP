package com.travelingsalesman.agap.ui.screens.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.travelingsalesman.agap.ui.components.EmergencyTypePicker
import com.travelingsalesman.agap.ui.components.SosButton
import com.travelingsalesman.agap.ui.components.SystemStatusCard
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun HomeScreen(
    onNavigateToIncidentList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: (() -> Unit)? = null,
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
                title = "CITIZEN SOS",
                onProfileClick = onNavigateToProfile,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                notificationCount = if (uiState.pendingPacketCount > 0) uiState.pendingPacketCount else 0
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // User Greeting Header & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${uiState.username ?: "Citizen"}",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Your Lifetime. Always.",
                        color = subTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = onNavigateToIncidentList) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("FEED", color = RedPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }

            // Telemetry System Status Card
            SystemStatusCard(isOnline = uiState.isOnline)

            // Quick Alerts 4-Box Emergency Selector
            EmergencyTypePicker(
                selectedType = uiState.selectedEmergencyType,
                onTypeSelected = viewModel::selectEmergencyType
            )

            // Central Glowing Pulsing SOS Button
            SosButton(
                onClick = viewModel::triggerSos,
                isLoading = uiState.isSendingSos
            )

            // Embedded OSMDroid Incident Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize(),
                        userLocation = uiState.userLocation,
                        incidents = uiState.incidents,
                        isDarkTheme = isDarkTheme
                    )
                }
            }

            // Feedback Snackbar
            uiState.sosFeedbackMessage?.let { msg ->
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
}


