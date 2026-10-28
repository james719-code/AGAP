package com.travelingsalesman.agap.ui.screens.home

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.EmergencySosJoystick
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToReport: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRelayDetails by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission(context))
    }
    var gpsEnabled by remember {
        mutableStateOf(isGpsProviderEnabled(context))
    }
    var showLocationRequirementDialog by remember {
        mutableStateOf(false)
    }
    var locationPermissionRequestedOnce by remember {
        mutableStateOf(false)
    }
    var entryLocationCheckDone by remember {
        mutableStateOf(false)
    }

    val locationReady = hasLocationPermission && gpsEnabled

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasLocationPermission = hasAnyLocationPermission(context)
        gpsEnabled = isGpsProviderEnabled(context)

        if (!hasLocationPermission || !gpsEnabled) {
            showLocationRequirementDialog = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        locationPermissionRequestedOnce = true
        hasLocationPermission = hasAnyLocationPermission(context)
        gpsEnabled = isGpsProviderEnabled(context)

        if (hasLocationPermission && !gpsEnabled) {
            showLocationRequirementDialog = true
        } else if (!hasLocationPermission) {
            showLocationRequirementDialog = true
        }
    }

    fun refreshLocationReadiness(): Boolean {
        hasLocationPermission = hasAnyLocationPermission(context)
        gpsEnabled = isGpsProviderEnabled(context)
        return hasLocationPermission && gpsEnabled
    }

    fun requestLocationReadiness() {
        refreshLocationReadiness()
        showLocationRequirementDialog = true
    }

    LaunchedEffect(Unit) {
        val activity = context as? MainActivity
        if (activity != null && !activity.hasCorePermissions()) {
            activity.checkAndRequestPermissions()
        }
        if (!entryLocationCheckDone) {
            entryLocationCheckDone = true
            if (!refreshLocationReadiness()) {
                showLocationRequirementDialog = true
            }
        }
    }

    LaunchedEffect(uiState.sosFeedbackMessage) {
        uiState.sosFeedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedbackMessage()
        }
    }

    if (showLocationRequirementDialog) {
        LocationRequirementDialog(
            hasLocationPermission = hasLocationPermission,
            gpsEnabled = gpsEnabled,
            permissionWasRequested = locationPermissionRequestedOnce,
            onDismiss = {
                showLocationRequirementDialog = false
            },
            onConfirm = {
                when {
                    !hasLocationPermission && !locationPermissionRequestedOnce -> {
                        showLocationRequirementDialog = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }

                    !hasLocationPermission -> {
                        showLocationRequirementDialog = false
                        systemSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }

                    !gpsEnabled -> {
                        showLocationRequirementDialog = false
                        systemSettingsLauncher.launch(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }

                    else -> {
                        showLocationRequirementDialog = false
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AgapTopBar(
                title = "Emergency assistance",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.unreadNotificationCount,
                pendingPacketCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState,
                onNotificationClick = onNavigateToNotifications
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = AgapSpacing.lg,
                    end = AgapSpacing.lg,
                    top = AgapSpacing.xl,
                    bottom = AgapSpacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xl)
            ) {
                item {
                    CitizenCommandHeader(
                        username = uiState.username,
                        userDisplayId = uiState.userDisplayId,
                        isOnline = uiState.isOnline,
                        hasLocationPermission = hasLocationPermission,
                        gpsEnabled = gpsEnabled,
                        hasLocationFix = uiState.userLocation != null,
                        meshPeers = uiState.bleMeshState.nearbyDeviceCount
                    )
                }

                if (!locationReady) {
                    item {
                        SosLocationGuardBanner(
                            hasLocationPermission = hasLocationPermission,
                            gpsEnabled = gpsEnabled,
                            onFixLocation = ::requestLocationReadiness
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        EmergencySosJoystick(
                            emergencyTypes = uiState.emergencyTypes,
                            selectedTypeCode = uiState.selectedEmergencyTypeCode,
                            onTypeSelected = { code ->
                                viewModel.selectEmergencyType(code)
                                if (!refreshLocationReadiness()) {
                                    requestLocationReadiness()
                                }
                            },
                            onEmergencyTriggered = { emergencyCode ->
                                if (refreshLocationReadiness()) {
                                    viewModel.triggerSos(emergencyCode)
                                } else {
                                    requestLocationReadiness()
                                }
                            },
                            isLoading = uiState.isSendingSos,
                            hapticsEnabled = uiState.vibrationAlertsEnabled
                        )

                        if (!locationReady && !uiState.isSendingSos) {
                            SosJoystickLocationLock(
                                hasLocationPermission = hasLocationPermission,
                                gpsEnabled = gpsEnabled,
                                onClick = ::requestLocationReadiness,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                    ) {
                        DetailedReportAction(
                            onClick = onNavigateToReport,
                            isOnline = uiState.isOnline,
                            transmissionMode = uiState.transmissionMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                        CommunicationsCard(
                            uiState = uiState,
                            expanded = showRelayDetails,
                            onExpandedChange = { showRelayDetails = !showRelayDetails },
                            onTransmissionModeSelected = viewModel::selectTransmissionMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
