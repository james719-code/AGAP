package com.travelingsalesman.agap.ui.screens.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.travelingsalesman.agap.ui.components.StatusBanner
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateSurface
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun HomeScreen(
    onNavigateToIncidentList: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "CITIZEN SOS",
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // User Greeting & Status Banner
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hello, ${uiState.username ?: "Citizen"}",
                            color = SlateTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Legazpi Emergency Mesh Active",
                            color = SlateTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onNavigateToIncidentList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("INCIDENTS", color = RedPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                StatusBanner(
                    isOnline = uiState.isOnline,
                    pendingPacketCount = uiState.pendingPacketCount
                )
            }

            // Center SOS Trigger & Selector
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                EmergencyTypePicker(
                    selectedType = uiState.selectedEmergencyType,
                    onTypeSelected = viewModel::selectEmergencyType
                )

                Spacer(modifier = Modifier.height(16.dp))

                SosButton(
                    onClick = viewModel::triggerSos,
                    isLoading = uiState.isSendingSos
                )
            }

            // Embedded OSMDroid Incident Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize(),
                        userLocation = uiState.userLocation,
                        incidents = uiState.incidents
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
                    containerColor = SlateSurface,
                    contentColor = SlateTextPrimary
                ) {
                    Text(msg)
                }
            }
        }
    }
}
