package com.travelingsalesman.agap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedPrimary = Color(0xFFDC2626)
val RedDark = Color(0xFF991B1B)
val AmberWarning = Color(0xFFF59E0B)
val GreenOnline = Color(0xFF10B981)
val SlateBackground = Color(0xFF0F172A)
val SlateSurface = Color(0xFF1E293B)
val SlateTextPrimary = Color(0xFFF8FAFC)
val SlateTextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    secondary = AmberWarning,
    background = SlateBackground,
    surface = SlateSurface,
    onPrimary = Color.White,
    onBackground = SlateTextPrimary,
    onSurface = SlateTextPrimary
)

@Composable
fun AGAPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}