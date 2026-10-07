package com.cady.cadysalesapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AppThemeMode
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.ui.theme.ThemeColorPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsAppearanceViewModel @Inject constructor(
    private val repository: CompanySettingsRepository,
) : ViewModel() {

    val settings: StateFlow<CompanySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun setThemeColor(color: ThemeColorPreset) {
        viewModelScope.launch { repository.updateThemeColor(color) }
    }

    fun setHapticOnSave(enabled: Boolean) {
        viewModelScope.launch { repository.updateHapticOnSave(enabled) }
    }
}
