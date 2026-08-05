package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.IncidentCard
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun IncidentListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "INCIDENT FEED",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = viewModel::refreshRemoteIncidents) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = SlateTextPrimary
                        )
                    }
                }
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    color = SlateTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))

                listOf("ACTIVE", "DISPATCHED", "RESOLVED").forEach { status ->
                    FilterChip(
                        selected = uiState.selectedStatusFilter == status,
                        onClick = { viewModel.setStatusFilter(status) },
                        label = { Text(status, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Type:",
                    color = SlateTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))

                listOf("MEDICAL", "CRIME", "ACCIDENT", "DISASTER").forEach { type ->
                    FilterChip(
                        selected = uiState.selectedTypeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(type, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading && uiState.incidents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else if (uiState.incidents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No emergency incidents recorded.",
                        color = SlateTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.incidents, key = { it.incidentId }) { incident ->
                        IncidentCard(
                            incident = incident,
                            onClick = { onNavigateToDetail(incident.incidentId) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearErrorMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                containerColor = RedPrimary
            ) {
                Text(error, color = Color.White)
            }
        }
    }
}
