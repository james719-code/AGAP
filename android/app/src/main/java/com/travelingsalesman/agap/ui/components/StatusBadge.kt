package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import java.util.Locale

/** Status uses a dot, a readable label, and an adaptive color; never color alone. */
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val normalized = status.trim().uppercase(Locale.ROOT)
    val color = when (normalized) {
        "CRITICAL", "ACTIVE", "RECEIVED", "ACKNOWLEDGED" -> MaterialTheme.colorScheme.error
        "ASSIGNED", "ACCEPTED", "DISPATCHED", "EN_ROUTE", "ON_SCENE" -> AgapStatus.warning
        "RESOLVED", "IDLE", "AVAILABLE" -> AgapStatus.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.background(color.copy(alpha = 0.10f), AgapShape.xs)
            .padding(horizontal = AgapSpacing.sm, vertical = AgapSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = normalized.lowercase(Locale.ROOT).replace('_', ' ')
                .replaceFirstChar { it.titlecase(Locale.ROOT) },
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
