package com.cady.cadysalesapp.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rep-side sync activity log: Firebase sync runs, manual exports and manual imports,
 * newest first, capped at [MAX_ENTRIES]. Kept as a small JSON file rather than a Room table
 * on purpose — adding a table would force a database migration just for a log, and this
 * log must never be part of a backup/restore (it describes *this device's* history).
 *
 * Manual-export files live in [outboxDir]; when an entry falls off the end of the log its
 * file is deleted with it, so the outbox can't grow forever.
 */
@Singleton
class SyncActivityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<SyncActivityEntry>>(emptyList())

    @Volatile
    private var loaded = false

    val outboxDir: File get() = File(context.filesDir, "sync/outbox")
    private val logFile: File get() = File(context.filesDir, "sync/activity_log.json")

    /** Current log, newest first. Loads from disk on first collection. */
    val entries: Flow<List<SyncActivityEntry>> = flow {
        ensureLoaded()
        emitAll(state)
    }

    suspend fun snapshot(): List<SyncActivityEntry> {
        ensureLoaded()
        return state.value
    }

    suspend fun record(entry: SyncActivityEntry) {
        ensureLoaded()
        mutex.withLock {
            val merged = (listOf(entry) + state.value.filter { it.id != entry.id })
                .sortedByDescending { it.at }
            val kept = merged.take(MAX_ENTRIES)
            val dropped = merged.drop(MAX_ENTRIES)
            withContext(Dispatchers.IO) {
                dropped.forEach { old -> old.fileName?.let { File(outboxDir, it).delete() } }
                writeToDisk(kept)
            }
            state.value = kept
        }
    }

    /** Marks a manual export as confirmed by the manager. Returns false if no such export is logged. */
    suspend fun markAcknowledged(exportId: String): Boolean {
        ensureLoaded()
        return mutex.withLock {
            val current = state.value
            if (current.none { it.id == exportId && it.kind == SyncActivityKind.MANUAL_EXPORT }) {
                false
            } else {
                val updated = current.map { if (it.id == exportId) it.copy(acknowledged = true) else it }
                withContext(Dispatchers.IO) { writeToDisk(updated) }
                state.value = updated
                true
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (!loaded) {
                state.value = withContext(Dispatchers.IO) { readFromDisk() }
                loaded = true
            }
        }
    }

    private fun readFromDisk(): List<SyncActivityEntry> {
        val file = logFile
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            val out = ArrayList<SyncActivityEntry>(array.length())
            for (i in 0 until array.length()) {
                parseEntry(array.getJSONObject(i))?.let { out.add(it) }
            }
            out.sortedByDescending { it.at }
        } catch (e: Exception) {
            // A corrupt log must never break the sync screen — start over from empty.
            emptyList()
        }
    }

    private fun writeToDisk(entries: List<SyncActivityEntry>) {
        val file = logFile
        file.parentFile?.mkdirs()
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun SyncActivityEntry.toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("kind", kind.name)
        o.put("at", at.toEpochMilli())
        o.put("status", status.name)
        o.put("title", title)
        o.put("detail", detail ?: JSONObject.NULL)
        o.put("fileName", fileName ?: JSONObject.NULL)
        o.put("customers", counts.customers)
        o.put("products", counts.products)
        o.put("invoices", counts.invoices)
        o.put("receipts", counts.receipts)
        o.put("acknowledged", acknowledged)
        return o
    }

    private fun parseEntry(o: JSONObject): SyncActivityEntry? = try {
        SyncActivityEntry(
            id = o.getString("id"),
            kind = SyncActivityKind.valueOf(o.getString("kind")),
            at = Instant.ofEpochMilli(o.getLong("at")),
            status = SyncActivityStatus.valueOf(o.getString("status")),
            title = o.getString("title"),
            detail = o.stringOrNull("detail"),
            fileName = o.stringOrNull("fileName"),
            counts = RecordCounts(
                customers = o.optInt("customers", 0),
                products = o.optInt("products", 0),
                invoices = o.optInt("invoices", 0),
                receipts = o.optInt("receipts", 0),
            ),
            acknowledged = o.optBoolean("acknowledged", false),
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_ENTRIES = 30
    }
}
