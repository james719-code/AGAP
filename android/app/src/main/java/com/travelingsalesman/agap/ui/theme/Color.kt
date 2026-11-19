package com.travelingsalesman.agap.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Choose the higher-contrast foreground for a stable incident or brand accent. */
fun contentColorForAccent(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

// AGAP brand palette, adapted for native surfaces and text contrast.

// Light Mode Palette (Web landing light palette)
val PaletteBgLight = Color(0xFFF5F6F3)            // Calm mineral canvas separates white cards
val PaletteSurfaceLight = Color(0xFFFFFFFF)       // Clear, elevated content surface
val PaletteSurfaceVariantLight = Color(0xFFDDE3DF) // Quiet surface boundaries
val PaletteTextPrimaryLight = Color(0xFF172C29)    // Deep, high-contrast evergreen ink
val PaletteTextSecondaryLight = Color(0xFF546760)  // Readable mineral grey

val PaletteTealLight = Color(0xFF176E7E)         // Teal with readable white text
val PaletteOrangeLight = Color(0xFFF2A540)       // Amber / Dispatched / Warning (#F2A540)
val PaletteOliveLight = Color(0xFF2EB872)        // Mint Green / Resolved / Online (#2EB872)
val PaletteRedLight = Color(0xFFB93323)          // Emergency terracotta with readable white text

// Dark Mode Palette (Web Command Center dark palette)
val PaletteBgDark = Color(0xFF101B1A)             // Deep evergreen charcoal
val PaletteSurfaceDark = Color(0xFF192927)        // Distinct dark card surface
val PaletteSurfaceVariantDark = Color(0xFF2B3D39) // Dark mineral boundaries
val PaletteTextPrimaryDark = Color(0xFFF1F6F2)    // Light mineral text
val PaletteTextSecondaryDark = Color(0xFFBACBC5)  // Readable secondary text

val PaletteBlueDark = Color(0xFF1F91A8)          // AGAP Teal Accent (#1F91A8)
val PaletteOrangeDark = Color(0xFFF2A540)        // AGAP Amber Accent (#F2A540)
val PaletteGreenDark = Color(0xFF2EB872)         // Mint Emerald Green (#2EB872)
val PaletteRedDark = Color(0xFFFF9B84)           // Readable terracotta on dark surfaces

val GreenActive = Color(0xFF4CAF50)              // Active status indicator green

// Incident-type semantic colors. Green remains reserved for status/availability.
val PurpleCrime = Color(0xFF8B5CF6)
val PurpleDisaster = Color(0xFFA855F7)

// Agency-type semantic colors. Agency and incident taxonomies are separate.
val BluePolice = Color(0xFF6E82D8)
val PurpleVolunteer = Color(0xFF8B6CCF)

// BLE Mesh transport badge tokens
val PurpleMeshBg = Color(0xFF673AB7)
val PurpleMeshBorder = Color(0xFF9C27B0)
val PurpleMesh = Color(0xFFE040FB)

// Glassmorphism & Accent Strokes
val GlassStrokeDark = Color(0x3326292C)          // Glass Border Stroke
val GlassStrokeLight = Color(0x40E7E3DC)         // Light Border Stroke
val GlassSurfaceDark = Color(0xF0181C1F)         // Semi-transparent Card Surface
val GlassSurfaceLight = Color(0xF0FFFFFF)

// Gradient Brushes
val GradientPrimaryRed = Brush.linearGradient(
    colors = listOf(Color(0xFFE56347), Color(0xFFD15033), Color(0xFFB83E23))
)

val GradientElectricBlue = Brush.linearGradient(
    colors = listOf(Color(0xFF38BDF8), Color(0xFF1F91A8), Color(0xFF166F82))
)

val GradientEmeraldGreen = Brush.linearGradient(
    colors = listOf(Color(0xFF34D399), Color(0xFF2EB872), Color(0xFF059669))
)

val GradientAmberWarning = Brush.linearGradient(
    colors = listOf(Color(0xFFFBBF24), Color(0xFFF2A540), Color(0xFFD97706))
)

val GradientGlassDark = Brush.verticalGradient(
    colors = listOf(Color(0x33181C1F), Color(0xCC0F1214))
)

val GradientSosButton = Brush.radialGradient(
    colors = listOf(Color(0xFFE56347), Color(0xFFD15033), Color(0xFFB83E23))
)

val GradientSosButtonActive = Brush.radialGradient(
    colors = listOf(Color(0xFFF06B4D), Color(0xFFD15033), Color(0xFF9E341B))
)
