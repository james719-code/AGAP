package com.travelingsalesman.agap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semantic Theme Export Aliases (Adapts automatically or provided for direct use)
val RedPrimary = PaletteRedDark
val RedDark = PaletteRedDark
val AmberWarning = PaletteOrangeDark
val GreenOnline = PaletteGreenDark
val GreenResolved = PaletteGreenDark
val BlueMedical = PaletteBlueDark
val OrangeAccident = PaletteOrangeDark
val PurpleCrime = Color(0xFF8B5CF6)
val SlateBackground = PaletteBgDark
val SlateSurface = PaletteSurfaceDark
val SlateSurfaceVariant = PaletteSurfaceVariantDark
val SlateTextPrimary = PaletteTextPrimaryDark
val SlateTextSecondary = PaletteTextSecondaryDark

private val LightColorScheme = lightColorScheme(
    primary = PaletteRedLight,
    secondary = PaletteTealLight,
    tertiary = PaletteOrangeLight,
    background = PaletteBgLight,
    surface = PaletteSurfaceLight,
    surfaceVariant = PaletteSurfaceVariantLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PaletteTextPrimaryLight,
    onSurface = PaletteTextPrimaryLight,
    onSurfaceVariant = PaletteTextSecondaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PaletteRedDark,
    secondary = PaletteBlueDark,
    tertiary = PaletteOrangeDark,
    background = PaletteBgDark,
    surface = PaletteSurfaceDark,
    surfaceVariant = PaletteSurfaceVariantDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PaletteTextPrimaryDark,
    onSurface = PaletteTextPrimaryDark,
    onSurfaceVariant = PaletteTextSecondaryDark
)

@Composable
fun AGAPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}