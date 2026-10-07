package com.cady.cadysalesapp.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Everything needed to hand a file (already exposed through the FileProvider) to another app. */
data class ShareRequest(val uri: Uri, val mimeType: String, val chooserTitle: String)

fun Context.launchShare(request: ShareRequest) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = request.mimeType
        putExtra(Intent.EXTRA_STREAM, request.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, request.chooserTitle))
}
