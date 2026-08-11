package com.travelingsalesman.agap.ui.screens.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun ReportFlowScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOfflineMapPicker: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    val shouldConfirmExit = uiState.hasUnsavedChanges || uiState.isSubmitting

    val requestExit = {
        if (shouldConfirmExit) {
            showDiscardConfirmation = true
        } else {
            onNavigateBack()
        }
    }

    // Let the dialog receive system back while it is visible, so back dismisses the
    // confirmation instead of immediately navigating away from the report.
    BackHandler(enabled = !showDiscardConfirmation, onBack = requestExit)

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "REPORT INCIDENT",
                showBackButton = true,
                onBackClick = requestExit
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
        ) {
            if (uiState.phase == ReportPhase.SUBMITTED) {
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

    if (showDiscardConfirmation) {
        val isSending = uiState.isSubmitting
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = {
                Text(if (isSending) "Leave while sending?" else "Discard incident report?")
            },
            text = {
                Text(
                    if (isSending) {
                        "Your incident report is still being sent. Leaving may cancel the submission."
                    } else {
                        "Your unsent report details will be lost. Do you want to discard them?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onNavigateBack()
                    }
                ) {
                    Text(if (isSending) "LEAVE" else "DISCARD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text("KEEP EDITING")
                }
            }
        )
    }
}
