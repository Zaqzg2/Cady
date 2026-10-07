package com.cady.cadysalesapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.cady.cadysalesapp.ui.common.findFragmentActivity

/** The document-type / hero colours that sit beside the Material scheme — read with `MaterialTheme.cady`. */
val LocalCadyColors = staticCompositionLocalOf { LightExtraColors }

val MaterialTheme.cady: CadyExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCadyColors.current

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
    val extraColors = remember(colorScheme, darkTheme) { buildExtraColors(colorScheme, darkTheme) }

    // The system bars' icon colour follows the app's own light/dark choice, not just the phone's:
    // a person who forces dark mode on a light phone would otherwise get dark icons on a dark bar.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findFragmentActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // The app is Arabic-only regardless of the device's system language, so RTL is
    // forced explicitly here rather than left to follow the system locale — matches
    // the Directionality(TextDirection.rtl) wrapper in the current app's main.dart.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalCadyColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CadyTypography,
            shapes = CadyShapes,
            content = content,
        )
    }
}

/** Hero-panel colours follow the chosen brand colour; the document-type colours stay fixed. */
private fun buildExtraColors(scheme: ColorScheme, dark: Boolean): CadyExtraColors {
    val base = if (dark) DarkExtraColors else LightExtraColors
    return if (dark) {
        base.copy(
            heroStart = lerp(scheme.primaryContainer, Color.Black, 0.25f),
            heroEnd = lerp(scheme.primaryContainer, scheme.primary, 0.30f),
            onHero = scheme.onPrimaryContainer,
        )
    } else {
        base.copy(
            heroStart = lerp(scheme.primary, Color.Black, 0.28f),
            heroEnd = lerp(scheme.primary, Color.White, 0.08f),
            onHero = Color.White,
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
    val hue = hsl[0]
    fun tone(lightness: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, hsl[1].coerceAtLeast(0.35f), lightness)))
    // Page and card surfaces: the seed's hue at very low saturation, so a blue theme gets cool greys and
    // a green theme gets soft green-greys instead of the baseline's lavender.
    fun neutral(lightness: Float, saturation: Float = 0.07f): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

    return if (dark) {
        darkColorScheme(
            primary = tone(0.75f),
            onPrimary = tone(0.15f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.70f),
            onSecondary = tone(0.15f),
            secondaryContainer = neutral(0.26f, 0.14f),
            onSecondaryContainer = neutral(0.90f, 0.20f),
            tertiary = tone(0.65f),
            onTertiary = tone(0.15f),
            background = neutral(0.075f),
            onBackground = neutral(0.90f),
            surface = neutral(0.075f),
            onSurface = neutral(0.90f),
            surfaceVariant = neutral(0.24f),
            onSurfaceVariant = neutral(0.80f),
            outline = neutral(0.60f),
            outlineVariant = neutral(0.30f),
            surfaceDim = neutral(0.075f),
            surfaceBright = neutral(0.24f),
            surfaceContainerLowest = neutral(0.05f),
            surfaceContainerLow = neutral(0.10f),
            surfaceContainer = neutral(0.13f),
            surfaceContainerHigh = neutral(0.17f),
            surfaceContainerHighest = neutral(0.21f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.90f),
            onPrimaryContainer = tone(0.15f),
            secondary = tone(0.45f),
            onSecondary = Color.White,
            secondaryContainer = neutral(0.90f, 0.20f),
            onSecondaryContainer = neutral(0.15f, 0.25f),
            tertiary = tone(0.35f),
            onTertiary = Color.White,
            background = neutral(0.965f),
            onBackground = neutral(0.10f),
            surface = neutral(0.965f),
            onSurface = neutral(0.10f),
            surfaceVariant = neutral(0.92f),
            onSurfaceVariant = neutral(0.32f),
            outline = neutral(0.50f),
            outlineVariant = neutral(0.87f),
            surfaceDim = neutral(0.86f),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral(0.98f),
            surfaceContainer = neutral(0.945f),
            surfaceContainerHigh = neutral(0.92f),
            surfaceContainerHighest = neutral(0.90f),
        )
    }
}
