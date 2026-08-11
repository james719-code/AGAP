package com.travelingsalesman.agap.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.screens.report.hasValidIncidentCoordinates
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.RedPrimary
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

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "OFFLINE MAP PIN PICKER",
                showBackButton = true,
                onBackClick = onCancel
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OsmdroidMapView(
                modifier = Modifier.fillMaxSize(),
                pickerPoint = selectedPoint,
                onMapClick = { point ->
                    if (hasValidIncidentCoordinates(point.latitude, point.longitude)) {
                        selectedPoint = point
                    }
                },
                zoomLevel = 15.0,
                showIncidents = false,
                showResponders = false,
                isOnline = false
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AgapSpacing.lg)
                    .align(Alignment.TopCenter)
                    .clip(AgapShape.lg)
                    .border(width = 1.dp, color = borderCol, shape = AgapShape.lg),
                shape = AgapShape.lg,
                colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(AgapSpacing.lg)) {
                    Text(
                        text = if (hasSelectedLocation) {
                            "INCIDENT LOCATION READY"
                        } else {
                            "TAP MAP TO PLACE INCIDENT MARKER"
                        },
                        color = if (hasSelectedLocation) BlueMedical else AmberWarning,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(AgapSpacing.xs))
                    Text(
                        text = selectedPoint?.let { "Latitude: %.6f • Longitude: %.6f".format(it.latitude, it.longitude) } ?: "Tap anywhere on the map to set location pin",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (hasSelectedLocation) {
                            "Tap elsewhere to move the pin, then confirm this location."
                        } else {
                            "A location is required before this report can be confirmed."
                        },
                        color = subTextColor,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AgapSpacing.lg)
                    .align(Alignment.BottomCenter)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("CANCEL", color = textColor)
                }

                Button(
                    enabled = hasSelectedLocation,
                    onClick = {
                        selectedPoint?.takeIf {
                            hasValidIncidentCoordinates(it.latitude, it.longitude)
                        }?.let { pt ->
                            onConfirmLocation(pt.latitude, pt.longitude, LocationSource.MAP_PIN)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("CONFIRM LOCATION", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
