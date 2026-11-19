package com.travelingsalesman.agap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.travelingsalesman.agap.data.repository.ReportDelivery
import com.travelingsalesman.agap.data.repository.SubmitReportResult
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.screens.report.AdaptiveReportForm
import com.travelingsalesman.agap.ui.screens.report.ReportFormFooter
import com.travelingsalesman.agap.ui.screens.report.ReportStatusScreen
import com.travelingsalesman.agap.ui.screens.report.ReportUiState
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mapConfirmationEnablesSendOnNarrowScreenWithLargeText() {
        var sends = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AGAPTheme {
                    var state by remember { mutableStateOf(ReportUiState()) }
                    Scaffold(
                        modifier = Modifier.widthIn(max = 320.dp),
                        bottomBar = { ReportFormFooter(state) { sends++ } }
                    ) { insets ->
                        Column(Modifier.padding(insets).verticalScroll(rememberScrollState())) {
                            AdaptiveReportForm(
                                uiState = state,
                                onTypeSelected = { state = state.copy(selectedType = it) },
                                onSubjectChanged = { _, _ -> },
                                onPeopleAffectedChanged = {},
                                onDescriptionChanged = {},
                                onRefreshLocation = {},
                                onOpenMapPicker = {
                                    state = state.copy(
                                        locationSource = LocationSource.MAP_PIN,
                                        latitude = 13.14,
                                        longitude = 123.74
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("Location required", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Send incident report").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Choose on map").performScrollTo().performClick()
        compose.onNodeWithText("Map pin confirmed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Fire").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Send incident report").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun receiptReflectsTheDeliveryState() {
        val receipt = mutableStateOf<ReportDelivery?>(ReportDelivery.QUEUED_FOR_RELAY)
        var returns = 0
        compose.setContent {
            AGAPTheme(darkTheme = true) {
                ReportStatusScreen(
                    result = receipt.value?.let { delivery ->
                        SubmitReportResult(
                            reportUuid = "test-report",
                            transportTokenHex = "000000000000",
                            message = "AGAP is looking for a nearby device to relay your report.",
                            delivery = delivery
                        )
                    },
                    pendingMessage = "Internet-only mode is offline.",
                    onDone = { returns++ }
                )
            }
        }

        compose.onNodeWithText("Waiting for a nearby relay").assertIsDisplayed()
        compose.onNodeWithText("Not yet confirmed").assertIsDisplayed()
        compose.onNodeWithText("Dispatch received your report").assertDoesNotExist()
        compose.runOnIdle { receipt.value = ReportDelivery.RECEIVED_BY_DISPATCH }
        compose.onNodeWithText("Dispatch received your report").assertIsDisplayed()
        compose.onNodeWithText("Received").assertIsDisplayed()
        compose.runOnIdle { receipt.value = ReportDelivery.LOCAL_TEST }
        compose.onNodeWithText("Test report saved on this phone").assertIsDisplayed()
        compose.onNodeWithText("Not sent · test mode").assertIsDisplayed()
        compose.runOnIdle { receipt.value = null }
        compose.onNodeWithText("Saved for retry").assertIsDisplayed()
        compose.onNodeWithText("Return to home").performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }
}
