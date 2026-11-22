package com.travelingsalesman.agap.ui.screens.guest

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.ui.components.AgapBottomBar
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.EmergencySosJoystick
import com.travelingsalesman.agap.ui.components.defaultEmergencyTypeOptions
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.screens.home.DetailedReportAction
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.BrandRed

/**
 * Guest mode follows "value before sign-up": a guest can call a hotline,
 * practise the SOS gesture and read offline safety guides without an account.
 * Nothing here touches repositories, identity, location, or emergency sending.
 */
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
                onNotificationClick = { openSignIn("get alerts about incidents near you") }
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
                GuestMap(isDarkMode = isDarkMode, onSignIn = onSignIn)
            }
            composable(AgapDestinations.SENT_MESSAGES_ROUTE) {
                GuestLockedScreen(
                    icon = Icons.Outlined.Campaign,
                    title = "Your alerts will appear here",
                    message = "After you sign in, every SOS and report you send shows up here with live updates from responders.",
                    benefits = listOf(
                        Icons.Outlined.History to "See each alert's status, from received to resolved",
                        Icons.Outlined.LocationOn to "Know when a responder is on the way",
                        Icons.Outlined.Bluetooth to "Check alerts that were relayed phone to phone"
                    ),
                    onSignIn = onSignIn
                )
            }
            composable(AgapDestinations.PROFILE_ROUTE) {
                GuestProfile(isDarkMode = isDarkMode, onDarkModeChange = onDarkModeChange, onSignIn = onSignIn)
            }
        }
    }

    signInReason?.let { reason ->
        AlertDialog(
            onDismissRequest = { signInReason = null },
            title = { Text("Sign in to continue") },
            text = { Text("Sign in or create a free account to $reason. Guest mode does not send alerts or reports.") },
            confirmButton = {
                TextButton(onClick = { signInReason = null; onSignIn() }) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { signInReason = null }) { Text("Keep exploring") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestHome(onRequireSignIn: (String) -> Unit, onSignIn: () -> Unit) {
    val emergencyTypes = remember { defaultEmergencyTypeOptions() }
    var selectedType by rememberSaveable { mutableStateOf("MEDICAL") }
    var practicedType by rememberSaveable { mutableStateOf<String?>(null) }
    var openGuide by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.section)
        ) {
            // A real emergency comes first: hotlines work without an account.
            Column(Modifier.padding(horizontal = AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                SectionHeader("In an emergency", "These open your phone's dialer. No account needed.")
                HotlineRow()
            }

            WelcomeCard(onSignIn = onSignIn, modifier = Modifier.padding(horizontal = AgapSpacing.lg))

            Box(Modifier.padding(horizontal = AgapSpacing.lg)) {
                EmergencySosJoystick(
                    emergencyTypes = emergencyTypes,
                    selectedTypeCode = selectedType,
                    onTypeSelected = { selectedType = it },
                    onEmergencyTriggered = { code -> practicedType = code },
                    isLoading = false,
                    hapticsEnabled = false,
                    isPreview = true
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                SectionHeader(
                    "Be ready",
                    "Quick steps for hazards common across Bicol. Works offline.",
                    modifier = Modifier.padding(horizontal = AgapSpacing.lg)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AgapSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                ) {
                    items(bicolSafetyGuides, key = { it.title }) { guide ->
                        GuideCard(guide, onClick = { openGuide = guide.title })
                    }
                }
            }

            DetailedReportAction(
                onClick = { onRequireSignIn("report an incident") },
                isOnline = false,
                transmissionMode = TransmissionMode.AUTO,
                isPreview = true,
                modifier = Modifier.padding(horizontal = AgapSpacing.lg)
            )
        }
    }

    practicedType?.let { code ->
        val label = emergencyTypes.firstOrNull { it.code == code }?.displayName ?: "emergency"
        AlertDialog(
            onDismissRequest = { practicedType = null },
            icon = { Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("That's how you send an SOS") },
            text = {
                Text(
                    "Nothing was sent. With an account, a $label SOS and your location would now go to " +
                        "responders, with 5 seconds to cancel. It still gets through without signal by " +
                        "hopping between nearby phones."
                )
            },
            confirmButton = {
                TextButton(onClick = { practicedType = null; onSignIn() }) { Text("Set up my account") }
            },
            dismissButton = {
                TextButton(onClick = { practicedType = null }) { Text("Practice again") }
            }
        )
    }

    bicolSafetyGuides.firstOrNull { it.title == openGuide }?.let { guide ->
        ModalBottomSheet(
            onDismissRequest = { openGuide = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            GuideSheet(guide)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() }
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HotlineRow() {
    val context = LocalContext.current
    fun dial(number: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
        // 911 is the dominant control: filled, larger share of the row.
        Surface(
            onClick = { dial("911") },
            modifier = Modifier.weight(1.4f).heightIn(min = 96.dp)
                .semantics { contentDescription = "Call 911, national emergency hotline" },
            shape = AgapShape.xl,
            color = BrandRed,
            contentColor = Color.White
        ) {
            Row(
                Modifier.padding(AgapSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                Box(
                    Modifier.size(44.dp).background(Color.White.copy(alpha = 0.18f), AgapShape.circle),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Call, null, modifier = Modifier.size(22.dp)) }
                Column {
                    Text("911", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                    Text("National emergency", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Surface(
            onClick = { dial("143") },
            modifier = Modifier.weight(1f).heightIn(min = 96.dp)
                .semantics { contentDescription = "Call 143, Philippine Red Cross" },
            shape = AgapShape.xl,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("143", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black))
                }
                Text("Red Cross", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomeCard(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    // Calm surface on purpose: red stays reserved for the emergency actions above.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AgapShape.xxl,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.xl), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Text(
                "You're exploring as a guest",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                "Get help even when there's no signal",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                WelcomeBenefit(Icons.Outlined.Bluetooth, "Your SOS hops phone to phone when the network is down")
                WelcomeBenefit(Icons.Outlined.LocationOn, "Responders see exactly where you are")
                WelcomeBenefit(Icons.Outlined.History, "Track every alert until help arrives")
            }
            Spacer(Modifier.height(AgapSpacing.xs))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = AgapShape.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Create a free account", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                "Already registered? Sign in from the same screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun WelcomeBenefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), AgapShape.circle),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GuideCard(guide: SafetyGuide, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(164.dp).height(156.dp),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
            Box(
                Modifier.size(44.dp).background(guide.accent.copy(alpha = 0.16f), AgapShape.md),
                contentAlignment = Alignment.Center
            ) { Icon(guide.icon, null, tint = guide.accent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.weight(1f))
            Text(guide.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                guide.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GuideSheet(guide: SafetyGuide) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgapSpacing.xxl)
            .padding(bottom = AgapSpacing.xxl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
            Box(
                Modifier.size(52.dp).background(guide.accent.copy(alpha = 0.16f), AgapShape.lg),
                contentAlignment = Alignment.Center
            ) { Icon(guide.icon, null, tint = guide.accent, modifier = Modifier.size(28.dp)) }
            Column {
                Text(guide.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() })
                Text(guide.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        guide.steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Box(
                    Modifier.size(28.dp).background(guide.accent, AgapShape.circle),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 2.dp))
            }
        }
        Text(
            "General guidance. Always follow instructions from your LGU, PAGASA, PHIVOLCS, and responders on scene.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuestMap(isDarkMode: Boolean, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Guest map" },
            showIncidents = false,
            showResponders = false,
            isDarkTheme = isDarkMode
        )
        Surface(
            onClick = onSignIn,
            // Bottom placement keeps the map attribution at the top visible.
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(AgapSpacing.lg),
            shape = AgapShape.pill,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(start = AgapSpacing.lg, end = AgapSpacing.sm, top = AgapSpacing.sm, bottom = AgapSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                Icon(Icons.Outlined.Map, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Browsing the map", style = MaterialTheme.typography.titleSmall)
                    Text("Sign in to see where you are", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = AgapSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun GuestLockedScreen(
    icon: ImageVector,
    title: String,
    message: String,
    benefits: List<Pair<ImageVector, String>>,
    onSignIn: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(AgapSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(AgapSpacing.xxl))
            Box(
                Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), AgapShape.circle),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) }
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() })
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = AgapShape.xl,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(AgapSpacing.lg), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                    benefits.forEach { (benefitIcon, text) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                            Icon(benefitIcon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            SignInButton(onSignIn)
        }
    }
}

@Composable
private fun SignInButton(onSignIn: () -> Unit) {
    Button(
        onClick = onSignIn,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = AgapShape.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Icon(Icons.AutoMirrored.Outlined.Login, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AgapSpacing.sm))
        Text("Sign in or create an account", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun GuestProfile(isDarkMode: Boolean, onDarkModeChange: (Boolean) -> Unit, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
        ) {
            Surface(
                shape = AgapShape.xxl,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(AgapSpacing.xl), verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.lg)) {
                        Box(
                            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, AgapShape.circle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                            Text("Guest", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            Text("Not signed in", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Create an account to add emergency contacts and medical details. Responders see them the moment your SOS arrives.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SignInButton(onSignIn)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                Text("Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AgapSpacing.xs))
                Surface(
                    shape = AgapShape.xl,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                    ) {
                        Box(
                            Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, AgapShape.md),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.DarkMode, null, modifier = Modifier.size(20.dp)) }
                        Text("Dark theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onDarkModeChange,
                            modifier = Modifier.semantics { contentDescription = "Dark theme" }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                Text("Emergency hotlines", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AgapSpacing.xs))
                HotlineRow()
            }
        }
    }
}
