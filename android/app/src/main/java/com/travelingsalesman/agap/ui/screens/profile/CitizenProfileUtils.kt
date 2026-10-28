package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
internal fun profileTextFieldColors(
    accent: Color = RedPrimary
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    focusedLabelColor = accent,
    cursorColor = accent
)

@Composable
internal fun accountStatusColor(status: String?): Color = when (status?.uppercase()) {
    "APPROVED", "ACTIVE" -> GreenActive
    "PENDING" -> PaletteOrangeDark
    "SUSPENDED", "REJECTED", "DISABLED" -> RedPrimary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun formatDateCompact(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return value.take(10)
}

internal fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "Not available"
    return value
        .replace("T", " ")
        .replace("Z", "")
        .take(16)
}

internal fun String?.orNotProvided(): String =
    this?.takeIf { it.isNotBlank() } ?: "Not provided"

internal fun String?.orFallback(fallback: String): String =
    this?.takeIf { it.isNotBlank() } ?: fallback
