package com.travelingsalesman.agap.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.screens.report.hasValidIncidentCoordinates
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import org.osmdroid.util.GeoPoint

@Composable
fun OfflineLocationPicker(
    initialLat: Double? = null,
    initialLon: Double? = null,
    initialLocationIsConfirmed: Boolean = false,
    onConfirmLocation: (lat: Double, lon: Double, source: LocationSource) -> Unit,
    onCancel: () -> Unit
) {
    var selectedPoint by remember(initialLat, initialLon, initialLocationIsConfirmed) {
        mutableStateOf(
            if (
                initialLocationIsConfirmed &&
                    hasValidIncidentCoordinates(initialLat, initialLon)
            ) {
                GeoPoint(requireNotNull(initialLat), requireNotNull(initialLon))
            }
            else null
        )
    }
    val hasSelectedLocation = selectedPoint?.let {
        hasValidIncidentCoordinates(it.latitude, it.longitude)
    } == true

    val colors = MaterialTheme.colorScheme

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "Choose location",
                showBackButton = true,
                onBackClick = onCancel
            )
        },
        bottomBar = {
            Surface(color = colors.surface, shadowElevation = AgapSpacing.xs) {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = AgapSpacing.xl, vertical = AgapSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        val point = selectedPoint
                        Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                            Text(
                                if (hasSelectedLocation) "Pin placed" else "Tap the map where help is needed",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasSelectedLocation) AgapStatus.success else colors.onSurface
                            )
                            if (hasSelectedLocation && point != null) {
                                Text(
                                    "Latitude %.5f, longitude %.5f".format(point.latitude, point.longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            enabled = hasSelectedLocation,
                            onClick = {
                                if (hasSelectedLocation && point != null) {
                                    onConfirmLocation(point.latitude, point.longitude, LocationSource.MAP_PIN)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = AgapShape.md
                        ) {
                            Text("Use this location")
                        }
                    }
                }
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
        OsmdroidMapView(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            pickerPoint = selectedPoint,
            onMapClick = { point ->
                if (hasValidIncidentCoordinates(point.latitude, point.longitude)) {
                    selectedPoint = point
                }
            },
            zoomLevel = 15.0,
            showIncidents = false,
            showResponders = false,
            isDarkTheme = colors.background.luminance() < 0.5f,
            isOnline = false
        )
    }
}
