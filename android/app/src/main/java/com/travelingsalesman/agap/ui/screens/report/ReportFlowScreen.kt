package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar

@Composable
fun ReportFlowScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOfflineMapPicker: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AgapTopBar(title = "REPORT INCIDENT", onProfileClick = onNavigateBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.currentStep == 6) {
                ReportStatusScreen(
                    submissionResult = uiState.submissionResult,
                    submissionError = uiState.submissionError,
                    onDone = onNavigateBack
                )
            } else {
                AdaptiveReportForm(
                    uiState = uiState,
                    onTypeSelected = viewModel::setEmergencyType,
                    onSubjectChanged = viewModel::setSubjectRelation,
                    onPeopleAffectedChanged = viewModel::setPeopleAffected,
                    onDescriptionChanged = viewModel::setDescription,
                    onRefreshLocation = viewModel::acquireGpsLocation,
                    onOpenMapPicker = onNavigateToOfflineMapPicker,
                    onSend = viewModel::submitReport
                )
            }
        }
    }
}
