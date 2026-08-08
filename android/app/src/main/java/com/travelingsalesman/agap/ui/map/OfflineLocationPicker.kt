package com.travelingsalesman.agap.ui.map

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.travelingsalesman.agap.ui.theme.RedPrimary
import org.osmdroid.util.GeoPoint

@Composable
fun OfflineLocationPicker(
    initialLat: Double? = 13.1391,
    initialLon: Double? = 123.7438,
    onConfirmLocation: (lat: Double, lon: Double, source: LocationSource) -> Unit,
    onCancel: () -> Unit
) {
    var selectedPoint by remember { 
        mutableStateOf(
            if (initialLat != null && initialLon != null) GeoPoint(initialLat, initialLon)
            else GeoPoint(13.1391, 123.7438)
        ) 
    }

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "OFFLINE MAP PIN PICKER",
                onProfileClick = onCancel
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
                onMapClick = { point -> selectedPoint = point },
                zoomLevel = 15.0,
                showIncidents = false,
                showResponders = false
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(16.dp))
                    .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "TAP MAP TO PLACE OR MOVE INCIDENT MARKER",
                        color = RedPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Latitude: %.6f • Longitude: %.6f".format(selectedPoint.latitude, selectedPoint.longitude),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Location Source: MAP_PIN (Offline Manual Choice)",
                        color = subTextColor,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("CANCEL", color = textColor)
                }

                Button(
                    onClick = {
                        onConfirmLocation(selectedPoint.latitude, selectedPoint.longitude, LocationSource.MAP_PIN)
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
