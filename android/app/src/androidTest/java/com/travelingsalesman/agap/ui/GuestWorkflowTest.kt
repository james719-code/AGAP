package com.travelingsalesman.agap.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.travelingsalesman.agap.ui.screens.guest.GuestExperience
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GuestWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportRequiresSignInAndCanBeCancelled() {
        var signIns = 0
        compose.setContent {
            AGAPTheme { GuestExperience({ signIns++ }, true, {}) }
        }
        compose.onNodeWithText("Fire").performScrollTo().performClick()
        compose.onNodeWithText("Report an incident").performScrollTo().performClick()
        assertSignInPrompt()
        compose.onNodeWithText("Keep exploring").performClick()
        compose.onNodeWithText("Sign in to continue").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, signIns) }
        compose.onNodeWithText("Report an incident").performScrollTo().performClick()
        assertSignInPrompt()
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, signIns) }
    }

    @Test fun alertsAndProfileShowGuestPromptsAndThemeWorks() {
        var signIns = 0
        val darkMode = mutableStateOf(true)
        compose.setContent {
            AGAPTheme(darkTheme = darkMode.value) {
                GuestExperience({ signIns++ }, darkMode.value, { darkMode.value = it })
            }
        }
        compose.onNodeWithContentDescription("Alerts").performClick()
        compose.onNodeWithText("Sign in to view your sent alerts and response updates.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Browsing as a guest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dark theme").performClick()
        compose.runOnIdle { assertEquals(false, darkMode.value) }
        compose.onNodeWithText("Sign in or create an account").performClick()
        compose.runOnIdle { assertEquals(1, signIns) }
    }

    @Test fun draggingGuestSosAsksForSignIn() {
        var signIns = 0
        compose.setContent {
            AGAPTheme { GuestExperience({ signIns++ }, true, {}) }
        }
        compose.onNodeWithText("DRAG", useUnmergedTree = true).performScrollTo().performTouchInput {
            swipe(center, center + Offset(0f, -220f), 600)
        }
        assertSignInPrompt()
        compose.onNodeWithText("Keep exploring").performClick()
        compose.runOnIdle { assertEquals(0, signIns) }
    }

    private fun assertSignInPrompt() {
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Sign in to continue").assertIsDisplayed() }.isSuccess
        }
    }
}
