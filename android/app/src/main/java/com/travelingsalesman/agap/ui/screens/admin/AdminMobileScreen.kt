@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbTwilight
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.travelingsalesman.agap.ui.components.OperationalWorkspaceHeader
import com.travelingsalesman.agap.ui.components.OperationalMetric
import com.travelingsalesman.agap.ui.components.OperationalMetrics
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.contentColorForAccent
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.EmergencyType
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
private const val UNASSIGNED_TRIAGE_STALE_AFTER_MILLIS = 10L * 60L * 1_000L
private const val PROGRESSING_TRIAGE_STALE_AFTER_MILLIS = 60L * 60L * 1_000L

private data class TriageAgePresentation(
    val label: String,
    val isStale: Boolean,
    val accessibilityLabel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMobileScreen(
    onNavigateToProfile: () -> Unit,
    onOpenFullMap: () -> Unit,
    onNavigateToIncidents: (() -> Unit)? = null,
    onNavigateToIncidentDetail: ((String) -> Unit)? = null,
    onNavigateToManagement: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToMessages: (() -> Unit)? = null,
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
                title = "Command center",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState,
                onNotificationClick = { onNavigateToNotifications?.invoke() },
                onMessageClick = { onNavigateToMessages?.invoke() }
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
                    bottom = AgapSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
            ) {
                item {
                    OperationalWorkspaceHeader(
                        title = "Command center",
                        subtitle = uiState.username?.takeIf { it.isNotBlank() }?.let { "Camarines Sur · $it" } ?: "Camarines Sur emergency operations",
                        role = "Administrator",
                        icon = Icons.Outlined.Shield,
                        isOnline = uiState.isOnline,
                        mapAction = "Open incident map",
                        onOpenMap = onOpenFullMap,
                        statusDetail = if (uiState.pendingPacketCount > 0) "${uiState.pendingPacketCount} updates waiting to send" else null
                    )
                }

                item {
                    OperationalMetrics(listOf(
                        OperationalMetric("Open", activeIncidents.size.toString(), Icons.Outlined.WarningAmber, MaterialTheme.colorScheme.primary),
                        OperationalMetric("Dispatched", dispatchedCount.toString(), Icons.Outlined.Shield, AgapStatus.warning),
                        OperationalMetric("Units", uiState.responders.size.toString(), Icons.Outlined.People, AgapStatus.info)
                    ))
                }

                item {
                    SectionTitle(
                        eyebrow = "Triage queue",
                        title = "Immediate action",
                        subtitle = "Unresolved incidents requiring operator attention",
                        count = activeIncidents.size,
                        accent = MaterialTheme.colorScheme.primary
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
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                            triagePreview.forEach { incident ->
                                IncidentListItem(
                                    incident = incident,
                                    onClick = {
                                        onNavigateToIncidentDetail?.invoke(incident.incidentId)
                                    },
                                    onContextualAction = {
                                        if (incident.requiresUnitAssignment()) {
                                            viewModel.openDispatchSheet(incident.incidentId)
                                        } else {
                                            onNavigateToIncidentDetail?.invoke(incident.incidentId)
                                        }
                                    },
                                    isContextualActionEnabled =
                                        incident.requiresUnitAssignment() ||
                                            onNavigateToIncidentDetail != null
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
                        eyebrow = "Field resources",
                        title = "Responder roster",
                        subtitle = "Operational units available to command",
                        count = uiState.responders.size,
                        accent = AgapStatus.info
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
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
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
        contentPadding = PaddingValues(
            vertical = AgapSpacing.md,
            horizontal = AgapSpacing.lg
        )
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
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
            text = "Field incident map",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Text(if (isOnline) "Cached map · online connection" else "Cached map · offline data",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

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
                    .padding(AgapSpacing.sm)
                    .heightIn(min = 48.dp),
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
                        horizontal = AgapSpacing.sm,
                        vertical = AgapSpacing.sm
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Expand map",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
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
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
        Text(
            eyebrow,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
    onClick: () -> Unit,
    onContextualAction: () -> Unit,
    isContextualActionEnabled: Boolean
) {
    val border = MaterialTheme.colorScheme.surfaceVariant
    val emergencyType = EmergencyType.fromCode(incident.emergencyType)
    val categoryColor = emergencyType.color
    val displayId = if (incident.incidentId.length > 10) "#${incident.incidentId.take(8).uppercase()}" else "#${incident.incidentId}"
    val requiresUnitAssignment = incident.requiresUnitAssignment()
    val agePresentation = triageAgePresentation(
        timestamp = incident.timestamp,
        requiresUnitAssignment = requiresUnitAssignment
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Open details for ${emergencyType.label} incident $displayId",
                role = Role.Button,
                onClick = onClick
            ),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AgapSpacing.md,
                    vertical = AgapSpacing.md
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emergency Type Icon Indicator Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AgapShape.sm)
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = emergencyType.icon,
                    contentDescription = emergencyType.label,
                    tint = contentColorForAccent(categoryColor),
                    modifier = Modifier.size(AgapSpacing.xxl)
                )
            }

            Spacer(Modifier.width(AgapSpacing.md))

            // Incident Telemetry Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${emergencyType.label} $displayId",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Spacer(Modifier.height(AgapSpacing.xs))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
                ) {
                    StatusBadge(status = incident.status)
                    TriageAgeBadge(agePresentation)
                }

                Spacer(Modifier.height(AgapSpacing.xs))

                Text(
                    text = "${incident.channel} • ${formatTime(incident.timestamp)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(AgapSpacing.sm))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            }

            HorizontalDivider(color = border)

            TriageContextualAction(
                requiresUnitAssignment = requiresUnitAssignment,
                incidentLabel = "${emergencyType.label} incident $displayId",
                enabled = isContextualActionEnabled,
                onClick = onContextualAction,
                modifier = Modifier.padding(AgapSpacing.sm)
            )
        }
    }
}

@Composable
private fun TriageAgeBadge(agePresentation: TriageAgePresentation) {
    val accent =
        if (agePresentation.isStale) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = Modifier.semantics {
            contentDescription = agePresentation.accessibilityLabel
        },
        shape = AgapShape.pill,
        color = accent.copy(alpha = if (agePresentation.isStale) 0.12f else 0.08f),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = if (agePresentation.isStale) 0.36f else 0.20f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.sm,
                vertical = AgapSpacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Icon(
                imageVector =
                    if (agePresentation.isStale) {
                        Icons.Outlined.WarningAmber
                    } else {
                        Icons.Outlined.AccessTime
                    },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = agePresentation.label,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TriageContextualAction(
    requiresUnitAssignment: Boolean,
    incidentLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionLabel = if (requiresUnitAssignment) "Assign unit" else "Review incident"
    val actionDescription =
        if (requiresUnitAssignment) {
            "Assign a responder unit to $incidentLabel"
        } else {
            "Review $incidentLabel"
        }
    val actionModifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = actionDescription }

    if (requiresUnitAssignment) {
        Button(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled,
            shape = AgapShape.md,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.NearMe,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AgapSpacing.sm))
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled,
            shape = AgapShape.md,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AgapSpacing.sm))
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
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
            modifier = Modifier.padding(
                horizontal = AgapSpacing.sm,
                vertical = AgapSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(AgapSpacing.sm))
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
        contentPadding = PaddingValues(horizontal = AgapSpacing.sm)
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(AgapSpacing.sm))
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
                Icon(Icons.Outlined.Group, null, tint = BlueMedical, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(AgapSpacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Text(
                    responder.unitCallsign,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    "Agency ${responder.agencyId} • ${compactId(responder.targetId)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                StatusBadge(responder.status)
            }
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
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GreenOnline.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = GreenOnline, modifier = Modifier.size(22.dp))
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
            modifier = Modifier.padding(
                horizontal = AgapSpacing.sm,
                vertical = AgapSpacing.xs
            ),
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
                            modifier = Modifier.padding(bottom = AgapSpacing.xs)
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
                                    Icon(Icons.Outlined.NearMe, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(AgapSpacing.md))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                                    Text(
                                        responder.unitCallsign,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Agency ${responder.agencyId}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    StatusBadge(responder.status)
                                }
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

/**
 * A received incident needs a concrete unit before it can progress. Other active states
 * already have an operational workflow, so their command-card action is review instead.
 */
private fun IncidentEntity.requiresUnitAssignment(): Boolean =
    status.trim().uppercase(Locale.ROOT) == "RECEIVED" && dispatchedUnitId == null

private fun triageAgePresentation(
    timestamp: Long,
    requiresUnitAssignment: Boolean,
    nowMillis: Long = System.currentTimeMillis()
): TriageAgePresentation {
    if (timestamp <= 0L) {
        return TriageAgePresentation(
            label = "AGE UNKNOWN",
            isStale = false,
            accessibilityLabel = "Incident age is unavailable"
        )
    }

    val elapsedMillis = (nowMillis - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    val ageLabel =
        when {
            elapsedMillis < minute -> "JUST NOW"
            elapsedMillis < hour -> "${elapsedMillis / minute}M AGO"
            elapsedMillis < day -> "${elapsedMillis / hour}H AGO"
            else -> "${elapsedMillis / day}D AGO"
        }
    val staleAfter =
        if (requiresUnitAssignment) {
            UNASSIGNED_TRIAGE_STALE_AFTER_MILLIS
        } else {
            PROGRESSING_TRIAGE_STALE_AFTER_MILLIS
        }
    val isStale = elapsedMillis >= staleAfter
    val accessibilityContext =
        if (requiresUnitAssignment) {
            "Unassigned incident"
        } else {
            "Incident"
        }

    return TriageAgePresentation(
        label = if (isStale) "STALE · $ageLabel" else ageLabel,
        isStale = isStale,
        accessibilityLabel =
            if (isStale) {
                "$accessibilityContext is stale, reported $ageLabel"
            } else {
                "$accessibilityContext was reported $ageLabel"
            }
    )
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Time unavailable"
    return SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun compactId(value: String): String {
    if (value.isBlank()) return "NO TARGET"
    return if (value.length <= 14) value else "${value.take(7)}…${value.takeLast(5)}"
}
