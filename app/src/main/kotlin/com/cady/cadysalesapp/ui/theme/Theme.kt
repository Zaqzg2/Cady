package com.cady.cadysalesapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils

/**
 * @param darkTheme Defaults to the system setting but is a parameter (not read
 *        directly from isSystemInDarkTheme() inside a fixed branch) so the
 *        Appearance settings screen can override it with an explicit user
 *        choice, exactly like CompanySettings.themeMode in the current app.
 * @param seedColor Which of the seven ThemeColorPreset values to build the
 *        scheme from — defaults to the brand red.
 */
@Composable
fun CadyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: ThemeColorPreset = ThemeColorPreset.RED,
    content: @Composable () -> Unit,
) {
    // The hand-picked red schemes stay for the default brand color (considered,
    // specific container/on-color choices); the other six presets go through
    // simple HSL-tone derivation below rather than a full Material You HCT
    // palette, to avoid pulling in a whole color-science dependency for six
    // secondary preset swatches.
    val colorScheme = remember(seedColor, darkTheme) {
        if (seedColor == ThemeColorPreset.RED) {
            if (darkTheme) DarkColorScheme else LightColorScheme
        } else {
            deriveColorScheme(seedColor.seed, darkTheme)
        }
    }

    // The app is Arabic-only regardless of the device's system language, so RTL is
    // forced explicitly here rather than left to follow the system locale — matches
    // the Directionality(TextDirection.rtl) wrapper in the current app's main.dart.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CadyTypography,
            content = content,
        )
    }
}

private fun deriveColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (seed.red * 255).toInt().coerceIn(0, 255),
        (seed.green * 255).toInt().coerceIn(0, 255),
        (seed.blue * 255).toInt().coerceIn(0, 255),
        hsl,
    )
    fun tone(lightness: Float): Color = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.35f), lightness)))

    return if (dark) {
        darkColorScheme(
            primary = tone(0.75f),
            onPrimary = tone(0.15f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.70f),
            onSecondary = tone(0.15f),
            tertiary = tone(0.65f),
            onTertiary = tone(0.15f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.90f),
            onPrimaryContainer = tone(0.15f),
            secondary = tone(0.45f),
            onSecondary = Color.White,
            tertiary = tone(0.35f),
            onTertiary = Color.White,
        )
    }
}
