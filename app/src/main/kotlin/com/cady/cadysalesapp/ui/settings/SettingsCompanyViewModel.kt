package com.cady.cadysalesapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Idle -> Saving -> Saved is what SettingsCompanyScreen's save button watches
    to show a spinner and then an explicit confirmation — the whole reason the
    save used to look unresponsive was that nothing on screen ever changed
    after tapping it, so a rep would back out before the write even finished. */
sealed interface SettingsSaveState {
    data object Idle : SettingsSaveState
    data object Saving : SettingsSaveState
    data object Saved : SettingsSaveState
}

@HiltViewModel
class SettingsCompanyViewModel @Inject constructor(
    private val repository: CompanySettingsRepository,
) : ViewModel() {

    val settings: StateFlow<CompanySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    private val _saveState = MutableStateFlow<SettingsSaveState>(SettingsSaveState.Idle)
    val saveState: StateFlow<SettingsSaveState> = _saveState

    /** [signaturePath] is only written when non-null (the rep actually drew a
        new default this time) — leaving the pad blank keeps whatever default
        was already saved, rather than silently clearing it. */
    fun save(
        name: String,
        address: String,
        phone: String,
        footerText: String,
        repDisplayName: String,
        signaturePath: String?,
    ) {
        viewModelScope.launch {
            _saveState.value = SettingsSaveState.Saving
            repository.updateCompanyInfo(name, address, phone, footerText)
            repository.updateRepDisplayName(repDisplayName)
            if (signaturePath != null) {
                repository.updateRepSignaturePath(signaturePath)
            }
            _saveState.value = SettingsSaveState.Saved
        }
    }

    /** Saves immediately on picking a new image — there's no "draft" state for
        a logo the way there is for the text fields above, so there's nothing
        gained by waiting for the main حفظ button. */
    fun updateLogo(path: String?) {
        viewModelScope.launch { repository.updateCompanyLogoPath(path) }
    }

    fun clearDefaultSignature() {
        viewModelScope.launch { repository.updateRepSignaturePath(null) }
    }

    fun acknowledgeSaved() {
        _saveState.value = SettingsSaveState.Idle
    }
}
