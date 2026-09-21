package com.cady.cadysalesapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Appearance settings screen writes to CompanySettingsRepository;
    MainActivity reads it back through here so a theme-color or dark-mode
    change actually applies app-wide, not just within that one screen. */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    repository: CompanySettingsRepository,
) : ViewModel() {
    val settings: StateFlow<CompanySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())
}
