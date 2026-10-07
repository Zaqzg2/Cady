package com.cady.cadysalesapp.data.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Must match the provider's android:authorities in AndroidManifest.xml. */
const val FILE_PROVIDER_AUTHORITY = "com.cady.cadysalesapp.fileprovider"

/**
 * A content:// URI another app (WhatsApp, a file manager, "Save to…") can read.
 * The file must live under one of the roots declared in res/xml/file_paths.xml.
 */
fun Context.fileProviderUri(file: File): Uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)

/** Keeps a user-facing name safe for use inside a file name (ASCII letters, digits, _ . -). */
fun safeFileToken(raw: String, fallback: String): String {
    val cleaned = raw.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('_', '.', '-')
    return cleaned.ifEmpty { fallback }
}
