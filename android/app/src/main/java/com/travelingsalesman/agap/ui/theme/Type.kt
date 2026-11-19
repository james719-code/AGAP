package com.travelingsalesman.agap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Available immediately, including on devices without Play Services or connectivity.
val AppFontFamily = FontFamily.SansSerif

private fun appText(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = appText(36, 42, FontWeight.Bold),
    displayMedium = appText(32, 38, FontWeight.Bold),
    displaySmall = appText(28, 34, FontWeight.Bold),
    headlineLarge = appText(30, 36, FontWeight.Bold).copy(letterSpacing = (-0.8).sp),
    headlineMedium = appText(24, 30, FontWeight.Bold),
    headlineSmall = appText(22, 28, FontWeight.Bold),
    titleLarge = appText(22, 28, FontWeight.Bold).copy(letterSpacing = (-0.4).sp),
    titleMedium = appText(16, 22, FontWeight.SemiBold),
    titleSmall = appText(14, 20, FontWeight.SemiBold),
    bodyLarge = appText(16, 24),
    bodyMedium = appText(14, 21),
    bodySmall = appText(13, 19),
    labelLarge = appText(14, 20, FontWeight.SemiBold),
    labelMedium = appText(12, 16, FontWeight.SemiBold),
    labelSmall = appText(12, 17, FontWeight.Medium)
)
