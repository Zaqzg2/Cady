package com.cady.cadysalesapp.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cady.cadysalesapp.data.files.DownloadsStorage

/** Everything needed to hand a file (already exposed through the FileProvider) to another app. */
data class ShareRequest(val uri: Uri, val mimeType: String, val chooserTitle: String)

/**
 * Opens the share sheet — and keeps a copy of the file in Download/Cady first, so every file the app
 * hands out (sync and update files, receipt confirmations, backups, CSV exports) can also be found in
 * the one folder on the phone, whether or not the share was completed.
 */
fun Context.launchShare(request: ShareRequest) {
    DownloadsStorage.saveSharedCopyAsync(this, request.uri, request.mimeType)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = request.mimeType
        putExtra(Intent.EXTRA_STREAM, request.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, request.chooserTitle))
}
