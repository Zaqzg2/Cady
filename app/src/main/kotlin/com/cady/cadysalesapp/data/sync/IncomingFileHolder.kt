package com.cady.cadysalesapp.data.sync

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A sync file another app handed to Cady (shared from WhatsApp, opened from a file manager…).
 * MainActivity drops it here; the navigation host brings the person to the sync screen, which
 * asks before importing anything. Nothing is ever imported without that confirmation.
 */
@Singleton
class IncomingFileHolder @Inject constructor() {
    private val state = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = state.asStateFlow()

    fun offer(uri: Uri) {
        state.value = uri
    }

    fun clear() {
        state.value = null
    }

    fun consume(): Uri? {
        val uri = state.value
        state.value = null
        return uri
    }
}
