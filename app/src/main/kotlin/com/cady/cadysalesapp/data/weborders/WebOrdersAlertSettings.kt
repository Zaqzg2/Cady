package com.cady.cadysalesapp.data.weborders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two device-only preferences of the web-order alerts (never part of a backup, like the backup switches):
 * whether the manager wants to be alerted, and which orders were already announced so that the live listener
 * and the background check never ring twice for the same order.
 */
@Singleton
class WebOrdersAlertSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val enabled = booleanPreferencesKey("web_orders_alerts_enabled")
        val announced = stringSetPreferencesKey("web_orders_announced_ids")
    }

    /** On unless the manager switched it off: someone who never touched the switch wants to hear about orders. */
    val alertsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.enabled] ?: true }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        withContext(NonCancellable) {
            dataStore.edit { it[Keys.enabled] = enabled }
        }
    }

    /**
     * Of the orders waiting right now ([waitingIds]), the ones nobody has been told about yet — and from this
     * moment they count as told. One atomic edit, so two callers racing can never both announce the same order.
     * Only orders that were seen again as handled ([handledIds]) are forgotten: the live listener and the
     * background check read windows of different sizes, so "not in my window" must never mean "forget it".
     */
    suspend fun claimUnannounced(waitingIds: Set<String>, handledIds: Set<String>): Set<String> {
        var fresh: Set<String> = emptySet()
        dataStore.edit { prefs ->
            val known = prefs[Keys.announced].orEmpty()
            fresh = waitingIds - known
            prefs[Keys.announced] = bounded((known - handledIds) + fresh, waitingIds)
        }
        return fresh
    }

    /** Orders the manager is looking at right now count as told, without ringing anything. */
    suspend fun markSeen(waitingIds: Set<String>, handledIds: Set<String>) {
        dataStore.edit { prefs ->
            val known = prefs[Keys.announced].orEmpty()
            prefs[Keys.announced] = bounded((known - handledIds) + waitingIds, waitingIds)
        }
    }

    /** Orders deleted from Firestore would otherwise stay here forever; past the cap, start again from what is waiting. */
    private fun bounded(ids: Set<String>, waitingIds: Set<String>): Set<String> =
        if (ids.size <= MAX_KNOWN) ids else waitingIds

    private companion object {
        const val MAX_KNOWN = 500
    }
}
