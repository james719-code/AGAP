package com.travelingsalesman.agap.ui.screens.admin

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.core.routing.OfflineRouteResult
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.ui.map.MapCameraAction
import com.travelingsalesman.agap.ui.map.MapCameraCommand
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.EmergencyType
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import java.util.Locale
import kotlin.math.max

/*
 * THEME-AWARE COMMAND MAP
 *
 * Do not use hard-coded dark/light surfaces here.
 * These getters read the currently active AGAP MaterialTheme, so the map UI
 * follows whatever theme AGAP is actually displaying.
 *
 * This is better than using isSystemInDarkTheme() directly because AGAPTheme
 * can be explicitly switched independently of the phone's system appearance.
 */
private val MapPanel: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface.copy(
        alpha = 0.95f
    )

private val MapPanelStrong: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface.copy(
        alpha = 0.985f
    )

private val MapPanelSoft: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = 0.90f
    )

private val MapBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

private val MapText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

private val MapTextMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

private val MapBackdrop: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

private val LIVE_MAP_CLOSED_STATUSES =
    setOf(
        "RESOLVED",
        "CANCELLED",
        "FALSE_ALARM"
    )

private const val FULL_MAP_INCIDENT_LIMIT = 60
private const val FULL_MAP_RESPONDER_LIMIT = 60
private const val FULL_MAP_DISPATCH_LIMIT = 60

@Composable
fun AdminLiveMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToIncident: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminMobileViewModel
) {
    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState =
        remember { SnackbarHostState() }

    /*
     * Reflect the theme that AGAP is currently rendering.
     *
     * MaterialTheme is the source of truth. This means:
     * - AGAP dark theme  -> dark map tiles + dark command surfaces
     * - AGAP light theme -> normal/light map tiles + light command surfaces
     *
     * It also updates automatically if the active theme changes while this
     * destination is still composed.
     */
    val isDarkMapTheme =
        MaterialTheme.colorScheme.background
            .luminance() < 0.5f

    var showLegend by
    remember { mutableStateOf(false) }

    var showFilters by
    remember { mutableStateOf(false) }

    var showIncidents by
    remember { mutableStateOf(true) }

    var showResponders by
    remember { mutableStateOf(true) }

    var onlyOpen by
    remember { mutableStateOf(true) }

    /*
     * selectedIncident controls the bottom information card.
     * focusedIncident controls camera focus + route rendering.
     *
     * Keeping them separate prevents the map from suddenly zooming to the first
     * incident just because the screen opened.
     */
    var selectedIncidentId by
    remember { mutableStateOf<String?>(null) }

    var focusedIncidentId by
    remember { mutableStateOf<String?>(null) }

    var selectedResponderId by
    remember { mutableStateOf<Long?>(null) }

    /*
     * Full map is the primary workspace.
     * The selected-incident command deck can collapse into a compact status bar
     * so it does not permanently consume a large part of the map.
     */
    var incidentPanelExpanded by
    remember { mutableStateOf(true) }

    var cameraCommand by
    remember { mutableStateOf<MapCameraCommand?>(null) }

    var cameraCommandId by
    remember { mutableStateOf(0L) }

    DisposableEffect(viewModel) {
        viewModel.startAutoRefresh()

        onDispose {
            viewModel.stopAutoRefresh()
        }
    }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedbackMessage()
        }
    }

    val responderEntities =
        remember(uiState.responders) {
            uiState.responders
                .asSequence()
                .map { it.toMapEntity() }
                .filter {
                    it.currentLat != 0.0 &&
                            it.currentLon != 0.0
                }
                .take(FULL_MAP_RESPONDER_LIMIT)
                .toList()
        }

    val mapIncidents =
        remember(
            uiState.incidents,
            onlyOpen
        ) {
            uiState.incidents
                .asSequence()
                .filter {
                    it.latitude != 0.0 ||
                            it.longitude != 0.0
                }
                .filter {
                    !onlyOpen ||
                            it.status.uppercase() !in
                            LIVE_MAP_CLOSED_STATUSES
                }
                .sortedWith(
                    compareBy<IncidentEntity> {
                        mapPriority(it.status)
                    }.thenByDescending {
                        it.timestamp
                    }
                )
                .take(FULL_MAP_INCIDENT_LIMIT)
                .toList()
        }

    LaunchedEffect(mapIncidents) {
        val selectedStillExists =
            mapIncidents.any {
                it.incidentId ==
                        selectedIncidentId
            }

        if (!selectedStillExists) {
            selectedIncidentId =
                mapIncidents.firstOrNull()
                    ?.incidentId
        }

        val focusedStillExists =
            mapIncidents.any {
                it.incidentId ==
                        focusedIncidentId
            }

        if (!focusedStillExists) {
            focusedIncidentId = null
        }
    }

    val selectedIncident =
        remember(
            mapIncidents,
            selectedIncidentId
        ) {
            mapIncidents.firstOrNull {
                it.incidentId ==
                        selectedIncidentId
            }
        }

    val focusedIncident =
        remember(
            mapIncidents,
            focusedIncidentId
        ) {
            mapIncidents.firstOrNull {
                it.incidentId ==
                        focusedIncidentId
            }
        }

    val selectedResponder =
        remember(
            responderEntities,
            selectedResponderId
        ) {
            responderEntities.firstOrNull {
                it.id == selectedResponderId
            }
        }

    val dispatchRouteIncident =
        remember(mapIncidents, uiState.dispatchRouteIncidentId) {
            mapIncidents.firstOrNull {
                it.incidentId == uiState.dispatchRouteIncidentId
            }
        }

    val dispatchRouteResponder =
        remember(responderEntities, uiState.dispatchRouteResponderId) {
            responderEntities.firstOrNull {
                it.id == uiState.dispatchRouteResponderId
            }
        }

    val selectedIncidentIndex =
        remember(
            mapIncidents,
            selectedIncidentId
        ) {
            mapIncidents.indexOfFirst {
                it.incidentId ==
                        selectedIncidentId
            }
        }

    val selectedResponderIndex =
        remember(
            responderEntities,
            selectedResponderId
        ) {
            responderEntities.indexOfFirst {
                it.id ==
                        selectedResponderId
            }
        }

    fun issueCameraCommand(
        action: MapCameraAction,
        latitude: Double? = null,
        longitude: Double? = null,
        zoom: Double? = null
    ) {
        cameraCommandId += 1L

        cameraCommand =
            MapCameraCommand(
                id = cameraCommandId,
                action = action,
                latitude = latitude,
                longitude = longitude,
                zoom = zoom
            )
    }

    fun focusIncidentAt(index: Int) {
        if (mapIncidents.isEmpty()) return
        viewModel.clearCommandRoute()

        val normalized =
            ((index % mapIncidents.size) +
                    mapIncidents.size) %
                    mapIncidents.size

        val incident =
            mapIncidents[normalized]

        selectedIncidentId =
            incident.incidentId

        focusedIncidentId =
            incident.incidentId

        selectedResponderId = null

        issueCameraCommand(
            action =
                MapCameraAction.FOCUS_POINT,
            latitude =
                incident.latitude,
            longitude =
                incident.longitude,
            zoom = 16.0
        )
    }

    fun focusResponderAt(index: Int) {
        if (responderEntities.isEmpty()) return
        viewModel.clearCommandRoute()

        val normalized =
            ((index % responderEntities.size) +
                    responderEntities.size) %
                    responderEntities.size

        val responder =
            responderEntities[normalized]

        selectedResponderId =
            responder.id

        focusedIncidentId = null

        issueCameraCommand(
            action =
                MapCameraAction.FOCUS_POINT,
            latitude =
                responder.currentLat,
            longitude =
                responder.currentLon,
            zoom = 16.0
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapBackdrop)
    ) {
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize(),
            userLocation = uiState.userLocation,
            incidents =
                if (showIncidents) {
                    mapIncidents
                } else {
                    emptyList()
                },
            responders =
                if (showResponders) {
                    responderEntities
                } else {
                    emptyList()
                },

            /*
             * Only a user-selected/focused incident is sent as targetIncident.
             * This avoids the old automatic orange route + forced zoom on first load.
             */
            targetIncident =
                if (showIncidents) {
                    dispatchRouteIncident ?: focusedIncident
                } else {
                    null
                },
            routePoints = uiState.dispatchRoute?.points.orEmpty(),
            routeIsRoadRoute = uiState.dispatchRoute?.isRoadRoute ?: true,
            routeResponderId = uiState.dispatchRouteResponderId,
            autoFitRoute = uiState.dispatchRoute != null,

            onIncidentClick = { incident ->
                viewModel.clearCommandRoute()
                selectedIncidentId =
                    incident.incidentId

                focusedIncidentId =
                    incident.incidentId

                selectedResponderId = null

                issueCameraCommand(
                    action =
                        MapCameraAction.FOCUS_POINT,
                    latitude =
                        incident.latitude,
                    longitude =
                        incident.longitude,
                    zoom = 16.0
                )
            },

            cameraCommand = cameraCommand,
            showIncidents = showIncidents,
            showResponders = showResponders,
            zoomLevel = 13.2,

            /*
             * Make raster map rendering follow the active AGAP theme.
             *
             * Dark:
             *   OsmdroidMapView applies its dark color matrix.
             *
             * Light:
             *   The cached/original tile colors are rendered normally.
             *
             * This changes only presentation; it does not duplicate or replace
             * the permanent cache-first tile data.
             */
            isDarkTheme = isDarkMapTheme,

            // Cache-first tile provider handles cached-vs-missing behavior.
            isOnline = uiState.isOnline
        )

        /*
         * Compact theme-aware header scrim.
         *
         * In dark mode this becomes a dark fade.
         * In light mode this becomes a light fade.
         * Its height stays small, so it never washes out most of the map.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background
                                .copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.background
                                .copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )

        LiveMapHeaderV2(
            activeCount =
                mapIncidents.count {
                    it.status.uppercase() !in
                            LIVE_MAP_CLOSED_STATUSES
                },
            responderCount =
                responderEntities.size,
            isOnline =
                uiState.isOnline,
            onBack =
                onNavigateBack,
            onFilter = {
                showFilters =
                    !showFilters

                showLegend = false
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(
                    start = AgapSpacing.md,
                    end = AgapSpacing.md,
                    top = AgapSpacing.sm
                )
        )

        LiveStatusChipV2(
            isOnline = uiState.isOnline,
            activeCount = mapIncidents.size,
            responderCount = responderEntities.size,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(
                    start = 18.dp,
                    top = 74.dp
                )
        )

        if (
            uiState.dispatchRoute != null ||
            uiState.isCalculatingDispatchRoute
        ) {
            AdminRoutePreviewBanner(
                responderCallsign = dispatchRouteResponder?.unitCallsign,
                route = uiState.dispatchRoute,
                isCalculating = uiState.isCalculatingDispatchRoute,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 112.dp, start = 72.dp, end = 72.dp)
            )
        }

        CompactMapControlRail(
            legendOpen = showLegend,
            onLegend = {
                showLegend = !showLegend
                showFilters = false
            },
            onZoomIn = {
                issueCameraCommand(
                    MapCameraAction.ZOOM_IN
                )
            },
            onZoomOut = {
                issueCameraCommand(
                    MapCameraAction.ZOOM_OUT
                )
            },
            onRecenter = {
                viewModel.clearCommandRoute()
                focusedIncidentId = null
                selectedResponderId = null

                issueCameraCommand(
                    MapCameraAction.RECENTER
                )
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = AgapSpacing.md,
                    bottom = 38.dp
                )
        )


        if (showLegend) {
            LiveMapLegendV2(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        end = 72.dp,
                        bottom = 38.dp
                    )
            )
        }

        if (showFilters) {
            LiveMapFilterPanelV2(
                showIncidents = showIncidents,
                showResponders = showResponders,
                onlyOpen = onlyOpen,
                onShowIncidentsChange = {
                    showIncidents = it

                    if (!it) {
                        focusedIncidentId = null
                    }
                },
                onShowRespondersChange = {
                    showResponders = it
                },
                onOnlyOpenChange = {
                    onlyOpen = it
                },
                onClose = {
                    showFilters = false
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        top = 66.dp,
                        end = AgapSpacing.md
                    )
            )
        }

        if (
            selectedIncident != null &&
            showIncidents
        ) {
            SelectedIncidentCardV2(
                incident =
                    selectedIncident,
                userLocation =
                    uiState.userLocation,
                focused =
                    selectedIncident.incidentId ==
                            focusedIncidentId,

                incidentIndex =
                    selectedIncidentIndex,
                incidentCount =
                    mapIncidents.size,

                responderIndex =
                    selectedResponderIndex,
                responderCount =
                    responderEntities.size,
                selectedResponder =
                    selectedResponder,

                expanded =
                    incidentPanelExpanded,

                onExpandedChange = {
                    incidentPanelExpanded = it
                },

                onPreviousIncident = {
                    val base =
                        if (
                            selectedIncidentIndex >= 0
                        ) {
                            selectedIncidentIndex
                        } else {
                            0
                        }

                    focusIncidentAt(
                        base - 1
                    )
                },

                onNextIncident = {
                    val base =
                        if (
                            selectedIncidentIndex >= 0
                        ) {
                            selectedIncidentIndex
                        } else {
                            -1
                        }

                    focusIncidentAt(
                        base + 1
                    )
                },

                onPreviousResponder = {
                    val base =
                        if (
                            selectedResponderIndex >= 0
                        ) {
                            selectedResponderIndex
                        } else {
                            0
                        }

                    focusResponderAt(
                        base - 1
                    )
                },

                onNextResponder = {
                    val base =
                        if (
                            selectedResponderIndex >= 0
                        ) {
                            selectedResponderIndex
                        } else {
                            -1
                        }

                    focusResponderAt(
                        base + 1
                    )
                },

                onFocus = {
                    focusedIncidentId =
                        selectedIncident.incidentId

                    selectedResponderId = null

                    issueCameraCommand(
                        action =
                            MapCameraAction.FOCUS_POINT,
                        latitude =
                            selectedIncident.latitude,
                        longitude =
                            selectedIncident.longitude,
                        zoom = 16.0
                    )
                },

                onDetails = {
                    onNavigateToIncident(
                        selectedIncident.incidentId
                    )
                },

                onDispatch = {
                    viewModel.openDispatchSheet(
                        selectedIncident.incidentId
                    )
                },

                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .navigationBarsPadding()
                    .padding(
                        start = AgapSpacing.md,
                        end = AgapSpacing.md,
                        bottom = 10.dp
                    )
            )
        } else {
            MapOverviewCardV2(
                incidents =
                    mapIncidents.size,
                responders =
                    responderEntities.size,

                incidentIndex =
                    selectedIncidentIndex,
                responderIndex =
                    selectedResponderIndex,
                selectedResponder =
                    selectedResponder,

                expanded =
                    incidentPanelExpanded,

                onExpandedChange = {
                    incidentPanelExpanded = it
                },

                onPreviousIncident = {
                    val base =
                        if (
                            selectedIncidentIndex >= 0
                        ) {
                            selectedIncidentIndex
                        } else {
                            0
                        }

                    focusIncidentAt(
                        base - 1
                    )
                },

                onNextIncident = {
                    val base =
                        if (
                            selectedIncidentIndex >= 0
                        ) {
                            selectedIncidentIndex
                        } else {
                            -1
                        }

                    focusIncidentAt(
                        base + 1
                    )
                },

                onPreviousResponder = {
                    val base =
                        if (
                            selectedResponderIndex >= 0
                        ) {
                            selectedResponderIndex
                        } else {
                            0
                        }

                    focusResponderAt(
                        base - 1
                    )
                },

                onNextResponder = {
                    val base =
                        if (
                            selectedResponderIndex >= 0
                        ) {
                            selectedResponderIndex
                        } else {
                            -1
                        }

                    focusResponderAt(
                        base + 1
                    )
                },

                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .navigationBarsPadding()
                    .padding(
                        start = AgapSpacing.md,
                        end = AgapSpacing.md,
                        bottom = 10.dp
                    )
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(
                    Alignment.BottomCenter
                )
                .navigationBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    bottom =
                        if (
                            incidentPanelExpanded
                        ) {
                            300.dp
                        } else {
                            92.dp
                        }
                )
        )

        uiState.selectedIncidentIdForDispatch
            ?.let { incidentId ->
                AdminMapDispatchDialogV2(
                    incidentId = incidentId,
                    responders = uiState.availableDispatchResponders,
                    isLoadingResponders = uiState.isLoadingDispatchResponders,
                    selectedResponderId = uiState.dispatchRouteResponderId,
                    route = uiState.dispatchRoute,
                    isCalculatingRoute = uiState.isCalculatingDispatchRoute,
                    onDismiss = viewModel::closeDispatchSheet,
                    onSelectResponder = { unitId ->
                        viewModel.previewResponderRoute(incidentId, unitId)
                    },
                    onDispatch = { unitId ->
                        viewModel.dispatchResponder(incidentId, unitId)
                    }
                )
            }
    }
}

@Composable
private fun LiveMapHeaderV2(
    activeCount: Int,
    responderCount: Int,
    isOnline: Boolean,
    onBack: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        DarkRoundButton(
            icon =
                Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onBack
        )

        Spacer(Modifier.width(AgapSpacing.md))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE MAP",
                    color = MapText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.width(AgapSpacing.sm))

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) {
                                GreenOnline
                            } else {
                                AmberWarning
                            }
                        )
                )
            }

            Text(
                text =
                    "Legazpi City, Albay • $activeCount active • $responderCount units",
                color = MapTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DarkRoundButton(
            icon = Icons.Outlined.FilterAlt,
            contentDescription = "Filters",
            onClick = onFilter
        )
    }
}

@Composable
private fun DarkRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MapPanel,
        border =
            BorderStroke(
                1.dp,
                MapBorder
            ),
        shadowElevation = 8.dp,
        onClick = onClick
    ) {
        Box(
            contentAlignment =
                Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription =
                    contentDescription,
                tint = MapText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LiveStatusChipV2(
    isOnline: Boolean,
    activeCount: Int,
    responderCount: Int,
    modifier: Modifier = Modifier
) {
    val accent =
        if (isOnline) {
            GreenOnline
        } else {
            AmberWarning
        }

    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = MapPanelSoft,
        border =
            BorderStroke(
                1.dp,
                accent.copy(alpha = 0.42f)
            ),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.md,
                vertical = AgapSpacing.sm
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )

            Spacer(Modifier.width(AgapSpacing.sm))

            Text(
                text =
                    if (isOnline) {
                        "LIVE • CACHE-FIRST • $activeCount INCIDENTS • $responderCount UNITS"
                    } else {
                        "OFFLINE CACHE • $activeCount INCIDENTS"
                    },
                color = MapText,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.42.sp
            )
        }
    }
}

@Composable
private fun CompactMapControlRail(
    legendOpen: Boolean,
    onLegend: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    /*
     * Explicit width is important.
     * The old implementation allowed the control container to inherit broad
     * constraints and it expanded into the giant white rectangle in the screenshot.
     */
    Column(
        modifier = modifier.width(50.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = AgapShape.lg,
            color =
                if (legendOpen) {
                    BlueMedical.copy(
                        alpha = 0.94f
                    )
                } else {
                    MapPanel
                },
            border =
                BorderStroke(
                    1.dp,
                    if (legendOpen) {
                        BlueMedical
                    } else {
                        MapBorder
                    }
                ),
            shadowElevation = 8.dp,
            onClick = onLegend
        ) {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.Center
            ) {
                Icon(
                    imageVector =
                        Icons.Outlined.Layers,
                    contentDescription =
                        "Legend",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = "LEGEND",
                    color =
                        Color.White.copy(
                            alpha = 0.86f
                        ),
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Surface(
            modifier = Modifier.width(50.dp),
            shape = AgapShape.lg,
            color = MapPanel,
            border =
                BorderStroke(
                    1.dp,
                    MapBorder
                ),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.width(50.dp)
            ) {
                RailButtonV2(
                    icon = Icons.Outlined.Add,
                    label = "Zoom in",
                    onClick = onZoomIn
                )

                HorizontalDivider(
                    color = MapBorder
                )

                RailButtonV2(
                    icon = Icons.Outlined.Remove,
                    label = "Zoom out",
                    onClick = onZoomOut
                )

                HorizontalDivider(
                    color = MapBorder
                )

                RailButtonV2(
                    icon =
                        Icons.Outlined.MyLocation,
                    label = "Recenter",
                    onClick = onRecenter
                )
            }
        }
    }
}

@Composable
private fun RailButtonV2(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MapText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BottomMapNavigator(
    incidentIndex: Int,
    incidentCount: Int,
    responderIndex: Int,
    responderCount: Int,
    selectedResponder: ResponderUnitEntity?,
    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = "MAP NAVIGATION",
                color = MapTextMuted,
                fontSize = 7.5.sp,
                fontWeight =
                    FontWeight.Black,
                letterSpacing = 0.75.sp,
                modifier =
                    Modifier.weight(1f)
            )

            Text(
                text =
                    "Use arrows to move camera",
                color =
                    MapTextMuted.copy(
                        alpha = 0.78f
                    ),
                fontSize = 7.sp
            )
        }

        Spacer(
            Modifier.height(AgapSpacing.sm)
        )

        NavigationLine(
            title = "INCIDENT",
            position =
                mapPositionLabel(
                    incidentIndex,
                    incidentCount
                ),
            subtitle =
                if (incidentCount > 0) {
                    "Priority queue"
                } else {
                    "No mapped incidents"
                },
            accent = RedPrimary,
            enabled =
                incidentCount > 0,
            onPrevious =
                onPreviousIncident,
            onNext =
                onNextIncident
        )

        Spacer(
            Modifier.height(AgapSpacing.sm)
        )

        NavigationLine(
            title = "RESPONDER",
            position =
                mapPositionLabel(
                    responderIndex,
                    responderCount
                ),
            subtitle =
                selectedResponder
                    ?.let {
                        "${it.unitCallsign} • ${it.status}"
                    }
                    ?: if (
                        responderCount > 0
                    ) {
                        "Select a field unit"
                    } else {
                        "No mapped responder units"
                    },
            accent = BlueMedical,
            enabled =
                responderCount > 0,
            onPrevious =
                onPreviousResponder,
            onNext =
                onNextResponder
        )
    }
}

@Composable
private fun NavigationLine(
    title: String,
    position: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        color =
            MapPanelSoft.copy(
                alpha = 0.72f
            ),
        border =
            BorderStroke(
                1.dp,
                if (enabled) {
                    accent.copy(
                        alpha = 0.22f
                    )
                } else {
                    MapBorder
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(
                start = AgapSpacing.md,
                top = AgapSpacing.sm,
                end = AgapSpacing.sm,
                bottom = AgapSpacing.sm
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            accent
                        } else {
                            MapTextMuted.copy(
                                alpha = 0.30f
                            )
                        }
                    )
            )

            Spacer(
                Modifier.width(AgapSpacing.sm)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color =
                            if (enabled) {
                                MapText
                            } else {
                                MapTextMuted
                            },
                        fontSize = 8.sp,
                        fontWeight =
                            FontWeight.Black,
                        letterSpacing =
                            0.45.sp
                    )

                    Spacer(
                        Modifier.width(AgapSpacing.sm)
                    )

                    Text(
                        text = position,
                        color =
                            if (enabled) {
                                accent
                            } else {
                                MapTextMuted
                            },
                        fontSize = 8.sp,
                        fontWeight =
                            FontWeight.Black
                    )
                }

                Text(
                    text = subtitle,
                    color = MapTextMuted,
                    fontSize = 7.5.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            Spacer(
                Modifier.width(AgapSpacing.sm)
            )

            CompactNavigatorArrow(
                icon =
                    Icons.AutoMirrored.Outlined
                        .KeyboardArrowLeft,
                contentDescription =
                    "Previous $title",
                enabled = enabled,
                accent = accent,
                onClick = onPrevious
            )

            Spacer(
                Modifier.width(AgapSpacing.sm)
            )

            CompactNavigatorArrow(
                icon =
                    Icons.AutoMirrored.Outlined
                        .KeyboardArrowRight,
                contentDescription =
                    "Next $title",
                enabled = enabled,
                accent = accent,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun CompactNavigatorArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier.size(34.dp),
        shape = AgapShape.md,
        color =
            if (enabled) {
                accent.copy(
                    alpha = 0.10f
                )
            } else {
                MapPanel
            },
        border =
            BorderStroke(
                1.dp,
                if (enabled) {
                    accent.copy(
                        alpha = 0.42f
                    )
                } else {
                    MapBorder
                }
            ),
        enabled = enabled,
        onClick = onClick
    ) {
        Box(
            contentAlignment =
                Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription =
                    contentDescription,
                tint =
                    if (enabled) {
                        accent
                    } else {
                        MapTextMuted
                    },
                modifier =
                    Modifier.size(18.dp)
            )
        }
    }
}

private fun mapPositionLabel(
    index: Int,
    count: Int
): String {
    if (count <= 0) {
        return "0 / 0"
    }

    if (index < 0) {
        return "— / $count"
    }

    return "${index + 1} / $count"
}

@Composable
private fun LiveMapLegendV2(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(170.dp),
        shape = AgapShape.lg,
        color = MapPanelStrong,
        border =
            BorderStroke(
                1.dp,
                MapBorder
            ),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.md),
            verticalArrangement =
                Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            Text(
                text = "MAP LEGEND",
                color = MapText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )

            LegendRowV2(
                color = EmergencyType.DISASTER.color,
                label = EmergencyType.DISASTER.label
            )

            LegendRowV2(
                color = EmergencyType.MEDICAL.color,
                label = EmergencyType.MEDICAL.label
            )

            LegendRowV2(
                color = EmergencyType.FIRE.color,
                label = EmergencyType.FIRE.label
            )

            LegendRowV2(
                color = EmergencyType.ACCIDENT.color,
                label = EmergencyType.ACCIDENT.label
            )

            LegendRowV2(
                color = EmergencyType.CRIME.color,
                label = EmergencyType.CRIME.label
            )

            LegendRowV2(
                color = BlueMedical,
                label = "Responder"
            )

            LegendRowV2(
                color = GreenOnline,
                label = "Command device"
            )
        }
    }
}

@Composable
private fun LegendRowV2(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(Modifier.width(AgapSpacing.sm))

        Text(
            text = label,
            color = MapTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LiveMapFilterPanelV2(
    showIncidents: Boolean,
    showResponders: Boolean,
    onlyOpen: Boolean,
    onShowIncidentsChange: (Boolean) -> Unit,
    onShowRespondersChange: (Boolean) -> Unit,
    onOnlyOpenChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(250.dp),
        shape = AgapShape.xl,
        color = MapPanelStrong,
        border =
            BorderStroke(
                1.dp,
                MapBorder
            ),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.lg),
            verticalArrangement =
                Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text = "MAP FILTERS",
                        color = MapText,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )

                    Text(
                        text =
                            "Visible operational layers",
                        color = MapTextMuted,
                        fontSize = 9.sp
                    )
                }

                TextButton(
                    onClick = onClose
                ) {
                    Text(
                        text = "DONE",
                        color = BlueMedical,
                        fontWeight =
                            FontWeight.Black
                    )
                }
            }

            FilterRowV2(
                title = "Incidents",
                subtitle =
                    "Emergency markers",
                checked = showIncidents,
                onCheckedChange =
                    onShowIncidentsChange
            )

            FilterRowV2(
                title = "Responder units",
                subtitle =
                    "Field resources",
                checked = showResponders,
                onCheckedChange =
                    onShowRespondersChange
            )

            FilterRowV2(
                title = "Open incidents only",
                subtitle =
                    "Hide resolved calls",
                checked = onlyOpen,
                onCheckedChange =
                    onOnlyOpenChange
            )
        }
    }
}

@Composable
private fun FilterRowV2(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = MapText,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = MapTextMuted,
                fontSize = 8.5.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange =
                onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor =
                        Color.White,
                    checkedTrackColor =
                        BlueMedical,
                    uncheckedThumbColor =
                        MapTextMuted,
                    uncheckedTrackColor =
                        MapPanelSoft,
                    uncheckedBorderColor =
                        MapBorder
                )
        )
    }
}

@Composable
private fun SelectedIncidentCardV2(
    incident: IncidentEntity,
    userLocation: Location?,
    focused: Boolean,

    incidentIndex: Int,
    incidentCount: Int,

    responderIndex: Int,
    responderCount: Int,
    selectedResponder: ResponderUnitEntity?,

    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,

    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit,

    onFocus: () -> Unit,
    onDetails: () -> Unit,
    onDispatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emergencyType = EmergencyType.fromCode(incident.emergencyType)
    val accent = if (incident.status.equals("RESOLVED", ignoreCase = true)) {
        GreenOnline
    } else {
        emergencyType.color
    }

    val distance =
        distanceText(
            userLocation = userLocation,
            incident = incident
        )

    Surface(
        modifier =
            modifier.fillMaxWidth(),
        shape =
            if (expanded) {
                AgapShape.xxl
            } else {
                AgapShape.xl
            },
        color = MapPanelStrong,
        border =
            BorderStroke(
                1.dp,
                if (focused) {
                    accent.copy(
                        alpha = 0.80f
                    )
                } else {
                    MapBorder
                }
            ),
        shadowElevation =
            if (expanded) {
                15.dp
            } else {
                10.dp
            }
    ) {
        if (!expanded) {
            /*
             * COLLAPSED MODE
             *
             * Keep only operationally important context:
             * - emergency type
             * - incident number
             * - current status
             * - distance / age
             *
             * Tapping anywhere on the bar restores the full command deck.
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExpandedChange(true)
                    }
                    .padding(
                        start = AgapSpacing.md,
                        top = AgapSpacing.sm,
                        end = AgapSpacing.sm,
                        bottom = AgapSpacing.sm
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            accent.copy(
                                alpha = 0.16f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector = emergencyType.icon,
                        contentDescription = emergencyType.label,
                        tint = accent,
                        modifier =
                            Modifier.size(18.dp)
                    )
                }

                Spacer(
                    Modifier.width(AgapSpacing.sm)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = emergencyType.label.uppercase(),
                            color = MapText,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Black,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )

                        Spacer(
                            Modifier.width(AgapSpacing.xs)
                        )

                        Text(
                            text =
                                "#${incident.incidentId}",
                            color = MapTextMuted,
                            fontSize = 8.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text = buildString {
                            if (distance != null) {
                                append(distance)
                                append(" • ")
                            }

                            append(
                                formatMapAge(
                                    incident.timestamp
                                )
                            )

                            if (focused) {
                                append(" • focused")
                            }
                        },
                        color = MapTextMuted,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }

                CommandStatusPill(
                    status = incident.status
                )

                Spacer(
                    Modifier.width(AgapSpacing.xs)
                )

                IconButton(
                    onClick = {
                        onExpandedChange(true)
                    },
                    modifier =
                        Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined
                                .KeyboardArrowUp,
                        contentDescription =
                            "Expand incident panel",
                        tint = MapText,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            }
        } else {
            /*
             * EXPANDED COMMAND DECK
             */
            Column(
                modifier = Modifier.padding(
                    horizontal = AgapSpacing.md,
                    vertical = AgapSpacing.md
                )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                accent.copy(
                                    alpha = 0.16f
                                )
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector = emergencyType.icon,
                            contentDescription = emergencyType.label,
                            tint = accent,
                            modifier =
                                Modifier.size(21.dp)
                        )
                    }

                    Spacer(
                        Modifier.width(AgapSpacing.md)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${emergencyType.label.uppercase()} EMERGENCY",
                            color = MapText,
                            fontSize = 14.sp,
                            fontWeight =
                                FontWeight.Black,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildString {
                                if (distance != null) {
                                    append(distance)
                                    append(" • ")
                                }

                                append(
                                    formatMapAge(
                                        incident.timestamp
                                    )
                                )

                                append(
                                    " • #${incident.incidentId}"
                                )
                            },
                            color = MapTextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }

                    CommandStatusPill(
                        status = incident.status
                    )

                    Spacer(
                        Modifier.width(AgapSpacing.xs)
                    )

                    /*
                     * Dedicated minimize control.
                     * The user does not need to search for a collapse gesture.
                     */
                    IconButton(
                        onClick = {
                            onExpandedChange(false)
                        },
                        modifier =
                            Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.Outlined
                                    .KeyboardArrowDown,
                            contentDescription =
                                "Minimize incident panel",
                            tint = MapTextMuted,
                            modifier =
                                Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MapTextMuted,
                        modifier =
                            Modifier.size(15.dp)
                    )

                    Spacer(
                        Modifier.width(AgapSpacing.sm)
                    )

                    Text(
                        text =
                            incident.targetId
                                .ifBlank {
                                    "%.5f, %.5f".format(
                                        Locale.getDefault(),
                                        incident.latitude,
                                        incident.longitude
                                    )
                                },
                        color = MapTextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDetails,
                        modifier =
                            Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Outlined
                                    .KeyboardArrowRight,
                            contentDescription =
                                "Details",
                            tint = accent,
                            modifier =
                                Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onFocus,
                        modifier =
                            Modifier.weight(0.9f),
                        shape = AgapShape.md,
                        border =
                            BorderStroke(
                                1.dp,
                                if (focused) {
                                    accent.copy(
                                        alpha = 0.70f
                                    )
                                } else {
                                    MapBorder
                                }
                            ),
                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    contentColor =
                                        if (focused) {
                                            accent
                                        } else {
                                            MapText
                                        }
                                ),
                        contentPadding =
                            PaddingValues(
                                vertical = AgapSpacing.sm
                            )
                    ) {
                        Icon(
                            imageVector =
                                Icons.Outlined.MyLocation,
                            contentDescription = null,
                            modifier =
                                Modifier.size(14.dp)
                        )

                        Spacer(
                            Modifier.width(AgapSpacing.xs)
                        )

                        Text(
                            text =
                                if (focused) {
                                    "FOCUSED"
                                } else {
                                    "FOCUS"
                                },
                            fontSize = 8.5.sp,
                            fontWeight =
                                FontWeight.Black
                        )
                    }

                    OutlinedButton(
                        onClick = onDetails,
                        modifier =
                            Modifier.weight(0.9f),
                        shape = AgapShape.md,
                        border =
                            BorderStroke(
                                1.dp,
                                MapBorder
                            ),
                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    contentColor =
                                        MapText
                                ),
                        contentPadding =
                            PaddingValues(
                                vertical = AgapSpacing.sm
                            )
                    ) {
                        Text(
                            text = "DETAILS",
                            fontSize = 8.5.sp,
                            fontWeight =
                                FontWeight.Black
                        )
                    }

                    if (
                        incident.status
                            .uppercase() !in
                        LIVE_MAP_CLOSED_STATUSES
                    ) {
                        Button(
                            onClick = onDispatch,
                            modifier =
                                Modifier.weight(1.2f),
                            shape = AgapShape.md,
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            accent,
                                        contentColor =
                                            Color.White
                                    ),
                            contentPadding =
                                PaddingValues(
                                    vertical = AgapSpacing.sm
                                )
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Outlined.NearMe,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(14.dp)
                            )

                            Spacer(
                                Modifier.width(AgapSpacing.xs)
                            )

                            Text(
                                text = "RESPOND",
                                fontSize = 8.5.sp,
                                fontWeight =
                                    FontWeight.Black
                            )
                        }
                    }
                }

                /*
                 * Navigation belongs below the incident details.
                 */
                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                HorizontalDivider(
                    color = MapBorder
                )

                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                BottomMapNavigator(
                    incidentIndex =
                        incidentIndex,
                    incidentCount =
                        incidentCount,
                    responderIndex =
                        responderIndex,
                    responderCount =
                        responderCount,
                    selectedResponder =
                        selectedResponder,
                    onPreviousIncident =
                        onPreviousIncident,
                    onNextIncident =
                        onNextIncident,
                    onPreviousResponder =
                        onPreviousResponder,
                    onNextResponder =
                        onNextResponder
                )
            }
        }
    }
}

@Composable
private fun CommandStatusPill(
    status: String
) {
    val accent =
        when (status.uppercase()) {
            "RECEIVED" ->
                RedPrimary

            "ACKNOWLEDGED" ->
                AmberWarning

            "DISPATCHED",
            "EN_ROUTE" ->
                AmberWarning

            "ON_SCENE" ->
                BlueMedical

            "RESOLVED" ->
                GreenOnline

            else ->
                MapTextMuted
        }

    Surface(
        shape = AgapShape.pill,
        color = accent.copy(alpha = 0.12f),
        border =
            BorderStroke(
                1.dp,
                accent.copy(alpha = 0.55f)
            )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = AgapSpacing.sm,
                    vertical = AgapSpacing.xs
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )

            Spacer(Modifier.width(AgapSpacing.xs))

            Text(
                text =
                    status.replace(
                        "_",
                        " "
                    ).uppercase(),
                color = accent,
                fontSize = 7.sp,
                fontWeight =
                    FontWeight.Black,
                letterSpacing = 0.35.sp
            )
        }
    }
}

@Composable
private fun MapOverviewCardV2(
    incidents: Int,
    responders: Int,

    incidentIndex: Int,
    responderIndex: Int,
    selectedResponder: ResponderUnitEntity?,

    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,

    onPreviousIncident: () -> Unit,
    onNextIncident: () -> Unit,
    onPreviousResponder: () -> Unit,
    onNextResponder: () -> Unit,

    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier.fillMaxWidth(),
        shape =
            if (expanded) {
                AgapShape.xl
            } else {
                AgapShape.pill
            },
        color = MapPanelStrong,
        border =
            BorderStroke(
                1.dp,
                MapBorder
            ),
        shadowElevation = 12.dp
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExpandedChange(true)
                    }
                    .padding(
                        horizontal = AgapSpacing.md,
                        vertical = AgapSpacing.sm
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            BlueMedical.copy(
                                alpha = 0.13f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier =
                            Modifier.size(17.dp)
                    )
                }

                Spacer(
                    Modifier.width(AgapSpacing.sm)
                )

                Text(
                    text =
                        "$incidents incidents • $responders units",
                    color = MapText,
                    fontSize = 9.sp,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier.weight(1f)
                )

                Icon(
                    imageVector =
                        Icons.Outlined.KeyboardArrowUp,
                    contentDescription =
                        "Expand map controls",
                    tint = MapTextMuted,
                    modifier =
                        Modifier.size(20.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(
                    horizontal = AgapSpacing.md,
                    vertical = AgapSpacing.md
                )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(AgapShape.md)
                            .background(
                                BlueMedical.copy(
                                    alpha = 0.13f
                                )
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = BlueMedical,
                            modifier =
                                Modifier.size(20.dp)
                        )
                    }

                    Spacer(
                        Modifier.width(AgapSpacing.md)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            text =
                                "Operational map ready",
                            color = MapText,
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                "$incidents incidents • $responders responder units",
                            color = MapTextMuted,
                            fontSize = 9.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            onExpandedChange(false)
                        },
                        modifier =
                            Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.Outlined
                                    .KeyboardArrowDown,
                            contentDescription =
                                "Minimize map controls",
                            tint = MapTextMuted
                        )
                    }
                }

                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                HorizontalDivider(
                    color = MapBorder
                )

                Spacer(
                    Modifier.height(AgapSpacing.sm)
                )

                BottomMapNavigator(
                    incidentIndex =
                        incidentIndex,
                    incidentCount =
                        incidents,
                    responderIndex =
                        responderIndex,
                    responderCount =
                        responders,
                    selectedResponder =
                        selectedResponder,
                    onPreviousIncident =
                        onPreviousIncident,
                    onNextIncident =
                        onNextIncident,
                    onPreviousResponder =
                        onPreviousResponder,
                    onNextResponder =
                        onNextResponder
                )
            }
        }
    }
}

@Composable
private fun AdminMapDispatchDialogV2(
    incidentId: String,
    responders: List<ResponderUnitDto>,
    isLoadingResponders: Boolean,
    selectedResponderId: Long?,
    route: OfflineRouteResult?,
    isCalculatingRoute: Boolean,
    onDismiss: () -> Unit,
    onSelectResponder: (Long) -> Unit,
    onDispatch: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MapPanelStrong,
        titleContentColor = MapText,
        textContentColor = MapTextMuted,
        title = {
            Column {
                Text(
                    text = "Choose responder",
                    color = MapText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "INCIDENT #$incidentId • ROUTE PREVIEW",
                    color = AmberWarning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                if (isLoadingResponders) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AgapSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                            color = AmberWarning
                        )
                        Spacer(Modifier.width(AgapSpacing.md))
                        Column {
                            Text(
                                text = "CHECKING AVAILABLE UNITS",
                                color = MapText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Busy, offline and incompatible units are excluded",
                                color = MapTextMuted,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                } else if (responders.isEmpty()) {
                    Text(
                        text = "No eligible responder units are currently available.",
                        color = MapTextMuted
                    )
                    Text(
                        text = "Units already assigned, offline, or incompatible with this emergency are hidden.",
                        color = MapTextMuted,
                        fontSize = 8.5.sp
                    )
                } else {
                    Text(
                        text = "Only eligible IDLE units are shown. Tap one to preview its shortest road path.",
                        color = MapTextMuted,
                        fontSize = 10.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 285.dp),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        items(
                            items = responders.take(FULL_MAP_DISPATCH_LIMIT),
                            key = { it.id },
                            contentType = { "map_dispatch_unit" }
                        ) { responder ->
                            val normalizedStatus = normalizeResponderStatusValue(responder.status)
                            val selectable = normalizedStatus == "IDLE"
                            val selected = responder.id == selectedResponderId && selectable
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectable) { onSelectResponder(responder.id) },
                                shape = AgapShape.md,
                                color = if (selected) {
                                    AmberWarning.copy(alpha = 0.11f)
                                } else {
                                    MapPanelSoft
                                },
                                border = BorderStroke(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) AmberWarning else MapBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(AgapSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    AmberWarning.copy(alpha = 0.18f)
                                                } else {
                                                    BlueMedical.copy(alpha = 0.15f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) {
                                                Icons.Outlined.NearMe
                                            } else {
                                                Icons.Outlined.Group
                                            },
                                            contentDescription = null,
                                            tint = if (selected) AmberWarning else BlueMedical,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(AgapSpacing.sm))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = responder.unitCallsign,
                                            color = MapText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Agency #${responder.agencyId} • $normalizedStatus",
                                            color = MapTextMuted,
                                            fontSize = 8.5.sp
                                        )
                                    }

                                    if (selected) {
                                        Text(
                                            text = "PREVIEWING",
                                            color = AmberWarning,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedResponderId != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AgapShape.md,
                        color = MapPanelSoft,
                        border = BorderStroke(1.dp, MapBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = AgapSpacing.md,
                                vertical = AgapSpacing.md
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCalculatingRoute) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AmberWarning
                                )
                                Spacer(Modifier.width(AgapSpacing.md))
                                Column {
                                    Text(
                                        text = "CALCULATING SHORTEST ROAD PATH",
                                        color = MapText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Using downloaded routing graph; corridor fallback only if the package is missing",
                                        color = MapTextMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            } else if (route != null) {
                                Icon(
                                    imageVector = if (route.isRoadRoute) {
                                        Icons.Outlined.NearMe
                                    } else {
                                        Icons.Outlined.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = if (route.isRoadRoute) AmberWarning else RedPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(AgapSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (route.isRoadRoute) {
                                            "SHORTEST ROAD ROUTE • ${formatRouteDistance(route.distanceMeters)} • ${formatRouteEta(route.durationSeconds)}"
                                        } else {
                                            "DIRECT FALLBACK • ${formatRouteDistance(route.distanceMeters)}"
                                        },
                                        color = MapText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (route.isRoadRoute) {
                                            route.message ?: "A* route follows cached OpenStreetMap roads"
                                        } else {
                                            route.message ?: "Road routing data unavailable"
                                        },
                                        color = MapTextMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "This unit has no usable GPS position for route preview.",
                                    color = MapTextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedResponderAvailable = selectedResponderId != null && responders.any {
                it.id == selectedResponderId && normalizeResponderStatusValue(it.status) == "IDLE"
            }
            Button(
                onClick = {
                    selectedResponderId?.let(onDispatch)
                },
                enabled = selectedResponderAvailable && route != null && !isCalculatingRoute && !isLoadingResponders,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarning,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(AgapSpacing.xs))
                Text(
                    text = if (route?.isRoadRoute == true) {
                        "DISPATCH & KEEP ROUTE"
                    } else {
                        "DISPATCH"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    color = MapTextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun AdminRoutePreviewBanner(
    responderCallsign: String?,
    route: OfflineRouteResult?,
    isCalculating: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = MapPanelStrong,
        border = BorderStroke(
            1.dp,
            if (route?.isRoadRoute == false) RedPrimary.copy(alpha = 0.55f)
            else AmberWarning.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.md,
                vertical = AgapSpacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            if (isCalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AmberWarning
                )
                Text(
                    text = "CALCULATING ROUTE${responderCallsign?.let { " • $it" } ?: ""}",
                    color = MapText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black
                )
            } else if (route != null) {
                Icon(
                    imageVector = if (route.isRoadRoute) Icons.Outlined.NearMe else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (route.isRoadRoute) AmberWarning else RedPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = buildString {
                        append(responderCallsign ?: "RESPONDER")
                        append(" • ")
                        append(if (route.isRoadRoute) "SHORTEST ROAD" else "DIRECT FALLBACK")
                        append(" • ")
                        append(formatRouteDistance(route.distanceMeters))
                        if (route.isRoadRoute) {
                            append(" • ")
                            append(formatRouteEta(route.durationSeconds))
                        }
                    },
                    color = MapText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatRouteDistance(distanceMeters: Double): String =
    if (distanceMeters < 1000.0) {
        "${distanceMeters.toInt()} m"
    } else {
        "%.1f km".format(Locale.getDefault(), distanceMeters / 1000.0)
    }

private fun formatRouteEta(durationSeconds: Long): String {
    val totalMinutes = (durationSeconds / 60L).coerceAtLeast(1L)
    return if (totalMinutes < 60L) {
        "~$totalMinutes min"
    } else {
        "~${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    }
}

private fun distanceText(
    userLocation: Location?,
    incident: IncidentEntity
): String? {
    val user =
        userLocation ?: return null

    val result =
        FloatArray(1)

    Location.distanceBetween(
        user.latitude,
        user.longitude,
        incident.latitude,
        incident.longitude,
        result
    )

    val meters =
        max(0f, result[0])

    return if (meters < 1000f) {
        "${meters.toInt()} m away"
    } else {
        "%.1f km away".format(
            Locale.getDefault(),
            meters / 1000f
        )
    }
}

private fun formatMapAge(
    timestamp: Long
): String {
    val elapsed =
        (
                System.currentTimeMillis() -
                        timestamp
                ).coerceAtLeast(0L)

    val minutes =
        elapsed / 60_000L

    return when {
        minutes < 1L ->
            "just now"

        minutes < 60L ->
            "$minutes min ago"

        minutes < 1_440L ->
            "${minutes / 60L} hr ago"

        else ->
            "${minutes / 1_440L} d ago"
    }
}

private fun mapPriority(
    status: String
): Int {
    return when (status.uppercase()) {
        "RECEIVED" -> 0
        "ACKNOWLEDGED" -> 1
        "DISPATCHED" -> 2
        "EN_ROUTE" -> 3
        "ON_SCENE" -> 4
        "RESOLVED" -> 5
        else -> 6
    }
}

private fun ResponderUnitDto.toMapEntity() =
    ResponderUnitEntity(
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
