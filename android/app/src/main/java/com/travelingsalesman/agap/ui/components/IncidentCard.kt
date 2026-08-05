package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.OrangeAccident
import com.travelingsalesman.agap.ui.theme.PurpleCrime
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateSurface
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (typeColor, icon) = when (incident.emergencyType.uppercase()) {
        "MEDICAL" -> BlueMedical to Icons.Default.LocalHospital
        "CRIME" -> PurpleCrime to Icons.Default.LocalPolice
        "ACCIDENT" -> OrangeAccident to Icons.Default.CarCrash
        else -> RedPrimary to Icons.Default.Warning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color indicator bar & icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = incident.emergencyType,
                    tint = typeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = incident.emergencyType.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        fontSize = 15.sp
                    )
                    StatusBadge(status = incident.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Target ID: ${incident.targetId}",
                    fontSize = 12.sp,
                    color = SlateTextSecondary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Lat: %.4f | Lon: %.4f".format(incident.latitude, incident.longitude),
                    fontSize = 11.sp,
                    color = SlateTextSecondary
                )
            }
        }
    }
}
