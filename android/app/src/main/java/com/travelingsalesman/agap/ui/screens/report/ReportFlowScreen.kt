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
        topBar = {
            AgapTopBar(
                title = "REPORT HAZARD (${uiState.currentStep.coerceAtMost(5)}/5)",
                onProfileClick = onNavigateBack
            )
        },
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
            when (uiState.currentStep) {
                1 -> ReportTypeScreen(
                    selectedType = uiState.selectedType,
                    onSelectType = { viewModel.setEmergencyType(it) },
                    onNext = { viewModel.nextStep() }
                )
                2 -> ReportSubjectScreen(
                    subjectRelation = uiState.subjectRelation,
                    immediateDanger = uiState.immediateDanger,
                    onSubjectChanged = { relation, danger -> viewModel.setSubjectRelation(relation, danger) },
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                3 -> ReportLocationScreen(
                    locationSource = uiState.locationSource,
                    latitude = uiState.latitude,
                    longitude = uiState.longitude,
                    accuracyMeters = uiState.accuracyMeters,
                    barangayCode = uiState.barangayCode,
                    cachedBarangays = uiState.cachedBarangays,
                    onLocationChosen = { src, lat, lon, acc, brgy ->
                        viewModel.setLocationChoice(src, lat, lon, acc, brgy)
                    },
                    onOpenMapPicker = onNavigateToOfflineMapPicker,
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                4 -> ReportDetailsScreen(
                    description = uiState.description,
                    peopleAffected = uiState.peopleAffected,
                    onDescriptionChanged = { viewModel.setDescription(it) },
                    onPeopleCountChanged = { viewModel.setPeopleAffected(it) },
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                5 -> ReportReviewScreen(
                    uiState = uiState,
                    onSubmit = { viewModel.submitReport() },
                    onPrev = { viewModel.prevStep() }
                )
                6 -> ReportStatusScreen(
                    submissionResult = uiState.submissionResult,
                    submissionError = uiState.submissionError,
                    onDone = onNavigateBack
                )
            }
        }
    }
}
