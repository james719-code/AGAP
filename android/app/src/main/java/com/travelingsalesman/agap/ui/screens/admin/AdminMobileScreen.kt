package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.Brush
import java.util.Calendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CLOSED_STATUSES = setOf("RESOLVED", "CANCELLED", "FALSE_ALARM")
private val DISPATCHED_STATUSES = setOf("DISPATCHED", "EN_ROUTE", "ON_SCENE")

private const val TRIAGE_CARD_PREVIEW_LIMIT = 3
private const val RESPONDER_CARD_PREVIEW_LIMIT = 3
private const val MAP_INCIDENT_MARKER_LIMIT = 40
private const val MAP_RESPONDER_MARKER_LIMIT = 40
private const val DISPATCH_DIALOG_LIMIT = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMobileScreen(
    onNavigateToProfile: () -> Unit,
    onOpenFullMap: () -> Unit,
    onNavigateToIncidents: (() -> Unit)? = null,
    onNavigateToIncidentDetail: ((String) -> Unit)? = null,
    onNavigateToManagement: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AdminMobileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Poll only while this command screen is actually visible.
    // WebSocket/Room remains the primary instant-update path.
    DisposableEffect(viewModel) {
        viewModel.startAutoRefresh()

        onDispose {
            viewModel.stopAutoRefresh()
        }
    }

    val activeIncidents = remember(uiState.incidents) {
        uiState.incidents
            .filter { it.status.uppercase() !in CLOSED_STATUSES }
            .sortedWith(
                compareBy<IncidentEntity> { priorityOf(it.status) }
                    .thenByDescending { it.timestamp }
            )
    }
    val dispatchedCount = remember(uiState.incidents) {
        uiState.incidents.count { it.status.uppercase() in DISPATCHED_STATUSES }
    }
    val responderEntities = remember(uiState.responders) {
        uiState.responders.map { it.toEntity() }
    }

    /*
     * Keep expensive dashboard layers bounded.
     * Full incident history is available in the Incidents tab.
     */
    val triagePreview =
        remember(activeIncidents) {
            activeIncidents.take(
                TRIAGE_CARD_PREVIEW_LIMIT
            )
        }

    val responderPreview =
        remember(uiState.responders) {
            uiState.responders.take(
                RESPONDER_CARD_PREVIEW_LIMIT
            )
        }

    val mapIncidentPreview =
        remember(activeIncidents) {
            activeIncidents
                .asSequence()
                .filter {
                    it.latitude != 0.0 ||
                            it.longitude != 0.0
                }
                .take(
                    MAP_INCIDENT_MARKER_LIMIT
                )
                .toList()
        }

    val mapResponderPreview =
        remember(responderEntities) {
            responderEntities
                .asSequence()
                .filter {
                    it.currentLat != 0.0 &&
                            it.currentLon != 0.0
                }
                .take(
                    MAP_RESPONDER_MARKER_LIMIT
                )
                .toList()
        }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AgapTopBar(
                title = "AGAP COMMAND",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState,
                onNotificationClick = { onNavigateToNotifications?.invoke() }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = AgapSpacing.lg)
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AgapSpacing.lg,
                    end = AgapSpacing.lg,
                    top = AgapSpacing.md,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
            ) {
                item {
                    CommandHeader(
                        username = uiState.username,
                        isOnline = uiState.isOnline,
                        pendingCount = uiState.pendingPacketCount,
                        nearbyMeshCount = uiState.bleMeshState.nearbyDeviceCount,
                        autoRefreshActive = uiState.autoRefreshActive,
                        lastDataRefreshAt = uiState.lastDataRefreshAt
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "OPEN",
                            value = activeIncidents.size,
                            icon = Icons.Default.WarningAmber,
                            accent = RedPrimary
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "DISPATCHED",
                            value = dispatchedCount,
                            icon = Icons.Default.Shield,
                            accent = AmberWarning
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            label = "UNITS",
                            value = uiState.responders.size,
                            icon = Icons.Default.People,
                            accent = BlueMedical
                        )
                    }
                }

                item {
                    MapSection(
                        activeIncidents = mapIncidentPreview,
                        responders = mapResponderPreview,
                        userLocation = uiState.userLocation,
                        isOnline = uiState.isOnline,
                        onOpenFullMap = onOpenFullMap
                    )
                }

                item {
                    SectionTitle(
                        eyebrow = "TRIAGE QUEUE",
                        title = "Immediate action",
                        subtitle = "Unresolved incidents requiring operator attention",
                        count = activeIncidents.size,
                        accent = RedPrimary
                    )
                }

                if (activeIncidents.isEmpty()) {
                    item {
                        EmptyCard(
                            title = "Triage queue is clear",
                            message = "No active emergency requests are waiting for action."
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            triagePreview.forEach { incident ->
                                IncidentListItem(
                                    incident = incident,
                                    onClick = {
                                        onNavigateToIncidentDetail?.invoke(incident.incidentId)
                                    }
                                )
                            }
                        }
                    }

                    if (
                        activeIncidents.size >
                        triagePreview.size
                    ) {
                        item(
                            key =
                                "triage-preview-limit",
                            contentType =
                                "preview_notice"
                        ) {
                            PerformancePreviewNotice(
                                buttonLabel = "VIEW ALL ${activeIncidents.size} INCIDENTS",
                                accentColor = RedPrimary,
                                onClick = {
                                    onNavigateToIncidents
                                        ?.invoke()
                                }
                            )
                        }
                    }
                }

                item {
                    SectionTitle(
                        eyebrow = "FIELD RESOURCES",
                        title = "Responder roster",
                        subtitle = "Operational units available to command",
                        count = uiState.responders.size,
                        accent = BlueMedical
                    )
                }

                if (uiState.responders.isEmpty()) {
                    item {
                        EmptyCard(
                            title = "No responder units loaded",
                            message = "Units will appear here after synchronization."
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            responderPreview.forEach { responder ->
                                ResponderCard(responder)
                            }
                        }
                    }

                    if (
                        uiState.responders.size >
                        responderPreview.size
                    ) {
                        item(
                            key =
                                "responder-preview-limit",
                            contentType =
                                "preview_notice"
                        ) {
                            PerformancePreviewNotice(
                                buttonLabel = "VIEW ALL ${uiState.responders.size} RESPONDERS",
                                accentColor = BlueMedical,
                                onClick = {
                                    onNavigateToManagement
                                        ?.invoke()
                                }
                            )
                        }
                    }
                }
            }
        }

        uiState.selectedIncidentIdForDispatch?.let { incidentId ->
            DispatchDialog(
                incidentId = incidentId,
                responders = uiState.responders,
                onDismiss = viewModel::closeDispatchSheet,
                onDispatch = { unitId -> viewModel.dispatchResponder(incidentId, unitId) }
            )
        }
    }
}

private data class TimeGreetingInfo(
    val greeting: String,
    val subText: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
private fun rememberTimeGreeting(username: String?): TimeGreetingInfo {
    val now = remember { Calendar.getInstance() }
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val timeFormatted = remember { SimpleDateFormat("h:mm a • EEE, MMM d", Locale.getDefault()).format(now.time) }
    val name = username?.takeIf { it.isNotBlank() } ?: "Admin"

    return remember(hour, name) {
        when (hour) {
            in 5..11 -> TimeGreetingInfo(
                greeting = "Good morning, $name",
                subText = "$timeFormatted • Legazpi Command Center",
                icon = Icons.Default.WbSunny,
                accentColor = Color(0xFFFFB300)
            )
            in 12..16 -> TimeGreetingInfo(
                greeting = "Good afternoon, $name",
                subText = "$timeFormatted • Legazpi Command Center",
                icon = Icons.Default.WbSunny,
                accentColor = Color(0xFF2196F3)
            )
            in 17..20 -> TimeGreetingInfo(
                greeting = "Good evening, $name",
                subText = "$timeFormatted • Legazpi Command Center",
                icon = Icons.Default.WbTwilight,
                accentColor = Color(0xFFFF7043)
            )
            else -> TimeGreetingInfo(
                greeting = "Good evening, $name",
                subText = "$timeFormatted • Legazpi Command Center",
                icon = Icons.Default.NightsStay,
                accentColor = Color(0xFF90CAF9)
            )
        }
    }
}

@Composable
private fun CommandHeader(
    username: String?,
    isOnline: Boolean,
    pendingCount: Int,
    nearbyMeshCount: Int,
    autoRefreshActive: Boolean,
    lastDataRefreshAt: Long?
) {
    val greetingInfo = rememberTimeGreeting(username)
    val accent = if (isOnline) GreenOnline else AmberWarning
    val accountTitle = username?.takeIf { it.isNotBlank() }
        ?.let { "Operator $it • Legazpi Command" }
        ?: "Legazpi Emergency Command Center"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glowing Celestial Badge (Sun/Moon)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(greetingInfo.accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = greetingInfo.icon,
                            contentDescription = null,
                            tint = greetingInfo.accentColor,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingInfo.greeting,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = accountTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        shape = AgapShape.pill,
                        color = RedPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.30f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(RedPrimary)
                            )
                            Text("ADMIN", color = RedPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    thickness = 1.dp
                )

                // Sub-Row: Formatted Time & Status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = greetingInfo.subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
}

@Composable
private fun MetricCard(
    label: String,
    value: Int,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(92.dp)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AgapSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value.toString(),
                color = accent,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(1.dp, accent.copy(alpha = 0.50f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PerformancePreviewNotice(
    buttonLabel: String,
    accentColor: Color = RedPrimary,
    onClick: (() -> Unit)? = null
) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MapSection(
    activeIncidents: List<IncidentEntity>,
    responders: List<ResponderUnitEntity>,
    userLocation: Location?,
    isOnline: Boolean,
    onOpenFullMap: () -> Unit
) {
    val isDarkMapTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapActionColor = MaterialTheme.colorScheme.primary

    Column(
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        Text(
            text = "FIELD INCIDENT MAP",
            color = mapActionColor,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.8.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(AgapShape.xl)
                .background(
                    MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color =
                        MaterialTheme.colorScheme
                            .surfaceVariant,
                    shape = AgapShape.xl
                )
        ) {
            OsmdroidMapView(
                modifier = Modifier.fillMaxSize(),
                userLocation = userLocation,
                incidents = activeIncidents,
                responders = responders,
                showIncidents = true,
                showResponders = true,
                zoomLevel = 13.5,
                /*
                 * Same visual theme as the surrounding AGAP dashboard.
                 *
                 * Light AGAP theme:
                 *   cached raster tiles render normally.
                 *
                 * Dark AGAP theme:
                 *   OsmdroidMapView applies its dark raster color matrix.
                 *
                 * This is visual only; it does not duplicate or replace cached tiles.
                 */
                isDarkTheme = isDarkMapTheme,

                /*
                 * Cache-first behavior:
                 * - render previously cached tiles first
                 * - if a tile has never been cached and internet is available,
                 *   fetch it once and persist it
                 * - normal refreshes do not replace existing cached tiles
                 */
                isOnline = isOnline
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AgapSpacing.sm),
                shape = AgapShape.pill,
                color =
                    MaterialTheme.colorScheme
                        .surface.copy(
                            alpha = 0.94f
                        ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme
                        .surfaceVariant
                ),
                shadowElevation = 6.dp,
                onClick = onOpenFullMap
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 6.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "EXPAND",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(AgapSpacing.sm),
                shape = AgapShape.pill,
                color =
                    MaterialTheme.colorScheme
                        .surface.copy(
                            alpha = 0.94f
                        ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme
                        .surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) GreenOnline else AmberWarning)
                    )
                    Text(
                        if (isOnline) "CACHE-FIRST • LIVE DATA" else "LOCAL MAP • OFFLINE DATA",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    eyebrow: String,
    title: String,
    subtitle: String,
    count: Int,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            eyebrow,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            SmallPill(count.toString(), accent)
        }
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun IncidentListItem(
    incident: IncidentEntity,
    onClick: () -> Unit
) {
    val border = MaterialTheme.colorScheme.surfaceVariant
    val categoryColor = when {
        incident.emergencyType.contains("MEDICAL", ignoreCase = true) -> BlueMedical
        incident.emergencyType.contains("FIRE", ignoreCase = true) -> Color(0xFFFF5722)
        incident.emergencyType.contains("CRIME", ignoreCase = true) || incident.emergencyType.contains("SECURITY", ignoreCase = true) -> Color(0xFF8E24AA)
        incident.emergencyType.contains("ACCIDENT", ignoreCase = true) || incident.emergencyType.contains("TRAFFIC", ignoreCase = true) -> Color(0xFFFFB300)
        incident.emergencyType.contains("DISASTER", ignoreCase = true) -> Color(0xFF4CAF50)
        else -> RedPrimary
    }
    val displayId = if (incident.incidentId.length > 10) "#${incident.incidentId.take(8).uppercase()}" else "#${incident.incidentId}"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = AgapShape.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emergency Type Icon Indicator Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(AgapShape.sm)
                    .background(categoryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Incident Telemetry Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${incident.emergencyType.uppercase()} $displayId",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "${incident.channel} • ${formatTime(incident.timestamp)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(
                    value.replace('_', ' '),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = AgapShape.md,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ResponderCard(responder: ResponderUnitDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, AgapShape.lg),
        shape = AgapShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AgapShape.md)
                    .background(BlueMedical.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Group, null, tint = BlueMedical, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(AgapSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    responder.unitCallsign,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Agency ${responder.agencyId} • ${compactId(responder.targetId)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(AgapSpacing.sm))
            StatusBadge(responder.status)
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GreenOnline.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = GreenOnline, modifier = Modifier.size(22.dp))
            }
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SmallPill(text: String, accent: Color) {
    Surface(
        shape = AgapShape.pill,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = accent,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun DispatchDialog(
    incidentId: String,
    responders: List<ResponderUnitDto>,
    onDismiss: () -> Unit,
    onDispatch: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text("Dispatch responder", style = MaterialTheme.typography.titleLarge)
                Text(
                    "INCIDENT #$incidentId",
                    color = AmberWarning,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        text = {
            if (responders.isEmpty()) {
                Text(
                    "No responder units are currently available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    item {
                        Text(
                            "Choose the unit to assign:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(
                        items =
                            responders.take(
                                DISPATCH_DIALOG_LIMIT
                            ),
                        key = {
                            it.id
                        },
                        contentType = {
                            "dispatch_responder"
                        }
                    ) { responder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, AgapShape.md),
                            onClick = { onDispatch(responder.id) },
                            shape = AgapShape.md,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AgapSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(AgapShape.sm)
                                        .background(AmberWarning.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NearMe, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        responder.unitCallsign,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Agency ${responder.agencyId}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                StatusBadge(responder.status)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun ResponderUnitDto.toEntity() = ResponderUnitEntity(
    id = id,
    agencyId = agencyId,
    targetId = targetId,
    unitCallsign = unitCallsign,
    deviceId = deviceId,
    status = status,
    currentLat = currentLat,
    currentLon = currentLon,
    lastSeenAt = lastSeenAt
)

private fun formatClockTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}

private fun priorityOf(status: String): Int = when (status.uppercase()) {
    "RECEIVED", "ACKNOWLEDGED", "CRITICAL" -> 0
    "DISPATCHED" -> 1
    "EN_ROUTE" -> 2
    "ON_SCENE" -> 3
    else -> 4
}

private fun statusAccent(status: String): Color = when (status.uppercase()) {
    "RECEIVED", "ACKNOWLEDGED", "CRITICAL" -> RedPrimary
    "DISPATCHED", "EN_ROUTE", "ON_SCENE" -> AmberWarning
    "RESOLVED" -> GreenOnline
    else -> BlueMedical
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Time unavailable"
    return SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun compactId(value: String): String {
    if (value.isBlank()) return "NO TARGET"
    return if (value.length <= 14) value else "${value.take(7)}…${value.takeLast(5)}"
}
