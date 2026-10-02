package com.cady.cadysalesapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cady.cadysalesapp.navigation.CadyNavHost
import com.cady.cadysalesapp.ui.lock.LockScreen
import com.cady.cadysalesapp.ui.session.SessionViewModel

/**
 * The navigation graph with the app lock laid over it. The lock is an overlay,
 * not a destination: it appears the moment the app opens (or returns after a
 * while in the background) without touching the back stack, and covers the
 * screen completely and blocks touches until it is unlocked.
 */
@Composable
fun AppRoot(sessionViewModel: SessionViewModel) {
    val startRoute by sessionViewModel.startRoute.collectAsState()
    val locked by sessionViewModel.locked.collectAsState()

    val route = startRoute
    Box(modifier = Modifier.fillMaxSize()) {
        // Until the saved session has been read there is nothing to show yet
        // (a blank frame is better than flashing Login to someone already signed in).
        if (route != null) {
            CadyNavHost(startDestination = route, sessionViewModel = sessionViewModel)
            if (locked) {
                // Back must not slip past the lock into the screen behind it.
                BackHandler(enabled = true) { }
                Surface(modifier = Modifier.fillMaxSize()) {
                    LockScreen(onUnlocked = sessionViewModel::onUnlocked)
                }
            }
        }
    }
}
