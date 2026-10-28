package com.travelingsalesman.agap.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat

// Stable domain colors. Use MaterialTheme.colorScheme for adaptive interface colors.
val RedPrimary = PaletteRedLight
val RedDark = PaletteRedLight
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
    primaryContainer = Color(0xFFFFE5DD),
    onPrimaryContainer = Color(0xFF762513),
    secondary = PaletteTealLight,
    secondaryContainer = Color(0xFFE1F1F2),
    onSecondaryContainer = Color(0xFF164D57),
    tertiary = Color(0xFF8A590D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBCB),
    onTertiaryContainer = Color(0xFF65410B),
    background = PaletteBgLight,
    surface = PaletteSurfaceLight,
    surfaceVariant = PaletteSurfaceVariantLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PaletteTextPrimaryLight,
    onSurface = PaletteTextPrimaryLight,
    onSurfaceVariant = PaletteTextSecondaryLight,
    outline = Color(0xFF858079),
    outlineVariant = PaletteSurfaceVariantLight,
    surfaceContainer = Color(0xFFF0EEE9),
    surfaceContainerLow = Color(0xFFFAF9F7),
    surfaceContainerHigh = Color(0xFFECE9E3),
    surfaceContainerLowest = PaletteSurfaceLight,
    surfaceContainerHighest = Color(0xFFE4E0D9),
    surfaceBright = PaletteSurfaceLight,
    surfaceDim = Color(0xFFE4E0D9),
    inverseSurface = PaletteTextPrimaryLight,
    inverseOnSurface = PaletteBgLight,
    inversePrimary = PaletteRedDark,
    surfaceTint = PaletteRedLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = PaletteRedDark,
    primaryContainer = Color(0xFF5C2A20),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFF82CBD3),
    secondaryContainer = Color(0xFF163C43),
    onSecondaryContainer = Color(0xFFB8EAF0),
    tertiary = PaletteOrangeDark,
    onTertiary = Color(0xFF442B06),
    tertiaryContainer = Color(0xFF4A3518),
    onTertiaryContainer = Color(0xFFFFDDA7),
    background = PaletteBgDark,
    surface = PaletteSurfaceDark,
    surfaceVariant = PaletteSurfaceVariantDark,
    onPrimary = Color(0xFF47180D),
    onSecondary = Color(0xFF10383F),
    onBackground = PaletteTextPrimaryDark,
    onSurface = PaletteTextPrimaryDark,
    onSurfaceVariant = PaletteTextSecondaryDark,
    outline = Color(0xFF918B84),
    outlineVariant = Color(0xFF343B40),
    surfaceContainer = Color(0xFF20262A),
    surfaceContainerLow = PaletteSurfaceDark,
    surfaceContainerHigh = Color(0xFF2B3237),
    surfaceContainerLowest = Color(0xFF0B0E10),
    surfaceContainerHighest = Color(0xFF343B40),
    surfaceBright = Color(0xFF343B40),
    surfaceDim = PaletteBgDark,
    inverseSurface = PaletteTextPrimaryDark,
    inverseOnSurface = PaletteBgDark,
    inversePrimary = PaletteRedLight,
    surfaceTint = PaletteRedDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
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

/** Status colors are separate from the brand and remain legible in either theme. */
data class AgapStatusColors(
    val success: Color,
    val warning: Color,
    val info: Color
)

private val LightStatusColors = AgapStatusColors(
    success = Color(0xFF2C704D),
    warning = Color(0xFF8A590D),
    info = Color(0xFF176E7E)
)
private val DarkStatusColors = AgapStatusColors(
    success = Color(0xFF89CCA6),
    warning = Color(0xFFF2BF72),
    info = Color(0xFF82CBD3)
)
val LocalAgapStatusColors = staticCompositionLocalOf { LightStatusColors }

object AgapStatus {
    val success: Color @Composable get() = LocalAgapStatusColors.current.success
    val warning: Color @Composable get() = LocalAgapStatusColors.current.warning
    val info: Color @Composable get() = LocalAgapStatusColors.current.info
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
fun glassCardBackground(): Color = MaterialTheme.colorScheme.surface

@Composable
fun glassCardBorder(): Color = MaterialTheme.colorScheme.outlineVariant

// Reusable Glass Card Modifier
@Composable
fun Modifier.glassCard(
    cornerShape: Shape = AgapShape.lg,
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalAgapStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
      MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = AgapShape.xs,
            small = AgapShape.md,
            medium = AgapShape.lg,
            large = AgapShape.xl,
            extraLarge = AgapShape.xxl
        ),
        content = content
      )
    }
}
