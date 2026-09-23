package com.cady.cadysalesapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cady.cadysalesapp.ui.theme.ThemeColorPreset
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

/** Which page geometry PdfService should render invoices/receipts/statements at.
    THERMAL_80MM matches the Bluetooth receipt printer this app targets; A4 is
    kept for reps who print via a phone-connected office printer instead. */
enum class PdfLayoutMode { THERMAL_80MM, A4 }

data class CompanySettings(
    val companyName: String = "",
    val companyAddress: String = "",
    val companyPhone: String = "",
    val invoiceFooterText: String = "",
    /** File path in app-private storage, same convention as ProductEntity.imagePath. */
    val companyLogoPath: String? = null,
    /** Optional override for the name printed as "المندوب" on documents — leave
        blank to fall back to the signed-in account's own display name. */
    val repDisplayName: String = "",
    /** Default signature file, reused for every new document unless the rep
        draws a fresh one on that specific invoice/receipt. */
    val repSignaturePath: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val themeColor: ThemeColorPreset = ThemeColorPreset.RED,
    val fontScale: Float = 1.0f,
    val hapticOnSave: Boolean = true,
    val printLayoutMode: PdfLayoutMode = PdfLayoutMode.THERMAL_80MM,
    /** Print-specific text scale — independent from the UI's `fontScale` above,
        since a receipt's readable size and the app's own UI text size are
        tuned separately (matches the review doc's "معاينة حية لحجم خط... الطباعة"). */
    val printFontScale: Float = 1.0f,
    /** Extra space (in print-points) added between printed lines, on top of
        each line's natural height. */
    val printLineSpacingExtra: Float = 0f,
    val printBlackThreshold: Int = 175,
    val hideAmountsInRecents: Boolean = false,
)

/**
 * One DataStore-backed settings object, same shape as the Flutter app's
 * SettingsService — a handful of scalar preferences, not something that
 * belongs in Room (there's only ever one row, and it's config, not data
 * synced between devices the way customers/invoices are).
 */
@Singleton
class CompanySettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val name = stringPreferencesKey("company_name")
        val address = stringPreferencesKey("company_address")
        val phone = stringPreferencesKey("company_phone")
        val footer = stringPreferencesKey("invoice_footer_text")
        val logoPath = stringPreferencesKey("company_logo_path")
        val repDisplayName = stringPreferencesKey("rep_display_name")
        val repSignaturePath = stringPreferencesKey("rep_signature_path")
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColor = stringPreferencesKey("theme_color")
        val fontScale = stringPreferencesKey("font_scale")
        val haptic = booleanPreferencesKey("haptic_on_save")
        val printLayoutMode = stringPreferencesKey("print_layout_mode")
        val printFontScale = stringPreferencesKey("print_font_scale")
        val printLineSpacingExtra = stringPreferencesKey("print_line_spacing_extra")
        val blackThreshold = intPreferencesKey("print_black_threshold")
        val hideAmounts = booleanPreferencesKey("hide_amounts_in_recents")
    }

    val settings: Flow<CompanySettings> = dataStore.data.map { prefs ->
        CompanySettings(
            companyName = prefs[Keys.name].orEmpty(),
            companyAddress = prefs[Keys.address].orEmpty(),
            companyPhone = prefs[Keys.phone].orEmpty(),
            invoiceFooterText = prefs[Keys.footer].orEmpty(),
            companyLogoPath = prefs[Keys.logoPath],
            repDisplayName = prefs[Keys.repDisplayName].orEmpty(),
            repSignaturePath = prefs[Keys.repSignaturePath],
            themeMode = prefs[Keys.themeMode]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.SYSTEM,
            themeColor = prefs[Keys.themeColor]?.let { runCatching { ThemeColorPreset.valueOf(it) }.getOrNull() } ?: ThemeColorPreset.RED,
            fontScale = prefs[Keys.fontScale]?.toFloatOrNull() ?: 1.0f,
            hapticOnSave = prefs[Keys.haptic] ?: true,
            printLayoutMode = prefs[Keys.printLayoutMode]?.let { runCatching { PdfLayoutMode.valueOf(it) }.getOrNull() } ?: PdfLayoutMode.THERMAL_80MM,
            printFontScale = prefs[Keys.printFontScale]?.toFloatOrNull() ?: 1.0f,
            printLineSpacingExtra = prefs[Keys.printLineSpacingExtra]?.toFloatOrNull() ?: 0f,
            printBlackThreshold = prefs[Keys.blackThreshold] ?: 175,
            hideAmountsInRecents = prefs[Keys.hideAmounts] ?: false,
        )
    }

    // Every write below runs inside NonCancellable. The save button in each settings
    // screen already awaits completion before letting the user navigate away, but this
    // is what guarantees the DataStore commit itself can't be cut short if the ViewModel
    // is cleared anyway (a fast back-press racing the coroutine, process death, etc.) —
    // belt-and-suspenders against data vanishing after leaving a settings screen.

    suspend fun updateCompanyInfo(name: String, address: String, phone: String, footerText: String) {
        withContext(NonCancellable) {
            dataStore.edit {
                it[Keys.name] = name
                it[Keys.address] = address
                it[Keys.phone] = phone
                it[Keys.footer] = footerText
            }
        }
    }

    suspend fun updateCompanyLogoPath(path: String?) {
        withContext(NonCancellable) {
            dataStore.edit { if (path == null) it.remove(Keys.logoPath) else it[Keys.logoPath] = path }
        }
    }

    suspend fun updateRepDisplayName(name: String) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.repDisplayName] = name } }
    }

    suspend fun updateRepSignaturePath(path: String?) {
        withContext(NonCancellable) {
            dataStore.edit { if (path == null) it.remove(Keys.repSignaturePath) else it[Keys.repSignaturePath] = path }
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.themeMode] = mode.name } }
    }

    suspend fun updateThemeColor(color: ThemeColorPreset) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.themeColor] = color.name } }
    }

    suspend fun updateFontScale(scale: Float) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.fontScale] = scale.toString() } }
    }

    suspend fun updateHapticOnSave(enabled: Boolean) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.haptic] = enabled } }
    }

    suspend fun updatePrintLayoutMode(mode: PdfLayoutMode) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.printLayoutMode] = mode.name } }
    }

    suspend fun updatePrintFontScale(scale: Float) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.printFontScale] = scale.toString() } }
    }

    suspend fun updatePrintLineSpacingExtra(extra: Float) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.printLineSpacingExtra] = extra.toString() } }
    }

    suspend fun updatePrintBlackThreshold(threshold: Int) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.blackThreshold] = threshold } }
    }

    suspend fun updateHideAmountsInRecents(enabled: Boolean) {
        withContext(NonCancellable) { dataStore.edit { it[Keys.hideAmounts] = enabled } }
    }
}
