package com.cady.cadysalesapp.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.AuthRepository
import com.cady.cadysalesapp.data.repository.BiometricAuthHelper
import com.cady.cadysalesapp.data.repository.BiometricResult
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun tryBiometric(activity: FragmentActivity, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            when (val result = biometricAuthHelper.authenticate(activity)) {
                is BiometricResult.Success -> onUnlocked()
                is BiometricResult.Cancelled -> {} // silently return to the PIN pad, no error needed
                is BiometricResult.Error -> _errorMessage.value = "تعذّر التحقق بالبصمة — ${result.message}"
            }
        }
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
