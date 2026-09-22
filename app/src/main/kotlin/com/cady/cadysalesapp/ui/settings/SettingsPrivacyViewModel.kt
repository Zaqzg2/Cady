package com.cady.cadysalesapp.ui.settings

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AuthRepository
import com.cady.cadysalesapp.data.repository.BiometricAuthHelper
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val isPasswordSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val hideAmountsInRecents: Boolean = false,
)

@HiltViewModel
class SettingsPrivacyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val companySettingsRepository: CompanySettingsRepository,
    private val biometricAuthHelper: BiometricAuthHelper,
) : ViewModel() {

    val uiState: StateFlow<PrivacyUiState> = combine(
        authRepository.isPasswordSet,
        authRepository.isBiometricEnabled,
        companySettingsRepository.settings,
    ) { passwordSet, biometricEnabled, company ->
        PrivacyUiState(passwordSet, biometricEnabled, company.hideAmountsInRecents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrivacyUiState())

    fun isBiometricAvailable(activity: FragmentActivity): Boolean = biometricAuthHelper.isBiometricAvailable(activity)

    fun setAppLockPassword(pin: String) {
        viewModelScope.launch { authRepository.setPassword(pin) }
    }

    fun clearAppLockPassword() {
        viewModelScope.launch {
            authRepository.clearPassword()
            authRepository.setBiometricEnabled(false) // biometric unlock only makes sense with a PIN as fallback
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { authRepository.setBiometricEnabled(enabled) }
    }

    fun setHideAmountsInRecents(enabled: Boolean) {
        viewModelScope.launch { companySettingsRepository.updateHideAmountsInRecents(enabled) }
    }
}
