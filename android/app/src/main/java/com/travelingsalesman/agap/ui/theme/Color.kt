package com.travelingsalesman.agap.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- AGAP Brand Color Palette (Matches exact web swatches) ---

// Light Mode Palette
val PaletteBgLight = Color(0xFFF8FAFC)            // Soft Slate White Background
val PaletteSurfaceLight = Color(0xFFFFFFFF)       // Light Card Surface
val PaletteSurfaceVariantLight = Color(0xFFECEFF4) // Soft Gray Container
val PaletteTextPrimaryLight = Color(0xFF0F172A)    // Dark Slate Text
val PaletteTextSecondaryLight = Color(0xFF64748B)  // Muted Slate Text

val PaletteTealLight = Color(0xFF1982A5)         // Cyan Teal / Info / Medical
val PaletteOrangeLight = Color(0xFFFAA935)       // Warm Amber / Dispatched / Warning
val PaletteOliveLight = Color(0xFF5E7A2A)        // Olive Green / Resolved / Online
val PaletteRedLight = Color(0xFFE0533C)          // Terracotta Red / SOS / Critical

// Dark Mode Palette
val PaletteBgDark = Color(0xFF0B0E14)             // Deep Navy Black Background
val PaletteSurfaceDark = Color(0xFF131924)        // Dark Card Surface
val PaletteSurfaceVariantDark = Color(0xFF232A38) // Dark Container Border/Muted
val PaletteTextPrimaryDark = Color(0xFFF8FAFC)    // White Slate Text
val PaletteTextSecondaryDark = Color(0xFF94A3B8)  // Muted Slate Text

val PaletteBlueDark = Color(0xFF2176FF)          // Electric Blue / Info / Medical
val PaletteOrangeDark = Color(0xFFFAA935)        // Warm Amber / Dispatched / Warning
val PaletteGreenDark = Color(0xFF2EB872)         // Mint Emerald Green / Resolved / Online
val PaletteRedDark = Color(0xFFE53935)           // Crimson Red / SOS / Critical / Crime

// Glassmorphism & Accent Strokes
val GlassStrokeDark = Color(0x1AFFFFFF)          // 10% White Border Stroke
val GlassStrokeLight = Color(0x33CBD5E1)         // Soft Slate Border Stroke
val GlassSurfaceDark = Color(0xF0131924)         // Semi-transparent Card Surface
val GlassSurfaceLight = Color(0xF0FFFFFF)

// Gradient Brushes
val GradientPrimaryRed = Brush.linearGradient(
    colors = listOf(Color(0xFFFF6B6B), Color(0xFFE53935), Color(0xFFFF8E53))
)

val GradientElectricBlue = Brush.linearGradient(
    colors = listOf(Color(0xFF38BDF8), Color(0xFF2176FF), Color(0xFF818CF8))
)

val GradientEmeraldGreen = Brush.linearGradient(
    colors = listOf(Color(0xFF34D399), Color(0xFF2EB872), Color(0xFF059669))
)

val GradientAmberWarning = Brush.linearGradient(
    colors = listOf(Color(0xFFFBBF24), Color(0xFFFAA935), Color(0xFFD97706))
)

val GradientGlassDark = Brush.verticalGradient(
    colors = listOf(Color(0x331E293B), Color(0xCC0F172A))
)