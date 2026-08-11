package com.travelingsalesman.agap.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Semantic Theme Export Aliases (Adapts automatically or provided for direct use)
val RedPrimary = PaletteRedDark
val RedDark = PaletteRedDark
val AmberWarning = PaletteOrangeDark
val GreenOnline = PaletteGreenDark
val GreenResolved = PaletteGreenDark
val BlueMedical = PaletteBlueDark
val OrangeAccident = PaletteOrangeDark
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

object AgapSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 28.dp
}

object AgapShape {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(20.dp)
    val xxl = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50.dp)
    val circle = CircleShape
}

@Composable
fun glassCardBackground(): Color = if (isSystemInDarkTheme()) GlassSurfaceDark else PaletteSurfaceLight

@Composable
fun glassCardBorder(): Color = if (isSystemInDarkTheme()) GlassStrokeDark else PaletteSurfaceVariantLight

// Reusable Glass Card Modifier
@Composable
fun Modifier.glassCard(
    cornerShape: Shape = AgapShape.lg,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
): Modifier = this
    .clip(cornerShape)
    .background(backgroundColor)
    .border(width = borderWidth, color = borderColor, shape = cornerShape)



@Composable
fun AGAPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
