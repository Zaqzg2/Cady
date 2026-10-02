package com.cady.cadysalesapp.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/**
 * LocalContext inside Compose is not always the Activity itself — themed
 * contexts and dialogs wrap it. A plain `as? FragmentActivity` then quietly
 * yields null, and anything needing the Activity (the biometric prompt) just
 * never appears. Walking the wrappers finds the real one.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
