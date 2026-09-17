package com.cady.cadysalesapp.ui.theme

import androidx.compose.ui.graphics.Color

// Brand red — same seed as the current app (also used for the placeholder launcher icon).
val CadyRed = Color(0xFFC62828)
val CadyRedDark = Color(0xFF8E0000)
val CadyRedLight = Color(0xFFFF5F52)
val CadyRedContainerLight = Color(0xFFFFDAD4)
val CadyRedContainerDark = Color(0xFF930006)

val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = CadyRed,
    onPrimary = Color.White,
    primaryContainer = CadyRedContainerLight,
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF77574F),
    onSecondary = Color.White,
    tertiary = Color(0xFF6D5C2F),
    onTertiary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    error = Color(0xFFBA1A1A),
)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = CadyRedLight,
    onPrimary = Color(0xFF680003),
    primaryContainer = CadyRedContainerDark,
    onPrimaryContainer = CadyRedContainerLight,
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    tertiary = Color(0xFFDBC66E),
    onTertiary = Color(0xFF3B2F05),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    error = Color(0xFFFFB4AB),
)

/**
 * Deliberately independent of the (customizable) brand/primary color — "synced" should
 * always read as unambiguously positive and "attention" as unambiguously a warning, even
 * if the person picks a brand color where that meaning would otherwise clash. Ported
 * straight from the current app's app_theme.dart reasoning.
 */
object SyncColors {
    val Synced = Color(0xFF00897B)
    val Pending = Color(0xFFF9A825)
    val ErrorState = Color(0xFFC62828)
}

/** The Appearance settings screen (Phase 5) will let the user pick one of these as
    the seed for `LightColorScheme`/`DarkColorScheme` — stubbed here so the option
    list already exists once that screen is built. */
enum class ThemeColorPreset(val seed: Color) {
    RED(CadyRed),
    BLUE(Color(0xFF2962FF)),
    GREEN(Color(0xFF2E7D32)),
    PURPLE(Color(0xFF6A1B9A)),
    ORANGE(Color(0xFFEF6C00)),
    TEAL(Color(0xFF00695C)),
    INDIGO(Color(0xFF283593)),
}
