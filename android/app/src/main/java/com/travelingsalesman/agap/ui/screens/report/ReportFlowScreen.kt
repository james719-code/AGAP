package com.travelingsalesman.agap.ui.screens.report

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportFlowScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOfflineMapPicker: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "REPORT HAZARD (${uiState.currentStep}/5)",
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
                1 -> Step1EmergencyType(
                    selectedType = uiState.selectedType,
                    onSelectType = { viewModel.setEmergencyType(it) },
                    onNext = { viewModel.nextStep() }
                )
                2 -> Step2SubjectAndDanger(
                    subjectRelation = uiState.subjectRelation,
                    immediateDanger = uiState.immediateDanger,
                    onSubjectChanged = { relation, danger -> viewModel.setSubjectRelation(relation, danger) },
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                3 -> Step3LocationOption(
                    locationSource = uiState.locationSource,
                    latitude = uiState.latitude,
                    longitude = uiState.longitude,
                    accuracyMeters = uiState.accuracyMeters,
                    barangayCode = uiState.barangayCode,
                    onLocationChosen = { src, lat, lon, acc, brgy ->
                        viewModel.setLocationChoice(src, lat, lon, acc, brgy)
                    },
                    onOpenMapPicker = onNavigateToOfflineMapPicker,
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                4 -> Step4Details(
                    description = uiState.description,
                    peopleAffected = uiState.peopleAffected,
                    onDescriptionChanged = { viewModel.setDescription(it) },
                    onPeopleCountChanged = { viewModel.setPeopleAffected(it) },
                    onPrev = { viewModel.prevStep() },
                    onNext = { viewModel.nextStep() }
                )
                5 -> Step5Review(
                    uiState = uiState,
                    onSubmit = { viewModel.submitReport() },
                    onPrev = { viewModel.prevStep() }
                )
                6 -> Step6Result(
                    submissionResult = uiState.submissionResult,
                    submissionError = uiState.submissionError,
                    onDone = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun Step1EmergencyType(
    selectedType: String,
    onSelectType: (String) -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text("Step 1: Select Emergency Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
    Text("Choose the emergency category that best describes the incident.", style = MaterialTheme.typography.bodyMedium, color = subTextColor)

    val options = defaultEmergencyTypeOptions()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSelected = selectedType.equals(opt.code, ignoreCase = true)
            Card(
                onClick = { onSelectType(opt.code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                shape = AgapShape.md,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) RedPrimary else borderCol, shape = AgapShape.md)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(opt.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isSelected) RedPrimary else textColor)
                }
            }
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
    ) {
        Text("NEXT: SUBJECT & DANGER", fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun Step2SubjectAndDanger(
    subjectRelation: String,
    immediateDanger: Boolean,
    onSubjectChanged: (String, Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text("Step 2: Who Needs Assistance?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)

    val subjects = listOf(
        "SELF" to "Myself",
        "OTHER_PERSON" to "Another Person",
        "PUBLIC_OR_UNKNOWN" to "Public / Unknown Hazard"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        subjects.forEach { (code, label) ->
            val isSelected = subjectRelation.equals(code, ignoreCase = true)
            Card(
                onClick = { onSubjectChanged(code, immediateDanger) },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                shape = AgapShape.md,
                modifier = Modifier.fillMaxWidth().border(if (isSelected) 2.dp else 1.dp, if (isSelected) RedPrimary else borderCol, AgapShape.md)
            ) {
                Text(label, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = if (isSelected) RedPrimary else textColor)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = immediateDanger,
            onCheckedChange = { onSubjectChanged(subjectRelation, it) },
            colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
        )
        Text("IMMEDIATE DANGER INDICATED", fontWeight = FontWeight.Bold, color = RedPrimary, modifier = Modifier.padding(start = 8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("BACK", color = textColor)
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
            Text("NEXT: LOCATION", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun Step3LocationOption(
    locationSource: LocationSource,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    barangayCode: String?,
    onLocationChosen: (LocationSource, Double?, Double?, Float?, String?) -> Unit,
    onOpenMapPicker: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text("Step 3: Incident Location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
    Text("Select how incident coordinates are determined:", style = MaterialTheme.typography.bodyMedium, color = subTextColor)

    val options = listOf(
        LocationSource.GPS to "1. USE MY CURRENT GPS LOCATION",
        LocationSource.MAP_PIN to "2. CHOOSE ON OFFLINE MAP (PIN)",
        LocationSource.BARANGAY_REFERENCE to "3. SELECT BARANGAY (APPROXIMATE)",
        LocationSource.UNKNOWN to "4. SEND WITHOUT LOCATION"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (src, label) ->
            val isSelected = locationSource == src
            Card(
                onClick = {
                    if (src == LocationSource.MAP_PIN) {
                        onOpenMapPicker()
                    } else if (src == LocationSource.BARANGAY_REFERENCE) {
                        onLocationChosen(LocationSource.BARANGAY_REFERENCE, 13.1391, 123.7438, null, "BRGY_CENTRAL")
                    } else if (src == LocationSource.UNKNOWN) {
                        onLocationChosen(LocationSource.UNKNOWN, null, null, null, null)
                    } else {
                        onLocationChosen(LocationSource.GPS, latitude, longitude, accuracyMeters, null)
                    }
                },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                shape = AgapShape.md,
                modifier = Modifier.fillMaxWidth().border(if (isSelected) 2.dp else 1.dp, if (isSelected) RedPrimary else borderCol, AgapShape.md)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(label, fontWeight = FontWeight.Bold, color = if (isSelected) RedPrimary else textColor)
                    if (isSelected) {
                        val locText = when (src) {
                            LocationSource.GPS -> "GPS Fix: ${latitude ?: "Acquiring..."} , ${longitude ?: ""}"
                            LocationSource.MAP_PIN -> "Selected Pin: ${latitude ?: "Tap to choose"} , ${longitude ?: ""}"
                            LocationSource.BARANGAY_REFERENCE -> "Barangay Reference: ${barangayCode ?: "Central"} (Approximate)"
                            LocationSource.UNKNOWN -> "Location: Unknown / None"
                        }
                        Text(locText, style = MaterialTheme.typography.bodySmall, color = RedPrimary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("BACK", color = textColor)
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
            Text("NEXT: DETAILS", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun Step4Details(
    description: String,
    peopleAffected: Int,
    onDescriptionChanged: (String) -> Unit,
    onPeopleCountChanged: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text("Step 4: Additional Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)

    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChanged,
        label = { Text("Landmarks / Description") },
        placeholder = { Text("Enter landmark details or description...") },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = AgapShape.sm,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RedPrimary, unfocusedBorderColor = borderCol)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("BACK", color = textColor)
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
            Text("REVIEW REPORT", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun Step5Review(
    uiState: ReportUiState,
    onSubmit: () -> Unit,
    onPrev: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text("Step 5: Review & Submit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, borderCol, AgapShape.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Category: ${uiState.selectedType}", fontWeight = FontWeight.Bold, color = RedPrimary)
            Text("Subject: ${uiState.subjectRelation} (Immediate Danger: ${if (uiState.immediateDanger) "YES" else "NO"})", color = textColor)
            Text("Location Source: ${uiState.locationSource.name}", color = textColor)
            Text("Coordinates: ${uiState.latitude ?: "N/A"}, ${uiState.longitude ?: "N/A"}", color = subTextColor)
            if (uiState.description.isNotBlank()) {
                Text("Landmark/Notes: ${uiState.description}", color = textColor)
            }
        }
    }

    if (uiState.isSubmitting) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RedPrimary)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("BACK", color = textColor)
            }
            Button(onClick = onSubmit, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
                Text("SUBMIT NOW", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun Step6Result(
    submissionResult: String?,
    submissionError: String?,
    onDone: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (submissionError != null) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = RedPrimary, modifier = Modifier.height(64.dp))
            Text("Submission Warning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = RedPrimary)
            Text(submissionError, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenActive, modifier = Modifier.height(64.dp))
            Text("Report Submitted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GreenActive)
            Text(submissionResult ?: "Your report has been submitted.", style = MaterialTheme.typography.bodyMedium, color = textColor)
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
        ) {
            Text("RETURN TO HOME", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
