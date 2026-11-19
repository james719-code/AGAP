package com.travelingsalesman.agap.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.components.*
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.screens.home.*
import com.travelingsalesman.agap.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders production home components without an account or emergency network. */
class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightHomeActionsNeverSendAnAlert() = checkHome(false, 1f, "home-light")
    @Test fun darkHomeActionsNeverSendAnAlert() = checkHome(true, 1f, "home-dark")
    @Test fun largeTextKeepsReportAndConnectionSettingsReachable() = checkHome(false, 1.5f, "home-large-text")
    @Test fun doubleTextKeepsReportAndConnectionSettingsReachable() = checkHome(false, 2f, "home-double-text")

    @Test fun earlyReleaseAndCancelledDragNeverSend() {
        var sends = 0
        renderSos { sends++ }
        compose.onNodeWithTag("sos-handle").performTouchInput {
            swipe(center, center + Offset(0f, -30f), 500)
        }
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithTag("sos-handle").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, -220f), 500)
            cancel()
        }
        compose.runOnIdle { assertEquals(0, sends) }
    }

    @Test fun fullDragAndReleaseSendsExactlyOneSelectedEmergency() {
        val sent = mutableListOf<String>()
        renderSos { sent += it }
        compose.onNodeWithTag("sos-handle").performTouchInput {
            swipe(center, center + Offset(0f, -220f), 600)
        }
        compose.runOnIdle { assertEquals(listOf("MEDICAL"), sent) }
    }

    @Test fun changingCategoryDuringDragDoesNotMoveTheControlOrSendTwice() {
        val sent = mutableListOf<String>()
        renderSos { sent += it }
        // From the default medical target, drag toward the fire bearing to the upper right.
        compose.onNodeWithTag("sos-handle").performTouchInput {
            swipe(center, center + Offset(210f, -68f), 600)
        }
        compose.runOnIdle { assertEquals(listOf("FIRE"), sent) }
    }

    private fun renderSos(onSend: (String) -> Unit) {
        compose.setContent {
            AGAPTheme(darkTheme = false) {
                var selected by remember { mutableStateOf("MEDICAL") }
                EmergencySosJoystick(defaultEmergencyTypeOptions(), selected, { selected = it }, onSend,
                    isLoading = false, hapticsEnabled = false)
            }
        }
    }

    private fun checkHome(dark: Boolean, fontScale: Float, screenshot: String) {
        var sends = 0
        var reports = 0
        var sosNavigation = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AGAPTheme(darkTheme = dark) {
                    var selected by remember { mutableStateOf("MEDICAL") }
                    var expanded by remember { mutableStateOf(false) }
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = { AgapTopBar() },
                        bottomBar = {
                            AgapBottomBar(AgapDestinations.HOME_ROUTE, onNavigate = {}, onSosClick = { sosNavigation++ })
                        }
                    ) { padding ->
                        Column(
                            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(AgapSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
                        ) {
                            HomeReadiness(true, true, true, true, 2)
                            EmergencySosJoystick(
                                defaultEmergencyTypeOptions(), selected,
                                onTypeSelected = { selected = it },
                                onEmergencyTriggered = { sends++ },
                                isLoading = false, hapticsEnabled = false
                            )
                            DetailedReportAction({ reports++ }, true, TransmissionMode.AUTO)
                            CommunicationsCard(HomeUiState(), expanded, { expanded = !expanded }, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Need emergency help?").assertIsDisplayed()
        saveScreenshot(screenshot)
        compose.onNodeWithText("Fire").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithContentDescription("SOS access").performClick()
        compose.runOnIdle {
            assertEquals(1, sosNavigation)
            assertEquals(0, sends)
        }
        compose.onNodeWithText("Report an incident").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, reports) }
        compose.onNodeWithText("Connection settings").performScrollTo().performClick()
        compose.onNodeWithText("TRANSMISSION MODE").assertExists()
        compose.runOnIdle { assertEquals(0, sends) }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
