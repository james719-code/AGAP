package com.travelingsalesman.agap.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.data.repository.ReportDelivery
import com.travelingsalesman.agap.data.repository.SubmitReportResult
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.glassCard

@Composable
internal fun ReportStatusScreen(result: SubmitReportResult?, pendingMessage: String?, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (title, icon, accent) = when (result?.delivery) {
        ReportDelivery.RECEIVED_BY_DISPATCH ->
            Triple("Dispatch received your report", Icons.Outlined.CheckCircle, AgapStatus.success)

        ReportDelivery.QUEUED_FOR_RELAY ->
            Triple("Waiting for a nearby relay", Icons.Outlined.Schedule, AgapStatus.warning)

        ReportDelivery.LOCAL_TEST ->
            Triple("Test report saved on this phone", Icons.Outlined.PhoneAndroid, AgapStatus.info)

        null -> Triple("Saved for retry", Icons.Outlined.Schedule, AgapStatus.warning)
    }
    val detail = when (result?.delivery) {
        ReportDelivery.RECEIVED_BY_DISPATCH -> "Your report is available to the emergency response team. This confirms delivery, not that help is on the way."
        ReportDelivery.QUEUED_FOR_RELAY -> "Your report is saved on this phone. ${result.message}"
        ReportDelivery.LOCAL_TEST -> "This report stayed on your phone in the offline test app."
        null -> pendingMessage ?: "AGAP will retry delivery when it can connect."
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerShape = AgapShape.xxl, borderColor = accent.copy(alpha = 0.35f))
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(AgapSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Box(
                    Modifier.size(56.dp).background(accent.copy(alpha = 0.12f), AgapShape.circle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(AgapSpacing.section))
                }
                Text("Delivery status", style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            HorizontalDivider(color = colors.outlineVariant)
            DeliveryCheckpoint("Saved on this phone", "Complete", Icons.Outlined.CheckCircle, AgapStatus.success)
            DeliveryCheckpoint(
                title = "Dispatch confirmation",
                status = when (result?.delivery) {
                    ReportDelivery.RECEIVED_BY_DISPATCH -> "Received"
                    ReportDelivery.LOCAL_TEST -> "Not sent · test mode"
                    else -> "Not yet confirmed"
                },
                icon = when (result?.delivery) {
                    ReportDelivery.RECEIVED_BY_DISPATCH -> Icons.Outlined.CheckCircle
                    ReportDelivery.LOCAL_TEST -> Icons.Outlined.Info
                    else -> Icons.Outlined.Schedule
                },
                accent = accent
            )
            if (result == null && pendingMessage != null) {
                Text("AGAP will retry delivery automatically.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = AgapShape.md
        ) {
            Text("Return to home")
        }
    }
}

@Composable
private fun DeliveryCheckpoint(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(AgapSpacing.xxl))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = accent)
        }
    }
}
