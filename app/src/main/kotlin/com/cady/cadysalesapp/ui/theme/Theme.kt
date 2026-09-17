package com.cady.cadysalesapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * @param darkTheme Defaults to the system setting but is a parameter (not read
 *        directly from isSystemInDarkTheme() inside a fixed branch) so the
 *        Appearance settings screen (Phase 5) can override it with an explicit
 *        user choice, exactly like CompanySettings.themeMode in the current app.
 * @param seedColor Which of the seven ThemeColorPreset values to build the
 *        scheme from — defaults to the brand red.
 */
@Composable
fun CadyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: ThemeColorPreset = ThemeColorPreset.RED,
    content: @Composable () -> Unit,
) {
    // TODO(Phase 5): derive a full tonal palette from `seedColor` instead of only
    // swapping the two hand-authored schemes below, once the Appearance screen
    // actually exposes the picker.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
