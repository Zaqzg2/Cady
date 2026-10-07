package com.cady.cadysalesapp.ui.session

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.AuthRepository
import com.cady.cadysalesapp.navigation.CadyDestination
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * One place that knows who is signed in and whether the app lock is covering the
 * screen. Scoped to the Activity (MainActivity owns it), so it outlives every
 * screen and sees the whole app moving to the background and back.
 *
 * The role (manager / rep) is simply the signed-in account's role — one login
 * screen, one app, and the account's password decides which one you get.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private companion object {
        /** Time in the background after which the lock comes back. */
        const val LOCK_AFTER_BACKGROUND_MS = 30_000L

        /** How often (at most) the server is asked whether this account was switched off. */
        const val ACTIVE_CHECK_MIN_INTERVAL_MS = 60_000L
    }

    /** Null until the saved session has been read; then the route the app starts on. */
    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    private val _currentUser = MutableStateFlow<UserAccountEntity?>(null)
    val currentUser: StateFlow<UserAccountEntity?> = _currentUser.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** A message the whole app should show once (e.g. "the manager suspended this account"). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var backgroundedAt = 0L
    private var lastActiveCheckAt = 0L

    init {
        viewModelScope.launch {
            accountRepository.currentUser.collect { user ->
                _currentUser.value = user
                if (_startRoute.value == null) {
                    // First reading of the saved session = a cold start.
                    // A saved local account only counts as "still signed in" while
                    // the cloud session behind it is alive and belongs to the same
                    // account; otherwise (e.g. the last login happened offline)
                    // the person lands on Login exactly as before, so nothing is
                    // ever pushed to Firestore without a valid sign-in.
                    val cloudUser = if (user != null) settledCloudUser() else null
                    val sessionAlive = user != null && cloudUser != null && cloudUser.uid == user.id
                    // The lock flag is set before the route so the first frame
                    // already has the lock over the screen — never a flash of data.
                    _locked.value = sessionAlive && authRepository.isPasswordSetNow()
                    _startRoute.value =
                        if (sessionAlive) CadyDestination.Home.route else CadyDestination.Login.route
                    if (sessionAlive) verifyAccountStillActive()
                } else if (user == null) {
                    _locked.value = false // signed out: there is nothing left to protect
                }
            }
        }
    }

    /**
     * Firebase restores the saved cloud sign-in itself at startup; on a slow phone that can
     * still be in progress when this runs, and reading `currentUser` too early would wrongly
     * send a signed-in person to Login. Ask for it first, and if it isn't there yet wait for
     * the SDK's first report (it answers at once when truly signed out).
     */
    private suspend fun settledCloudUser(): FirebaseUser? {
        firebaseAuth.currentUser?.let { return it }
        return withTimeoutOrNull(2_000L) {
            callbackFlow<FirebaseUser?> {
                val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
                firebaseAuth.addAuthStateListener(listener)
                awaitClose { firebaseAuth.removeAuthStateListener(listener) }
            }.first()
        }
    }

    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        verifyAccountStillActive()
        val since = backgroundedAt
        backgroundedAt = 0L
        if (since == 0L) return // the very first start is handled in init
        if (SystemClock.elapsedRealtime() - since < LOCK_AFTER_BACKGROUND_MS) return
        if (_currentUser.value == null) return
        viewModelScope.launch {
            if (authRepository.isPasswordSetNow()) _locked.value = true
        }
    }

    /**
     * "Suspend the rep" used to stop only the NEXT login; a session that was already open kept
     * working. Now the server is asked when the app opens and whenever it comes back to the
     * screen (at most once a minute): if it says the account is off, this device signs out and
     * says why. An unreachable server never signs anyone out (see checkStillActive).
     */
    private fun verifyAccountStillActive() {
        val user = _currentUser.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastActiveCheckAt != 0L && now - lastActiveCheckAt < ACTIVE_CHECK_MIN_INTERVAL_MS) return
        lastActiveCheckAt = now
        viewModelScope.launch {
            if (accountRepository.checkStillActive(user.id) == false) {
                _notice.value = "تم إيقاف هذا الحساب من قبل المدير — تواصل معه لإعادة التفعيل."
                accountRepository.logout()
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun onUnlocked() {
        _locked.value = false
    }

    fun logout() {
        viewModelScope.launch { accountRepository.logout() }
    }
}
