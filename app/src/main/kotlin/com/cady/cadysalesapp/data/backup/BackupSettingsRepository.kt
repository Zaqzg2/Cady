package com.cady.cadysalesapp.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The two automatic-backup preferences. Kept apart from CompanySettings on purpose: they are
    device housekeeping, not business data, and are never part of a backup. */
@Singleton
class BackupSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val autoEnabled = booleanPreferencesKey("auto_backup_enabled")
        val autoFrequency = stringPreferencesKey("auto_backup_frequency")
    }

    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.autoEnabled] ?: false }

    val autoBackupFrequency: Flow<AutoBackupFrequency> = dataStore.data.map { prefs ->
        prefs[Keys.autoFrequency]
            ?.let { runCatching { AutoBackupFrequency.valueOf(it) }.getOrNull() }
            ?: AutoBackupFrequency.DAILY
    }

    suspend fun setAutoBackup(enabled: Boolean, frequency: AutoBackupFrequency) {
        withContext(NonCancellable) {
            dataStore.edit {
                it[Keys.autoEnabled] = enabled
                it[Keys.autoFrequency] = frequency.name
            }
        }
    }
}
