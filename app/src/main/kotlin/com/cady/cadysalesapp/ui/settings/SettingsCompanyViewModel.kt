package com.cady.cadysalesapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsCompanyViewModel @Inject constructor(
    private val repository: CompanySettingsRepository,
) : ViewModel() {

    val settings: StateFlow<CompanySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    fun save(name: String, address: String, phone: String, footerText: String) {
        viewModelScope.launch { repository.updateCompanyInfo(name, address, phone, footerText) }
    }
}
