package com.cady.cadysalesapp.ui.settings

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AuthRepository
import com.cady.cadysalesapp.data.repository.BiometricAuthHelper
import com.cady.cadysalesapp.data.repository.BiometricAvailability
import com.cady.cadysalesapp.data.repository.BiometricResult
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

    fun biometricAvailability(activity: FragmentActivity): BiometricAvailability =
        biometricAuthHelper.availability(activity)

    /**
     * Turning fingerprint unlock on now requires one real, successful fingerprint
     * check first — so the switch can only ever be "on" for a sensor that works,
     * instead of being flipped on and then silently never doing anything.
     */
    fun enableBiometric(activity: FragmentActivity, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val result = biometricAuthHelper.authenticate(
                activity,
                title = "تفعيل البصمة",
                subtitle = "ضع إصبعك على المستشعر للتأكد من أنها تعمل",
            )
            when (result) {
                is BiometricResult.Success -> authRepository.setBiometricEnabled(true)
                is BiometricResult.Cancelled -> {}
                is BiometricResult.Error -> onFailure(result.message)
            }
        }
    }

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
