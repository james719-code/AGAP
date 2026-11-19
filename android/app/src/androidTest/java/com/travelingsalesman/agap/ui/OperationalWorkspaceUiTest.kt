package com.travelingsalesman.agap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.components.OperationalMetric
import com.travelingsalesman.agap.ui.components.OperationalMetrics
import com.travelingsalesman.agap.ui.components.OperationalWorkspaceHeader
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies the production dashboard components at narrow widths and enlarged type. */
class OperationalWorkspaceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun adminHeaderAndMetricsRemainReadableAtDoubleTextSize() = checkWorkspace(admin = true)
    @Test fun responderHeaderAndMetricsRemainReadableAtDoubleTextSize() = checkWorkspace(admin = false)

    private fun checkWorkspace(admin: Boolean) {
        var mapOpens = 0
        val title = if (admin) "Legazpi command center" else "TEST RESCUE 1"
        val firstLabel = if (admin) "Open" else "Assigned"
        val secondLabel = if (admin) "Dispatched" else "Distance"
        val thirdLabel = if (admin) "Units" else "Est. arrival"
        val mapLabel = if (admin) "Open incident map" else "Open field map"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AGAPTheme(darkTheme = admin) {
                    Column(
                        Modifier.widthIn(max = 320.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AgapSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                    ) {
                        OperationalWorkspaceHeader(title, "Local review account", if (admin) "Administrator" else "Field responder",
                            Icons.Outlined.Shield, isOnline = false, mapAction = mapLabel, onOpenMap = { mapOpens++ })
                        OperationalMetrics(listOf(
                            OperationalMetric(firstLabel, "2", Icons.Outlined.WarningAmber, MaterialTheme.colorScheme.primary),
                            OperationalMetric(secondLabel, "10.3 km", Icons.Outlined.NearMe, AgapStatus.info),
                            OperationalMetric(thirdLabel, "12 min", Icons.Outlined.AccessTime, AgapStatus.success)
                        ))
                    }
                }
            }
        }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Offline").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(mapLabel).performScrollTo().assertIsDisplayed().performClick()
        listOf(firstLabel, secondLabel, thirdLabel, "10.3 km", "12 min").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(1, mapOpens) }
    }
}
