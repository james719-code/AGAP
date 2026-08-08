package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.ui.components.BarangaySelectorDialog
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
fun ReportLocationScreen(
    locationSource: LocationSource,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    barangayCode: String?,
    cachedBarangays: List<CachedBarangayEntity> = emptyList(),
    onLocationChosen: (source: LocationSource, lat: Double?, lon: Double?, accuracy: Float?, barangay: String?) -> Unit,
    onOpenMapPicker: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    var showBarangayDialog by remember { mutableStateOf(false) }

    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderCol = MaterialTheme.colorScheme.surfaceVariant

    Text(
        text = "Step 3: Incident Location",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
    Text(
        text = "Select how incident coordinates are acquired:",
        style = MaterialTheme.typography.bodyMedium,
        color = subTextColor
    )

    val options = listOf(
        LocationSource.GPS to "1. USE MY CURRENT LOCATION",
        LocationSource.MAP_PIN to "2. CHOOSE ON OFFLINE MAP",
        LocationSource.BARANGAY_REFERENCE to "3. SELECT BARANGAY",
        LocationSource.UNKNOWN to "4. SEND WITHOUT LOCATION"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (src, label) ->
            val isSelected = locationSource == src
            Card(
                onClick = {
                    when (src) {
                        LocationSource.GPS -> {
                            onLocationChosen(LocationSource.GPS, latitude, longitude, accuracyMeters, null)
                        }
                        LocationSource.MAP_PIN -> {
                            onOpenMapPicker()
                        }
                        LocationSource.BARANGAY_REFERENCE -> {
                            showBarangayDialog = true
                        }
                        LocationSource.UNKNOWN -> {
                            onLocationChosen(LocationSource.UNKNOWN, null, null, null, null)
                        }
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) RedPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                shape = AgapShape.md,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) RedPrimary else borderCol,
                        shape = AgapShape.md
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) RedPrimary else textColor
                    )
                    if (isSelected) {
                        val locText = when (src) {
                            LocationSource.GPS -> if (latitude != null && longitude != null) {
                                "GPS Fix: %.5f, %.5f (Accuracy: ${accuracyMeters?.let { "%.1fm".format(it) } ?: "Unknown"})".format(latitude, longitude)
                            } else "GPS: Acquiring high-accuracy fix..."
                            LocationSource.MAP_PIN -> if (latitude != null && longitude != null) {
                                "Offline Map Pin: %.5f, %.5f (Manual)".format(latitude, longitude)
                            } else "Offline Map Pin: Tap to open map picker"
                            LocationSource.BARANGAY_REFERENCE -> "Barangay Reference: ${barangayCode ?: "Selected"} (Approximate location)"
                            LocationSource.UNKNOWN -> "Location: Unavailable / Unknown (Emergency last resort)"
                        }
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.bodySmall,
                            color = RedPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showBarangayDialog) {
        BarangaySelectorDialog(
            barangays = cachedBarangays,
            onDismissRequest = { showBarangayDialog = false },
            onBarangaySelected = { brgy ->
                val lat = brgy.representativeLatitudeE7 / 10_000_000.0
                val lon = brgy.representativeLongitudeE7 / 10_000_000.0
                onLocationChosen(LocationSource.BARANGAY_REFERENCE, lat, lon, null, brgy.barangayName)
            }
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = onPrev,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("BACK", color = textColor)
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
        ) {
            Text("NEXT: DETAILS", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
