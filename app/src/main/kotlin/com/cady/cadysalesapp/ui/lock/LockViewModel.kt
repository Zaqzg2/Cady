package com.cady.cadysalesapp.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val isBiometricEnabled: StateFlow<Boolean> = authRepository.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun verify(pin: String, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            if (authRepository.verify(pin)) {
                _errorMessage.value = null
                onUnlocked()
            } else {
                _errorMessage.value = "رمز غير صحيح"
            }
        }
    }

    fun tryBiometric(activity: FragmentActivity, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            when (biometricAuthHelper.authenticate(activity)) {
                is BiometricResult.Success -> onUnlocked()
                is BiometricResult.Cancelled -> {} // silently return to the PIN pad, no error needed
                is BiometricResult.Error -> _errorMessage.value = "تعذّر التحقق بالبصمة — استخدم الرمز"
            }
        }
    }
}
