package com.cady.cadysalesapp.data.files

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * ONE place on the phone for every file the app hands out — documents (PDF), sync / update / receipt-
 * confirmation files, backups, CSV exports: the folder **Download/Cady** inside the device's own Downloads.
 *
 * It uses MediaStore, so on Android 10+ it needs no storage permission at all and the folder is created
 * the first time something is saved into it. Android 7–9 would need the legacy storage permission; there
 * the screens keep their old "pick where to save" behaviour ([isSupported] is false).
 *
 * Saving the same name twice overwrites the earlier copy instead of piling up "name (1)", "name (2)"…
 */
object DownloadsStorage {
    /** The folder created inside Downloads. */
    const val FOLDER = "Cady"

    /** How the place is named to the person in messages. */
    const val FOLDER_LABEL = "مجلد Cady في التنزيلات"

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // Copies started from a share button must finish even if the screen is left straight away.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Copies [source] to Download/Cady/[displayName]. Throws if the copy could not be made. */
    suspend fun saveFile(context: Context, source: File, displayName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            // An explicit SDK check right before the API-29 call, in the form Android lint recognises.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IOException(UNSUPPORTED)
            writeToDownloads(context, displayName, mimeType) { out -> source.inputStream().use { it.copyTo(out) } }
        }

    /** Same, for a content Uri (the app's own FileProvider Uris are readable by the app itself). */
    suspend fun saveFromUri(context: Context, source: Uri, displayName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IOException(UNSUPPORTED)
            writeToDownloads(context, displayName, mimeType) { out ->
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("تعذّر قراءة الملف")
                input.use { it.copyTo(out) }
            }
        }

    /**
     * The hook behind every "share" button: a copy of the file goes to Download/Cady, then the share sheet
     * opens as usual. Fire-and-forget — a failed copy never blocks sharing.
     */
    fun saveSharedCopyAsync(context: Context, source: Uri, mimeType: String?) {
        if (!isSupported) return
        val app = context.applicationContext
        scope.launch {
            try {
                val name = displayNameOf(app, source) ?: return@launch
                val type = mimeType ?: app.contentResolver.getType(source) ?: "application/octet-stream"
                saveFromUri(app, source, name, type)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "حُفظت نسخة من الملف في $FOLDER_LABEL", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // Best effort only.
            }
        }
    }

    private const val UNSUPPORTED = "الحفظ المباشر في التنزيلات يتطلب أندرويد 10 أو أحدث"

    private fun displayNameOf(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Uri {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "-")

        // The same file saved again replaces the earlier copy in place.
        findExisting(resolver, safeName, relativePath)?.let { existing ->
            try {
                val out = resolver.openOutputStream(existing, "wt") ?: throw IOException("تعذّر فتح الملف للكتابة")
                out.use { write(it) }
                return existing
            } catch (e: SecurityException) {
                // Left behind by an earlier install — not ours to overwrite; add a fresh one below.
            }
        }

        val values = ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("تعذّر إنشاء الملف في مجلد التنزيلات")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("تعذّر فتح الملف للكتابة")
            out.use { write(it) }
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findExisting(resolver: ContentResolver, displayName: String, relativePath: String): Uri? {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, "$relativePath/")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(BaseColumns._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }
}
