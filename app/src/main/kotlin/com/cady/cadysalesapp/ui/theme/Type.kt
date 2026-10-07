package com.cady.cadysalesapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FontFamily.Default resolves to the device's system font (Noto/Roboto or the
 * manufacturer's own Arabic-aware default) — no bundled .ttf, smaller APK, and
 * it automatically respects the user's system font-size accessibility setting.
 * This was an explicit decision to move away from the Flutter app's bundled
 * Cairo font.
 *
 * Phase 9: the full scale is spelled out (the old file defined five styles and left the rest to the
 * Material baseline) with bolder titles and roomier line heights, which Arabic needs more than Latin.
 * No letter-spacing anywhere: tracking breaks Arabic letter joining.
 */
private val CadyFontFamily = FontFamily.Default

private fun style(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = CadyFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

val CadyTypography = Typography(
    displayLarge = style(FontWeight.Bold, 44, 54),
    displayMedium = style(FontWeight.Bold, 38, 48),
    displaySmall = style(FontWeight.Bold, 32, 42),
    headlineLarge = style(FontWeight.Bold, 30, 40),
    headlineMedium = style(FontWeight.Bold, 26, 36),
    headlineSmall = style(FontWeight.Bold, 22, 30),
    titleLarge = style(FontWeight.Bold, 20, 28),
    titleMedium = style(FontWeight.SemiBold, 16, 24),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 26),
    bodyMedium = style(FontWeight.Normal, 14, 22),
    bodySmall = style(FontWeight.Normal, 12, 18),
    labelLarge = style(FontWeight.SemiBold, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 16),
    labelSmall = style(FontWeight.Medium, 11, 16),
)
