package com.cady.cadysalesapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cady.cadysalesapp.ui.theme.ThemeColorPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

data class CompanySettings(
    val companyName: String = "",
    val companyAddress: String = "",
    val companyPhone: String = "",
    val invoiceFooterText: String = "",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val themeColor: ThemeColorPreset = ThemeColorPreset.RED,
    val fontScale: Float = 1.0f,
    val hapticOnSave: Boolean = true,
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
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColor = stringPreferencesKey("theme_color")
        val fontScale = stringPreferencesKey("font_scale")
        val haptic = booleanPreferencesKey("haptic_on_save")
        val blackThreshold = intPreferencesKey("print_black_threshold")
        val hideAmounts = booleanPreferencesKey("hide_amounts_in_recents")
    }

    val settings: Flow<CompanySettings> = dataStore.data.map { prefs ->
        CompanySettings(
            companyName = prefs[Keys.name].orEmpty(),
            companyAddress = prefs[Keys.address].orEmpty(),
            companyPhone = prefs[Keys.phone].orEmpty(),
            invoiceFooterText = prefs[Keys.footer].orEmpty(),
            themeMode = prefs[Keys.themeMode]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.SYSTEM,
            themeColor = prefs[Keys.themeColor]?.let { runCatching { ThemeColorPreset.valueOf(it) }.getOrNull() } ?: ThemeColorPreset.RED,
            fontScale = prefs[Keys.fontScale]?.toFloatOrNull() ?: 1.0f,
            hapticOnSave = prefs[Keys.haptic] ?: true,
            printBlackThreshold = prefs[Keys.blackThreshold] ?: 175,
            hideAmountsInRecents = prefs[Keys.hideAmounts] ?: false,
        )
    }

    suspend fun updateCompanyInfo(name: String, address: String, phone: String, footerText: String) {
        dataStore.edit {
            it[Keys.name] = name
            it[Keys.address] = address
            it[Keys.phone] = phone
            it[Keys.footer] = footerText
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun updateThemeColor(color: ThemeColorPreset) {
        dataStore.edit { it[Keys.themeColor] = color.name }
    }

    suspend fun updateFontScale(scale: Float) {
        dataStore.edit { it[Keys.fontScale] = scale.toString() }
    }

    suspend fun updateHapticOnSave(enabled: Boolean) {
        dataStore.edit { it[Keys.haptic] = enabled }
    }

    suspend fun updatePrintBlackThreshold(threshold: Int) {
        dataStore.edit { it[Keys.blackThreshold] = threshold }
    }

    suspend fun updateHideAmountsInRecents(enabled: Boolean) {
        dataStore.edit { it[Keys.hideAmounts] = enabled }
    }
}
