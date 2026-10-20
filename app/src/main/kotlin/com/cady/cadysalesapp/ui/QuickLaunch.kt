package com.cady.cadysalesapp.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the launcher shortcuts, the home-screen widget and the website-order notification ask for. They start MainActivity with one of
 * these actions; MainActivity drops it in [QuickLaunchHolder] and the navigation host opens the
 * screen as soon as someone is signed in (a locked app opens it behind the lock, which stays on top).
 */
object QuickLaunch {
    const val ACTION_NEW_INVOICE = "com.cady.cadysalesapp.action.NEW_INVOICE"
    const val ACTION_NEW_RECEIPT = "com.cady.cadysalesapp.action.NEW_RECEIPT"
    const val ACTION_NEW_CASH_SALE = "com.cady.cadysalesapp.action.NEW_CASH_SALE"

    /** Tapped on the "new website order" notification: opens the manager's orders screen (ignored for a rep). */
    const val ACTION_OPEN_WEB_ORDERS = "com.cady.cadysalesapp.action.OPEN_WEB_ORDERS"

    val ALL = setOf(ACTION_NEW_INVOICE, ACTION_NEW_RECEIPT, ACTION_NEW_CASH_SALE, ACTION_OPEN_WEB_ORDERS)
}

@Singleton
class QuickLaunchHolder @Inject constructor() {
    private val state = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = state.asStateFlow()

    fun offer(action: String) {
        if (action in QuickLaunch.ALL) state.value = action
    }

    fun clear() {
        state.value = null
    }
}

/** Lets the navigation host notice that a shortcut/widget request is waiting. */
@HiltViewModel
class QuickLaunchViewModel @Inject constructor(
    private val holder: QuickLaunchHolder,
) : ViewModel() {
    val pending: StateFlow<String?> = holder.pending

    fun consume() = holder.clear()
}
