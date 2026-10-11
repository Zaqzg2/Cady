package com.cady.cadysalesapp.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.AuthRepository
import com.cady.cadysalesapp.data.repository.BiometricAuthHelper
import com.cady.cadysalesapp.data.repository.BiometricResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val biometricAuthHelper: BiometricAuthHelper,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val isBiometricEnabled: StateFlow<Boolean> = authRepository.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * The pad checks quietly while the person is still typing (any length from 4 up
     * can be the whole PIN), so a wrong *prefix* must not flash an error —
     * [showErrorIfWrong] is true only for a deliberate confirm or a full-length entry.
     */
    fun verify(
        pin: String,
        showErrorIfWrong: Boolean,
        onUnlocked: () -> Unit,
        onWrong: () -> Unit = {},
    ) {
        viewModelScope.launch {
            if (authRepository.verify(pin)) {
                _errorMessage.value = null
                onUnlocked()
            } else if (showErrorIfWrong) {
                _errorMessage.value = "رمز غير صحيح"
                onWrong()
            }
        }
    }

    private var biometricJob: Job? = null

    /**
     * After the person dismissed the sheet — or it failed for real — the lock screen must not keep raising it
     * by itself (that would be a loop of sheets nobody asked for). A tap on the icon always asks again, and
     * leaving the app and coming back starts afresh.
     */
    private var autoPromptSuppressed = false

    /** @param automatic true for the sheet the lock screen raises on its own; false for a tap on the icon. */
    fun tryBiometric(activity: FragmentActivity, automatic: Boolean, onUnlocked: () -> Unit) {
        if (automatic && autoPromptSuppressed) return
        if (!automatic) autoPromptSuppressed = false
        // A sheet is already on screen (the automatic one, then a tap on the icon): never stack a second.
        if (biometricJob?.isActive == true) return
        _errorMessage.value = null
        biometricJob = viewModelScope.launch {
            var interrupted = 0
            while (true) {
                when (val result = biometricAuthHelper.authenticate(activity)) {
                    is BiometricResult.Success -> {
                        onUnlocked()
                        return@launch
                    }
                    is BiometricResult.Cancelled -> {
                        autoPromptSuppressed = true // the person chose the PIN pad: no message, no second sheet
                        return@launch
                    }
                    is BiometricResult.Interrupted -> {
                        // The system cancelled the sheet (the window was not focused yet, the sensor was
                        // busy). Ask again, twice at most — then say so instead of doing nothing.
                        interrupted++
                        if (interrupted > 2) {
                            autoPromptSuppressed = true
                            _errorMessage.value = "تعذّر فتح نافذة البصمة — ${result.message}. اضغط أيقونة البصمة للمحاولة أو أدخل الرمز."
                            return@launch
                        }
                        delay(700)
                    }
                    is BiometricResult.Error -> {
                        autoPromptSuppressed = true
                        _errorMessage.value = "تعذّر التحقق بالبصمة — ${result.message}"
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * The lock screen left the front (the app went to the background) or left the screen: close any sheet that
     * is still up and let the next visit ask afresh. Without this a sheet the system dismissed WITHOUT telling
     * us would leave a job suspended forever, and the "one sheet at a time" guard above would then refuse every
     * later attempt — a fingerprint that works some times and not others.
     */
    fun resetBiometric() {
        biometricJob?.cancel()
        biometricJob = null
        autoPromptSuppressed = false
    }

    /**
     * The way out when the PIN is forgotten: sign out and drop the PIN (and the
     * fingerprint switch that depends on it). Getting back in then needs the
     * account's username and password, so this does not weaken anything — and
     * because the PIN is gone, the next sign-in isn't locked out by it again;
     * a new one is set from Settings → الخصوصية.
     */
    fun forgotPin() {
        viewModelScope.launch {
            authRepository.clearPassword()
            authRepository.setBiometricEnabled(false)
            accountRepository.logout()
        }
    }
}
