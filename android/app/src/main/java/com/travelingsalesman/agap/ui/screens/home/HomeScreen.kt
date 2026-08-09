package com.travelingsalesman.agap.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.EmergencySosJoystick
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToReport: () -> Unit = {},
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

        // Permission is only the first half of SOS location readiness.
        // If access was granted but GPS itself is off, immediately continue
        // to the GPS-enable explanation.
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
                // Dismissing does not bypass the guard. The joystick stays locked and
                // tapping it will reopen this prompt.
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
                        // Permission was already denied once. Open the app's permission page,
                        // which also handles "Don't ask again" cleanly.
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
                title = "CITIZEN SOS",
                onProfileClick = onNavigateToProfile,
                notificationCount = uiState.pendingPacketCount,
                bleMeshState = uiState.bleMeshState
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // The app-level navigation scaffold already reserves the bottom navigation.
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AgapSpacing.lg,
                    end = AgapSpacing.lg,
                    top = AgapSpacing.md,
                    bottom = AgapSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
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

                // Keep the actual emergency action immediately below the readiness header.
                // The joystick is visually available, but AGAP places an interaction shield over it
                // until location permission + GPS are ready. This prevents a drag from being armed
                // without the location channel responders depend on.
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        EmergencySosJoystick(
                            emergencyTypes = uiState.emergencyTypes,
                            selectedTypeCode = uiState.selectedEmergencyTypeCode,
                            onTypeSelected = { code ->
                                viewModel.selectEmergencyType(code)

                                // If location was disabled after the page opened, selecting an
                                // emergency immediately re-checks and prompts before dispatch.
                                if (!refreshLocationReadiness()) {
                                    requestLocationReadiness()
                                }
                            },
                            onEmergencyTriggered = { emergencyCode ->
                                // Final guard at the exact send boundary. Even if GPS is toggled off
                                // through Quick Settings while this screen is open, no SOS is sent
                                // until the location requirement is restored.
                                if (refreshLocationReadiness()) {
                                    viewModel.triggerSos(emergencyCode)
                                } else {
                                    requestLocationReadiness()
                                }
                            },
                            isLoading = uiState.isSendingSos
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailedReportAction(
                            onClick = onNavigateToReport,
                            modifier = Modifier.weight(1f)
                        )
                        CommunicationsCard(
                            uiState = uiState,
                            expanded = showRelayDetails,
                            onExpandedChange = { showRelayDetails = !showRelayDetails },
                            onTransmissionModeSelected = viewModel::selectTransmissionMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CitizenCommandHeader(
    username: String?,
    userDisplayId: String?,
    isOnline: Boolean,
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    hasLocationFix: Boolean,
    meshPeers: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .padding(AgapSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EMERGENCY ACCESS",
                color = RedPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                label = if (isOnline) "ONLINE" else "OFFLINE",
                accent = if (isOnline) GreenOnline else AmberWarning
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocationOn,
                label = when {
                    !hasLocationPermission -> "NO ACCESS"
                    !gpsEnabled -> "GPS OFF"
                    hasLocationFix -> "GPS READY"
                    else -> "GPS ON"
                },
                accent = when {
                    !hasLocationPermission -> AmberWarning
                    !gpsEnabled -> RedPrimary
                    hasLocationFix -> GreenOnline
                    else -> BlueMedical
                }
            )
            ReadinessPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bluetooth,
                label = "$meshPeers PEER${if (meshPeers == 1) "" else "S"}",
                accent = if (meshPeers > 0) BlueMedical else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadinessPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = AgapShape.pill
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}



@Composable
private fun DetailedReportAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.lg,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(BlueMedical.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = BlueMedical,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "OPEN",
                    color = BlueMedical,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column {
                Text(
                    text = "Report Incident",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Add photos & details",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CommunicationsCard(
    uiState: HomeUiState,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onTransmissionModeSelected: (TransmissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meshOperational =
        uiState.bleMeshState.isScanning || uiState.bleMeshState.isAdvertising

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.lg)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpandedChange)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (meshOperational) BlueMedical.copy(alpha = 0.12f)
                                else AmberWarning.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (meshOperational) BlueMedical else AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Comm Mode",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (meshOperational) "BLE Mesh active" else "Internet direct",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommunicationMetric(
                        modifier = Modifier.weight(1f),
                        label = "MESH PEERS",
                        value = uiState.bleMeshState.nearbyDeviceCount.toString(),
                        accent = BlueMedical
                    )
                    CommunicationMetric(
                        modifier = Modifier.weight(1f),
                        label = "RELAYED",
                        value = uiState.bleMeshState.relayedPacketCount.toString(),
                        accent = GreenOnline
                    )
                    CommunicationMetric(
                        modifier = Modifier.weight(1f),
                        label = "PENDING",
                        value = uiState.pendingPacketCount.toString(),
                        accent = if (uiState.pendingPacketCount > 0) AmberWarning else GreenOnline
                    )
                }

                Spacer(Modifier.height(13.dp))

                if (!uiState.bleMeshState.isEnabled) {
                    OutlinedButton(
                        onClick = {
                            (context as? MainActivity)?.requestBluetoothEnable()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AmberWarning
                        ),
                        shape = AgapShape.md
                    ) {
                        Text(
                            text = "TURN ON BLUETOOTH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                } else if (uiState.bleMeshState.needsPermissionsPrompt) {
                    OutlinedButton(
                        onClick = {
                            (context as? MainActivity)?.checkAndRequestPermissions()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RedPrimary
                        ),
                        shape = AgapShape.md
                    ) {
                        Text(
                            text = "GRANT NEARBY DEVICES PERMISSION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = "TRANSMISSION MODE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )

                Spacer(Modifier.height(7.dp))

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    TransmissionModeChip(
                        label = "AUTO / HYBRID",
                        subtitle = "Internet first, BLE relay fallback",
                        selected = uiState.transmissionMode == TransmissionMode.AUTO,
                        accent = RedPrimary,
                        onClick = { onTransmissionModeSelected(TransmissionMode.AUTO) }
                    )
                    TransmissionModeChip(
                        label = "BLE MESH ONLY",
                        subtitle = "Store-carry-forward emergency relay",
                        selected = uiState.transmissionMode == TransmissionMode.FORCE_BLE_MESH,
                        accent = BlueMedical,
                        onClick = {
                            onTransmissionModeSelected(TransmissionMode.FORCE_BLE_MESH)
                            (context as? MainActivity)?.checkAndRequestPermissions()
                        }
                    )
                    TransmissionModeChip(
                        label = "INTERNET DIRECT",
                        subtitle = "Send directly to the configured server",
                        selected = uiState.transmissionMode == TransmissionMode.FORCE_INTERNET,
                        accent = GreenOnline,
                        onClick = {
                            onTransmissionModeSelected(TransmissionMode.FORCE_INTERNET)
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isWifiHotspotBroadcasting) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Wifi
                        },
                        contentDescription = null,
                        tint = if (uiState.isWifiHotspotBroadcasting) GreenOnline
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (uiState.isWifiHotspotBroadcasting) {
                            "Open Wi-Fi relay hotspot is broadcasting"
                        } else {
                            "Open Wi-Fi relay hotspot is currently off"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunicationMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        shape = AgapShape.md
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransmissionModeChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AgapShape.md,
        color = if (selected) accent.copy(alpha = 0.09f)
        else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


@Composable
private fun SosLocationGuardBanner(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onFixLocation: () -> Unit
) {
    val accent = if (!hasLocationPermission) AmberWarning else RedPrimary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFixLocation),
        shape = AgapShape.lg,
        color = accent.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!hasLocationPermission) {
                        "LOCATION ACCESS REQUIRED"
                    } else {
                        "GPS IS OFF"
                    },
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.55.sp
                )

                Text(
                    text = if (!hasLocationPermission) {
                        "Allow location access before the SOS joystick can be armed."
                    } else if (!gpsEnabled) {
                        "Turn on GPS so AGAP can attach your position to the emergency alert."
                    } else {
                        "Location is being prepared."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "FIX",
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@Composable
private fun SosJoystickLocationLock(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.46f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = AgapShape.xl,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            border = BorderStroke(
                1.dp,
                if (!hasLocationPermission) {
                    AmberWarning.copy(alpha = 0.55f)
                } else {
                    RedPrimary.copy(alpha = 0.55f)
                }
            ),
            shadowElevation = 10.dp,
            modifier = Modifier.padding(horizontal = 34.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (!hasLocationPermission) {
                                AmberWarning.copy(alpha = 0.12f)
                            } else {
                                RedPrimary.copy(alpha = 0.12f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (!hasLocationPermission) AmberWarning else RedPrimary,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (!hasLocationPermission) {
                        "LOCATION ACCESS NEEDED"
                    } else {
                        "ENABLE GPS TO ARM SOS"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = if (!hasLocationPermission) {
                        "Tap here to allow location access."
                    } else if (!gpsEnabled) {
                        "Tap here to open the Android GPS setting."
                    } else {
                        "Preparing location services."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LocationRequirementDialog(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    permissionWasRequested: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val needsPermission = !hasLocationPermission

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (needsPermission) {
                            AmberWarning.copy(alpha = 0.12f)
                        } else {
                            RedPrimary.copy(alpha = 0.12f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (needsPermission) AmberWarning else RedPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }
        },
        title = {
            Text(
                text = when {
                    needsPermission && permissionWasRequested ->
                        "Location permission is blocked"
                    needsPermission ->
                        "Allow location for SOS"
                    !gpsEnabled ->
                        "Turn on GPS for SOS"
                    else ->
                        "Location ready"
                },
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Text(
                text = when {
                    needsPermission && permissionWasRequested ->
                        "AGAP still does not have location access. Open App Settings and allow location so the SOS joystick can attach your position."
                    needsPermission ->
                        "AGAP uses your location to help responders identify where the emergency is happening. Allow location access before arming the SOS joystick."
                    !gpsEnabled ->
                        "GPS is currently off. Turn on Location/GPS in Android settings before choosing and sending an emergency alert."
                    else ->
                        "Location services are ready for emergency use."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = when {
                        needsPermission && permissionWasRequested ->
                            "OPEN APP SETTINGS"
                        needsPermission ->
                            "ALLOW LOCATION"
                        !gpsEnabled ->
                            "ENABLE GPS"
                        else ->
                            "DONE"
                    },
                    color = if (needsPermission) AmberWarning else RedPrimary,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            if (needsPermission || !gpsEnabled) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "NOT NOW",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse
}

private fun isGpsProviderEnabled(context: Context): Boolean {
    return try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val masterLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            true
        }

        masterLocationEnabled &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: Exception) {
        false
    }
}

private fun communicationsSummary(uiState: HomeUiState): String {
    val route = when (uiState.transmissionMode) {
        TransmissionMode.AUTO -> if (uiState.isOnline) {
            "Internet available • BLE fallback ready"
        } else {
            "Offline • BLE GATT store-carry-forward active"
        }

        TransmissionMode.FORCE_BLE_MESH -> "BLE GATT mesh only"
        TransmissionMode.FORCE_INTERNET -> "Internet direct only"
    }

    return "$route • ${uiState.pendingPacketCount} pending delivery"
}