package com.travelingsalesman.agap.ui.screens.guest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.travelingsalesman.agap.ui.components.AgapBottomBar
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.EmergencySosJoystick
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.screens.home.DetailedReportAction
import com.travelingsalesman.agap.data.local.TransmissionMode

/** Guest actions have no repository, identity, location, or emergency-send dependencies. */
@Composable
fun GuestExperience(
    onSignIn: () -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    var signInReason by rememberSaveable { mutableStateOf<String?>(null) }
    fun openSignIn(reason: String) { signInReason = reason }

    Scaffold(
        modifier = modifier,
        topBar = {
            AgapTopBar(
                title = "Guest mode",
                onProfileClick = { navController.navigate(AgapDestinations.PROFILE_ROUTE) { launchSingleTop = true } },
                onNotificationClick = { openSignIn("view your notifications") }
            )
        },
        bottomBar = {
            AgapBottomBar(
                currentRoute = entry?.destination?.route,
                userRole = "GUEST",
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(AgapDestinations.HOME_ROUTE) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSosClick = {
                    navController.navigate(AgapDestinations.HOME_ROUTE) {
                        popUpTo(AgapDestinations.HOME_ROUTE)
                        launchSingleTop = true
                    }
                }
            )
        }
    ) { padding ->
        NavHost(navController, startDestination = AgapDestinations.HOME_ROUTE, modifier = Modifier.padding(padding)) {
            composable(AgapDestinations.HOME_ROUTE) {
                GuestHome(onRequireSignIn = ::openSignIn, onSignIn = onSignIn)
            }
            composable(AgapDestinations.CITIZEN_MAP_ROUTE) {
                Box(Modifier.fillMaxSize()) {
                    OsmdroidMapView(
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Guest map" },
                        showIncidents = false,
                        showResponders = false,
                        isDarkTheme = isDarkMode
                    )
                    Card(Modifier.fillMaxWidth().padding(AgapSpacing.lg)) {
                        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                            Text("Explore the map", style = MaterialTheme.typography.titleMedium)
                            Text("Browse available map tiles. Sign in to use your location and track your reports.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            composable(AgapDestinations.SENT_MESSAGES_ROUTE) {
                GuestAccountPrompt("Your alerts", "Sign in to view your sent alerts and response updates.", onSignIn)
            }
            composable(AgapDestinations.PROFILE_ROUTE) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AgapSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
                ) {
                    GuestAccountPrompt(
                        "Browsing as a guest",
                        "Sign in or create an account to manage your profile, emergency contacts, and reports.",
                        onSignIn,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dark theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onDarkModeChange,
                            modifier = Modifier.semantics { contentDescription = "Dark theme" }
                        )
                    }
                }
            }
        }
    }

    signInReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { signInReason = null },
            title = { Text("Sign in to continue") },
            text = { Text("Sign in or create an account to $reason. Guest mode does not send alerts or reports.") },
            confirmButton = {
                TextButton(onClick = { signInReason = null; onSignIn() }) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { signInReason = null }) { Text("Keep exploring") }
            }
        )
    }
}

@Composable
private fun GuestHome(onRequireSignIn: (String) -> Unit, onSignIn: () -> Unit) {
    var selectedType by rememberSaveable { mutableStateOf("MEDICAL") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AgapSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                Text("Welcome, guest", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Help is within reach.", style = MaterialTheme.typography.headlineLarge)
                Text("Explore your community's safety network.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSignIn) { Text("Sign in or create an account") }
        }
        EmergencySosJoystick(
            emergencyTypes = defaultEmergencyTypeOptions(),
            selectedTypeCode = selectedType,
            onTypeSelected = { selectedType = it },
            onEmergencyTriggered = { onRequireSignIn("send an emergency alert") },
            isLoading = false,
            hapticsEnabled = false,
            isPreview = true
        )
        DetailedReportAction(onClick = { onRequireSignIn("report an incident") }, isOnline = false,
            transmissionMode = TransmissionMode.AUTO)
        Text("Guest mode does not send alerts or reports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuestAccountPrompt(title: String, message: String, onSignIn: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier.padding(AgapSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onSignIn) { Text("Sign in or create an account") }
    }
}
