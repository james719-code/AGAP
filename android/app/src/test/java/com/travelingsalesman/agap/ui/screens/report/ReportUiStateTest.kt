package com.travelingsalesman.agap.ui.screens.report

import com.travelingsalesman.agap.domain.model.LocationSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportUiStateTest {

    @Test
    fun automaticLocationFailureDoesNotMakeANewReportDirty() {
        val state = ReportUiState(locationSource = LocationSource.UNKNOWN)

        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun citizenEnteredDetailsAndManualLocationMakeAReportDirty() {
        assertTrue(ReportUiState(description = "Smoke near the bridge").hasUnsavedChanges)
        assertTrue(ReportUiState(selectedType = "FIRE").hasUnsavedChanges)
        assertTrue(ReportUiState(isLocationManuallySelected = true).hasUnsavedChanges)
    }

    @Test
    fun submittedReportDoesNotPromptForDiscard() {
        val state = ReportUiState(
            phase = ReportPhase.SUBMITTED,
            description = "Smoke near the bridge",
            isLocationManuallySelected = true
        )

        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun onlyRealGpsOrMapCoordinatesUnlockSubmission() {
        val gpsFix = ReportUiState(
            locationSource = LocationSource.GPS,
            latitude = 13.1391,
            longitude = 123.7438
        )
        val mapPin = ReportUiState(
            locationSource = LocationSource.MAP_PIN,
            latitude = 13.1402,
            longitude = 123.7423,
            isLocationManuallySelected = true
        )
        val fallbackCoordinates = ReportUiState(
            locationSource = LocationSource.GPS,
            latitude = 0.0,
            longitude = 0.0
        )
        val unknownSource = ReportUiState(
            locationSource = LocationSource.UNKNOWN,
            latitude = 13.1391,
            longitude = 123.7438
        )

        assertTrue(gpsFix.hasConfirmedIncidentLocation)
        assertTrue(gpsFix.canSubmit)
        assertTrue(mapPin.hasConfirmedIncidentLocation)
        assertFalse(fallbackCoordinates.hasConfirmedIncidentLocation)
        assertFalse(fallbackCoordinates.canSubmit)
        assertFalse(unknownSource.hasConfirmedIncidentLocation)
    }

    @Test
    fun locallySavedReportCannotBeSubmittedAgain() {
        val state = ReportUiState(
            locationSource = LocationSource.MAP_PIN,
            latitude = 13.1391,
            longitude = 123.7438,
            isReportSavedLocally = true
        )

        assertFalse(state.canEditDraft)
        assertFalse(state.canSubmit)
        assertFalse(state.hasUnsavedChanges)
    }
}
