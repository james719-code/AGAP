package com.travelingsalesman.agap.ui.theme

import android.app.Activity
import android.content.ContextWrapper
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
val RedPrimary = BrandRed
val RedDark = BrandRedDeep
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
    primary = BrandRedDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE8E4),
    onPrimaryContainer = Color(0xFF7A1A0F),
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF1F4),
    onSecondaryContainer = Color(0xFF0A4752),
    tertiary = BrandAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0D6),
    onTertiaryContainer = Color(0xFF5E3600),
    background = PaletteBgLight,
    onBackground = PaletteTextPrimaryLight,
    surface = PaletteSurfaceLight,
    onSurface = PaletteTextPrimaryLight,
    surfaceVariant = PaletteSurfaceVariantLight,
    onSurfaceVariant = PaletteTextSecondaryLight,
    outline = Color(0xFF8A99A3),
    outlineVariant = Color(0xFFDDE3E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF0F3F5),
    surfaceContainerHigh = Color(0xFFEAEEF1),
    surfaceContainerHighest = Color(0xFFE3E8EC),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE3E8),
    // Snackbars only: a soft slate, never pure black.
    inverseSurface = Color(0xFF2B3742),
    inverseOnSurface = Color(0xFFEEF2F5),
    inversePrimary = Color(0xFFFF9C8A),
    surfaceTint = Color.Transparent,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    // Same red family as light, with white text, instead of salmon-with-black.
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1A12),
    onPrimaryContainer = Color(0xFFFFD6CE),
    secondary = Color(0xFF6CCBDA),
    onSecondary = Color(0xFF062B33),
    secondaryContainer = Color(0xFF12363F),
    onSecondaryContainer = Color(0xFFBDEAF1),
    tertiary = BrandAmberBright,
    onTertiary = Color(0xFF3D2600),
    tertiaryContainer = Color(0xFF3E2C10),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = PaletteBgDark,
    onBackground = PaletteTextPrimaryDark,
    surface = PaletteSurfaceDark,
    onSurface = PaletteTextPrimaryDark,
    surfaceVariant = PaletteSurfaceVariantDark,
    onSurfaceVariant = PaletteTextSecondaryDark,
    outline = Color(0xFF6B7B86),
    outlineVariant = Color(0xFF26313A),
    surfaceContainerLowest = Color(0xFF070B0E),
    surfaceContainerLow = Color(0xFF11181D),
    surfaceContainer = Color(0xFF182027),
    surfaceContainerHigh = Color(0xFF1E2830),
    surfaceContainerHighest = Color(0xFF26313A),
    surfaceBright = Color(0xFF2C3842),
    surfaceDim = PaletteBgDark,
    inverseSurface = Color(0xFFDDE3E8),
    inverseOnSurface = Color(0xFF14202A),
    inversePrimary = BrandRedDeep,
    surfaceTint = Color.Transparent,
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
    success = BrandGreen,
    warning = BrandAmber,
    info = BrandTeal
)
private val DarkStatusColors = AgapStatusColors(
    success = Color(0xFF6FD49A),
    warning = BrandAmberBright,
    info = Color(0xFF6CCBDA)
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
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()?.window?.let { window ->
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
