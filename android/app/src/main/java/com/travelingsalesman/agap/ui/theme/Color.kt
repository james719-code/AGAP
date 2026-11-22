package com.travelingsalesman.agap.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Foreground for a filled accent (chip, button, selected target).
 *
 * Every accent in this palette is a mid-tone tuned to carry white text at 4.5:1,
 * so white is the default. Black is only used for genuinely pale fills.
 */
fun contentColorForAccent(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF14202A) else Color.White

/*
 * AGAP palette
 * ────────────
 * Semantic accents sit in a narrow luminance band (≈0.15–0.18): dark enough for
 * white text on a filled chip, light enough to read as an icon on dark surfaces.
 * That lets one constant work in both themes without falling back to black.
 */

// ── Brand ───────────────────────────────────────────────
val BrandRed = Color(0xFFD93B26)          // SOS / primary action (white text 4.6:1)
val BrandRedDeep = Color(0xFFB93323)      // Official terracotta, light-theme primary
val BrandTeal = Color(0xFF0B7F92)         // Connectivity / info
val BrandAmber = Color(0xFFAD6200)        // Attention / warning (AA on white)
val BrandAmberBright = Color(0xFFF2B544)  // Warning text on dark surfaces
val BrandGreen = Color(0xFF2A8250)        // Resolved / available

// ── Light theme neutrals ────────────────────────────────
val PaletteBgLight = Color(0xFFF4F6F8)
val PaletteSurfaceLight = Color(0xFFFFFFFF)
val PaletteSurfaceVariantLight = Color(0xFFE3E8EC)
val PaletteTextPrimaryLight = Color(0xFF14202A)
val PaletteTextSecondaryLight = Color(0xFF56656F)

val PaletteTealLight = BrandTeal
val PaletteOrangeLight = BrandAmber
val PaletteOliveLight = BrandGreen
val PaletteRedLight = BrandRedDeep

// ── Dark theme neutrals ─────────────────────────────────
val PaletteBgDark = Color(0xFF0B1015)
val PaletteSurfaceDark = Color(0xFF141B21)
val PaletteSurfaceVariantDark = Color(0xFF26313A)
val PaletteTextPrimaryDark = Color(0xFFEEF2F5)
val PaletteTextSecondaryDark = Color(0xFFA3B1BA)

val PaletteBlueDark = BrandTeal
val PaletteOrangeDark = BrandAmber
val PaletteGreenDark = BrandGreen
val PaletteRedDark = BrandRed

val GreenActive = BrandGreen

// ── Emergency-type accents (same luminance band) ────────
val PurpleCrime = Color(0xFF5B61E0)     // Indigo
val PurpleDisaster = Color(0xFF9A48C9)  // Violet

// ── Agency-type accents ─────────────────────────────────
val BluePolice = Color(0xFF5B61E0)
val PurpleVolunteer = Color(0xFFC2417A)

// ── Bluetooth mesh transport badge ──────────────────────
val PurpleMeshBg = Color(0xFF9A48C9)
val PurpleMeshBorder = Color(0xFF9A48C9)
val PurpleMesh = Color(0xFF9A48C9)

// ── Surfaces kept for older call sites ──────────────────
val GlassStrokeDark = Color(0x3326313A)
val GlassStrokeLight = Color(0x40E3E8EC)
val GlassSurfaceDark = Color(0xF0141B21)
val GlassSurfaceLight = Color(0xF0FFFFFF)

// ── Gradients: brand hues only ──────────────────────────
val GradientPrimaryRed = Brush.linearGradient(
    colors = listOf(Color(0xFFE8553F), BrandRed, Color(0xFFA0281A))
)

val GradientElectricBlue = Brush.linearGradient(
    colors = listOf(Color(0xFF1597AB), BrandTeal, Color(0xFF07606F))
)

val GradientEmeraldGreen = Brush.linearGradient(
    colors = listOf(Color(0xFF36A066), BrandGreen, Color(0xFF1E6440))
)

val GradientAmberWarning = Brush.linearGradient(
    colors = listOf(Color(0xFFD08A1A), BrandAmber, Color(0xFF7F4800))
)

val GradientGlassDark = Brush.verticalGradient(
    colors = listOf(Color(0x33141B21), Color(0xCC0B1015))
)

val GradientSosButton = Brush.radialGradient(
    colors = listOf(Color(0xFFEF5E46), BrandRed, Color(0xFF9E2416))
)

val GradientSosButtonActive = Brush.radialGradient(
    colors = listOf(Color(0xFFFF6E54), BrandRed, Color(0xFF8C1F12))
)

// ── SOS cancel window (full-screen countdown) ────────────────────────────────
val SosOverlayTop = Color(0xFFB3281A)
val SosOverlayBottom = Color(0xFF7E1A10)
val SosOverlayOnWhite = Color(0xFF9E2416)
