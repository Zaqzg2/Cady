package com.cady.cadysalesapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.printing.BluetoothPrinterBridge
import com.cady.cadysalesapp.data.printing.ThermalPrintService
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.data.repository.PdfLayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionCheckState {
    data object Idle : ConnectionCheckState
    data object Checking : ConnectionCheckState
    data object Verified : ConnectionCheckState
    data class Failed(val message: String) : ConnectionCheckState
}

@HiltViewModel
class SettingsPrintingViewModel @Inject constructor(
    private val thermalPrintService: ThermalPrintService,
    private val companySettingsRepository: CompanySettingsRepository,
) : ViewModel() {

    val settings: StateFlow<CompanySettings> = companySettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    val savedPrinterMac: StateFlow<String?> = thermalPrintService.savedPrinterMac
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pairedDevices = MutableStateFlow<List<BluetoothPrinterBridge.PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothPrinterBridge.PairedDevice>> = _pairedDevices

    private val _connectionCheck = MutableStateFlow<ConnectionCheckState>(ConnectionCheckState.Idle)
    val connectionCheck: StateFlow<ConnectionCheckState> = _connectionCheck

    fun hasBluetoothPermission(): Boolean = thermalPrintService.hasBluetoothPermission()
    val requiredBluetoothPermissions: Array<String> get() = thermalPrintService.requiredPermissions

    fun loadPairedDevices() {
        viewModelScope.launch {
            _pairedDevices.value = thermalPrintService.pairedDevices()
        }
    }

    fun selectPrinter(mac: String) {
        viewModelScope.launch {
            thermalPrintService.savePrinterMac(mac)
            checkConnection(mac)
        }
    }

    fun checkConnection(mac: String) {
        _connectionCheck.value = ConnectionCheckState.Checking
        viewModelScope.launch {
            val ok = thermalPrintService.verifyConnection(mac)
            _connectionCheck.value = if (ok) {
                ConnectionCheckState.Verified
            } else {
                ConnectionCheckState.Failed(thermalPrintService.lastError ?: "تعذّر التحقق من الاتصال")
            }
        }
    }

    fun setLayoutMode(mode: PdfLayoutMode) {
        viewModelScope.launch { companySettingsRepository.updatePrintLayoutMode(mode) }
    }

    fun setPrintFontScale(scale: Float) {
        viewModelScope.launch { companySettingsRepository.updatePrintFontScale(scale) }
    }

    fun setPrintLineSpacingExtra(extra: Float) {
        viewModelScope.launch { companySettingsRepository.updatePrintLineSpacingExtra(extra) }
    }

    fun setBlackThreshold(threshold: Int) {
        viewModelScope.launch { companySettingsRepository.updatePrintBlackThreshold(threshold) }
    }
}
