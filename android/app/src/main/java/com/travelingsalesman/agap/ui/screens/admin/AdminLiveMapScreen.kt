package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.map.MapCameraAction
import com.travelingsalesman.agap.ui.map.MapCameraCommand
import com.travelingsalesman.agap.ui.map.OsmdroidMapView
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun AdminLiveMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToIncident: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminMobileViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    /*
     * Reflect the theme that AGAP is currently rendering.
     *
     * MaterialTheme is the source of truth:
     * - AGAP dark theme  -> dark map tiles + dark command surfaces
     * - AGAP light theme -> normal/light map tiles + light command surfaces
     */
    val isDarkMapTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var showLegend by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showIncidents by remember { mutableStateOf(true) }
    var showResponders by remember { mutableStateOf(true) }
    var onlyOpen by remember { mutableStateOf(true) }

    /*
     * selectedIncident controls the bottom information card.
     * focusedIncident controls camera focus + route rendering.
     */
    var selectedIncidentId by remember { mutableStateOf<String?>(null) }
    var focusedIncidentId by remember { mutableStateOf<String?>(null) }
    var selectedResponderId by remember { mutableStateOf<Long?>(null) }

    /*
     * Full map is the primary workspace.
     * The selected-incident command deck can collapse into a compact status bar.
     */
    var incidentPanelExpanded by remember { mutableStateOf(false) }

    var cameraCommand by remember { mutableStateOf<MapCameraCommand?>(null) }
    var cameraCommandId by remember { mutableStateOf(0L) }

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

    val responderEntities = remember(uiState.responders) {
        uiState.responders
            .asSequence()
            .map { it.toMapEntity() }
            .filter {
                it.currentLat != 0.0 && it.currentLon != 0.0
            }
            .take(FULL_MAP_RESPONDER_LIMIT)
            .toList()
    }

    val mapIncidents = remember(uiState.incidents, onlyOpen) {
        uiState.incidents
            .asSequence()
            .filter {
                it.latitude != 0.0 || it.longitude != 0.0
            }
            .filter {
                !onlyOpen || it.status.uppercase() !in LIVE_MAP_CLOSED_STATUSES
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
        val selectedStillExists = mapIncidents.any { it.incidentId == selectedIncidentId }
        if (!selectedStillExists) {
            selectedIncidentId = mapIncidents.firstOrNull()?.incidentId
        }

        val focusedStillExists = mapIncidents.any { it.incidentId == focusedIncidentId }
        if (!focusedStillExists) {
            focusedIncidentId = null
        }
    }

    val selectedIncident = remember(mapIncidents, selectedIncidentId) {
        mapIncidents.firstOrNull { it.incidentId == selectedIncidentId }
    }

    val focusedIncident = remember(mapIncidents, focusedIncidentId) {
        mapIncidents.firstOrNull { it.incidentId == focusedIncidentId }
    }

    val selectedResponder = remember(responderEntities, selectedResponderId) {
        responderEntities.firstOrNull { it.id == selectedResponderId }
    }

    val dispatchRouteIncident = remember(mapIncidents, uiState.dispatchRouteIncidentId) {
        mapIncidents.firstOrNull { it.incidentId == uiState.dispatchRouteIncidentId }
    }

    val dispatchRouteResponder = remember(responderEntities, uiState.dispatchRouteResponderId) {
        responderEntities.firstOrNull { it.id == uiState.dispatchRouteResponderId }
    }

    val selectedIncidentIndex = remember(mapIncidents, selectedIncidentId) {
        mapIncidents.indexOfFirst { it.incidentId == selectedIncidentId }
    }

    val selectedResponderIndex = remember(responderEntities, selectedResponderId) {
        responderEntities.indexOfFirst { it.id == selectedResponderId }
    }

    fun issueCameraCommand(
        action: MapCameraAction,
        latitude: Double? = null,
        longitude: Double? = null,
        zoom: Double? = null
    ) {
        cameraCommandId += 1L
        cameraCommand = MapCameraCommand(
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

        val normalized = ((index % mapIncidents.size) + mapIncidents.size) % mapIncidents.size
        val incident = mapIncidents[normalized]

        selectedIncidentId = incident.incidentId
        focusedIncidentId = incident.incidentId
        selectedResponderId = null

        issueCameraCommand(
            action = MapCameraAction.FOCUS_POINT,
            latitude = incident.latitude,
            longitude = incident.longitude,
            zoom = 16.0
        )
    }

    fun focusResponderAt(index: Int) {
        if (responderEntities.isEmpty()) return
        viewModel.clearCommandRoute()

        val normalized = ((index % responderEntities.size) + responderEntities.size) % responderEntities.size
        val responder = responderEntities[normalized]

        selectedResponderId = responder.id
        focusedIncidentId = null

        issueCameraCommand(
            action = MapCameraAction.FOCUS_POINT,
            latitude = responder.currentLat,
            longitude = responder.currentLon,
            zoom = 16.0
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapBackdrop)
    ) {
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 124.dp),
            userLocation = uiState.userLocation,
            incidents = if (showIncidents) mapIncidents else emptyList(),
            responders = if (showResponders) responderEntities else emptyList(),
            targetIncident = if (showIncidents) {
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
                selectedIncidentId = incident.incidentId
                focusedIncidentId = incident.incidentId
                selectedResponderId = null
                incidentPanelExpanded = true

                issueCameraCommand(
                    action = MapCameraAction.FOCUS_POINT,
                    latitude = incident.latitude,
                    longitude = incident.longitude,
                    zoom = 16.0
                )
            },
            cameraCommand = cameraCommand,
            showIncidents = showIncidents,
            showResponders = showResponders,
            zoomLevel = 13.2,
            isDarkTheme = isDarkMapTheme,
            isOnline = uiState.isOnline
        )

        /*
         * Compact theme-aware header scrim.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )

        LiveMapHeaderV2(
            activeCount = mapIncidents.count { it.status.uppercase() !in LIVE_MAP_CLOSED_STATUSES },
            responderCount = responderEntities.size,
            isOnline = uiState.isOnline,
            onBack = onNavigateBack,
            onFilter = {
                showFilters = !showFilters
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

        if (uiState.dispatchRoute != null || uiState.isCalculatingDispatchRoute) {
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
                issueCameraCommand(MapCameraAction.ZOOM_IN)
            },
            onZoomOut = {
                issueCameraCommand(MapCameraAction.ZOOM_OUT)
            },
            onRecenter = {
                viewModel.clearCommandRoute()
                focusedIncidentId = null
                selectedResponderId = null

                issueCameraCommand(MapCameraAction.RECENTER)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    end = AgapSpacing.md,
                    top = 150.dp
                )
        )

        if (showLegend) {
            LiveMapLegendV2(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        end = 72.dp,
                        top = 150.dp
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

        if (selectedIncident != null && showIncidents) {
            SelectedIncidentCardV2(
                incident = selectedIncident,
                userLocation = uiState.userLocation,
                focused = selectedIncident.incidentId == focusedIncidentId,
                incidentIndex = selectedIncidentIndex,
                incidentCount = mapIncidents.size,
                responderIndex = selectedResponderIndex,
                responderCount = responderEntities.size,
                selectedResponder = selectedResponder,
                expanded = incidentPanelExpanded,
                onExpandedChange = {
                    incidentPanelExpanded = it
                },
                onPreviousIncident = {
                    val base = if (selectedIncidentIndex >= 0) selectedIncidentIndex else 0
                    focusIncidentAt(base - 1)
                },
                onNextIncident = {
                    val base = if (selectedIncidentIndex >= 0) selectedIncidentIndex else -1
                    focusIncidentAt(base + 1)
                },
                onPreviousResponder = {
                    val base = if (selectedResponderIndex >= 0) selectedResponderIndex else 0
                    focusResponderAt(base - 1)
                },
                onNextResponder = {
                    val base = if (selectedResponderIndex >= 0) selectedResponderIndex else -1
                    focusResponderAt(base + 1)
                },
                onFocus = {
                    focusedIncidentId = selectedIncident.incidentId
                    selectedResponderId = null
                    issueCameraCommand(
                        action = MapCameraAction.FOCUS_POINT,
                        latitude = selectedIncident.latitude,
                        longitude = selectedIncident.longitude,
                        zoom = 16.0
                    )
                },
                onDetails = {
                    onNavigateToIncident(selectedIncident.incidentId)
                },
                onDispatch = {
                    viewModel.openDispatchSheet(selectedIncident.incidentId)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = AgapSpacing.md,
                        end = AgapSpacing.md,
                        bottom = 10.dp
                    )
            )
        } else {
            MapOverviewCardV2(
                incidents = mapIncidents.size,
                responders = responderEntities.size,
                incidentIndex = selectedIncidentIndex,
                responderIndex = selectedResponderIndex,
                selectedResponder = selectedResponder,
                expanded = incidentPanelExpanded,
                onExpandedChange = {
                    incidentPanelExpanded = it
                },
                onPreviousIncident = {
                    val base = if (selectedIncidentIndex >= 0) selectedIncidentIndex else 0
                    focusIncidentAt(base - 1)
                },
                onNextIncident = {
                    val base = if (selectedIncidentIndex >= 0) selectedIncidentIndex else -1
                    focusIncidentAt(base + 1)
                },
                onPreviousResponder = {
                    val base = if (selectedResponderIndex >= 0) selectedResponderIndex else 0
                    focusResponderAt(base - 1)
                },
                onNextResponder = {
                    val base = if (selectedResponderIndex >= 0) selectedResponderIndex else -1
                    focusResponderAt(base + 1)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    bottom = if (incidentPanelExpanded) {
                        300.dp
                    } else {
                        92.dp
                    }
                )
        )

        uiState.selectedIncidentIdForDispatch?.let { incidentId ->
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
