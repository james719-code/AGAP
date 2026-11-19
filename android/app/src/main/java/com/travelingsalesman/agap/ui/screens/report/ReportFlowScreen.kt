package com.travelingsalesman.agap.ui.screens.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.AgapPageIntro
import com.travelingsalesman.agap.ui.theme.AgapShape
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
    val showReceipt = uiState.phase == ReportPhase.SUBMITTED || uiState.isReportSavedLocally
    val scrollState = rememberScrollState()

    LaunchedEffect(showReceipt, uiState.submissionError) {
        if (showReceipt || uiState.submissionError != null) scrollState.scrollTo(0)
    }

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
        modifier = Modifier.imePadding(),
        topBar = {
            AgapTopBar(
                title = "Incident report",
                showBackButton = true,
                onBackClick = requestExit
            )
        },
        bottomBar = {
            if (!showReceipt) {
                ReportFormFooter(uiState, onSend = viewModel::submitReport)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AgapSpacing.xl, vertical = AgapSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xxl)
            ) {
                if (showReceipt) {
                    ReportStatusScreen(
                        result = uiState.submissionResult,
                        pendingMessage = uiState.submissionError,
                        onDone = onNavigateBack
                    )
                } else {
                    AgapPageIntro(
                        title = "Where is help needed?",
                        description = "Confirm the incident location, then add details for responders."
                    )
                    uiState.submissionError?.let { message ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            shape = AgapShape.md,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(message, Modifier.padding(AgapSpacing.md), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    AdaptiveReportForm(
                        uiState = uiState,
                        onTypeSelected = viewModel::setEmergencyType,
                        onSubjectChanged = viewModel::setSubjectRelation,
                        onPeopleAffectedChanged = viewModel::setPeopleAffected,
                        onDescriptionChanged = viewModel::setDescription,
                        onRefreshLocation = viewModel::acquireGpsLocation,
                        onOpenMapPicker = onNavigateToOfflineMapPicker
                    )
                }
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
                    Text(if (isSending) "Leave" else "Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(if (isSending) "Keep waiting" else "Keep editing")
                }
            }
        )
    }
}

@Composable
internal fun ReportFormFooter(uiState: ReportUiState, onSend: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = AgapSpacing.xs
    ) {
        Box(Modifier.navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = AgapSpacing.xl, vertical = AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!uiState.isSubmitting && !uiState.hasConfirmedIncidentLocation) {
                    Text(
                        "Confirm the incident location to send.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onSend,
                    enabled = uiState.canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = AgapShape.md
                ) {
                    Text(if (uiState.isSubmitting) "Sending report…" else "Send incident report")
                }
            }
        }
    }
}
