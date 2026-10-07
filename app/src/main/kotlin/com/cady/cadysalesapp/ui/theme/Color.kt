package com.cady.cadysalesapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Brand red — same seed as before (also the launcher icon's colour).
val CadyRed = Color(0xFFC62828)
val CadyRedDark = Color(0xFF8E0000)
val CadyRedLight = Color(0xFFFF5F52)
val CadyRedContainerLight = Color(0xFFFFE3E0)
val CadyRedContainerDark = Color(0xFF8F1D22)

/**
 * Phase 9 palette. The look is "clean white cards on a warm off-white page": the page is a warm grey
 * (`background`), every card sits on `surfaceContainerLowest` (pure white in light mode) with a hairline
 * `outlineVariant` border, and the brand colour is kept for the one thing that should stand out per
 * screen (the hero panel, the main button, the selected tab). The old scheme tinted every surface pink,
 * which is what made the screens read as flat and "default Material".
 */
val LightColorScheme = lightColorScheme(
    primary = CadyRed,
    onPrimary = Color.White,
    primaryContainer = CadyRedContainerLight,
    onPrimaryContainer = Color(0xFF410004),
    inversePrimary = Color(0xFFFFB4AB),
    secondary = Color(0xFF6E5C58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E3DF),
    onSecondaryContainer = Color(0xFF2A1815),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF8F5F4),
    onBackground = Color(0xFF1F1A19),
    surface = Color(0xFFF8F5F4),
    onSurface = Color(0xFF1F1A19),
    surfaceVariant = Color(0xFFF1E7E5),
    onSurfaceVariant = Color(0xFF5B4F4D),
    surfaceTint = CadyRed,
    inverseSurface = Color(0xFF352F2E),
    inverseOnSurface = Color(0xFFFBEEEB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A7C79),
    outlineVariant = Color(0xFFE4D9D6),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3DAD8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF8F7),
    surfaceContainer = Color(0xFFF4EEEC),
    surfaceContainerHigh = Color(0xFFEEE7E5),
    surfaceContainerHighest = Color(0xFFE8E0DE),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF9E92),
    onPrimary = Color(0xFF5C0008),
    primaryContainer = CadyRedContainerDark,
    onPrimaryContainer = Color(0xFFFFDAD5),
    inversePrimary = CadyRed,
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF4A3530),
    onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFFEBC166),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF141110),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF141110),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF3E3533),
    onSurfaceVariant = Color(0xFFD4C3BF),
    surfaceTint = Color(0xFFFF9E92),
    inverseSurface = Color(0xFFEDE0DD),
    inverseOnSurface = Color(0xFF352F2E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9C8D8A),
    outlineVariant = Color(0xFF4F4543),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3B3534),
    surfaceDim = Color(0xFF141110),
    surfaceContainerLowest = Color(0xFF0F0C0B),
    surfaceContainerLow = Color(0xFF1C1817),
    surfaceContainer = Color(0xFF211C1B),
    surfaceContainerHigh = Color(0xFF2B2524),
    surfaceContainerHighest = Color(0xFF362F2E),
)

/**
 * Meaning-carrying colours that do not follow the (customisable) brand colour. A sale is always green,
 * a return always amber and a receipt always blue — in the lists, the dashboard charts and the quick
 * actions — so the type of a document is recognisable before a word of it is read.
 */
@Immutable
data class CadyExtraColors(
    val sale: Color,
    val saleContainer: Color,
    val returns: Color,
    val returnsContainer: Color,
    val receipt: Color,
    val receiptContainer: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    /** The customer owes us. */
    val debt: Color,
    /** The customer is in credit. */
    val credit: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
)

val LightExtraColors = CadyExtraColors(
    sale = Color(0xFF1B8A5A),
    saleContainer = Color(0xFFDDF5E8),
    returns = Color(0xFFC77700),
    returnsContainer = Color(0xFFFFEBCC),
    receipt = Color(0xFF1565C0),
    receiptContainer = Color(0xFFDCE9FB),
    success = Color(0xFF1B8A5A),
    warning = Color(0xFFC77700),
    info = Color(0xFF1565C0),
    debt = Color(0xFFC0392B),
    credit = Color(0xFF1B8A5A),
    heroStart = Color(0xFF8E1B1F),
    heroEnd = Color(0xFFD93636),
    onHero = Color.White,
)

val DarkExtraColors = CadyExtraColors(
    sale = Color(0xFF5FD3A0),
    saleContainer = Color(0xFF12382A),
    returns = Color(0xFFFFB347),
    returnsContainer = Color(0xFF40290A),
    receipt = Color(0xFF8AB8FF),
    receiptContainer = Color(0xFF102A4C),
    success = Color(0xFF5FD3A0),
    warning = Color(0xFFFFB347),
    info = Color(0xFF8AB8FF),
    debt = Color(0xFFFF8A80),
    credit = Color(0xFF5FD3A0),
    heroStart = Color(0xFF5A1216),
    heroEnd = Color(0xFF8F1D22),
    onHero = Color(0xFFFFDAD5),
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

/** The Appearance settings screen lets the user pick one of these as the seed for the colour scheme. */
enum class ThemeColorPreset(val seed: Color) {
    RED(CadyRed),
    BLUE(Color(0xFF2962FF)),
    GREEN(Color(0xFF2E7D32)),
    PURPLE(Color(0xFF6A1B9A)),
    ORANGE(Color(0xFFEF6C00)),
    TEAL(Color(0xFF00695C)),
    INDIGO(Color(0xFF283593)),
}
