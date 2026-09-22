package com.cady.cadysalesapp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-lock PIN (LockScreen) — a separate, local-only concept from account
 * login (AccountRepository). Matches the current app's AuthService: SHA-256 hex
 * digest, and "no password set" is treated as "unlocked" rather than an error,
 * since the app-lock is an opt-in feature, not mandatory.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val passwordHashKey = stringPreferencesKey("app_lock_password_hash")
    private val biometricEnabledKey = booleanPreferencesKey("biometric_unlock_enabled")

    val isPasswordSet: Flow<Boolean> =
        dataStore.data.map { prefs -> !prefs[passwordHashKey].isNullOrEmpty() }

    val isBiometricEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[biometricEnabledKey] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[biometricEnabledKey] = enabled }
    }

    suspend fun isPasswordSetNow(): Boolean = isPasswordSet.first()

    suspend fun setPassword(rawPassword: String) {
        dataStore.edit { prefs -> prefs[passwordHashKey] = sha256(rawPassword) }
    }

    suspend fun clearPassword() {
        dataStore.edit { prefs -> prefs.remove(passwordHashKey) }
    }

    /** Always true when no password has been set — app-lock is opt-in. */
    suspend fun verify(rawPassword: String): Boolean {
        val storedHash = dataStore.data.first()[passwordHashKey] ?: return true
        return storedHash == sha256(rawPassword)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
