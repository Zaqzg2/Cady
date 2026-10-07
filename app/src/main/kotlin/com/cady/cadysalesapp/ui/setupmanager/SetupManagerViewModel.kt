package com.cady.cadysalesapp.ui.setupmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AccountException
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ManagerCheckResult { CHECKING, SAFE_TO_CREATE, MANAGER_EXISTS, CHECK_UNAVAILABLE }

/**
 * Implements the fix agreed in the review (section 9): unlike the current app,
 * which only shows a warning label, creating a manager here is gated on an
 * actual check — run automatically on load — plus an explicit confirmation
 * the person must tick themselves once that check comes back clear.
 */
@HiltViewModel
class SetupManagerViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _checkResult = MutableStateFlow(ManagerCheckResult.CHECKING)
    val checkResult: StateFlow<ManagerCheckResult> = _checkResult

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    init {
        recheck()
    }

    fun recheck() {
        _checkResult.value = ManagerCheckResult.CHECKING
        viewModelScope.launch {
            _checkResult.value = try {
                if (accountRepository.managerAlreadyExists()) {
                    ManagerCheckResult.MANAGER_EXISTS
                } else {
                    ManagerCheckResult.SAFE_TO_CREATE
                }
            } catch (e: AccountException.ManagerCheckUnavailable) {
                ManagerCheckResult.CHECK_UNAVAILABLE
            }
        }
    }

    fun createManager(username: String, password: String, displayName: String, confirmedNoExistingManager: Boolean, onSuccess: () -> Unit) {
        if (_checkResult.value != ManagerCheckResult.SAFE_TO_CREATE || !confirmedNoExistingManager) {
            _state.value = UiState.Error("أكّد أولًا أنه لا يوجد حساب مدير سابق لهذا العمل")
            return
        }
        if (username.isBlank() || password.isBlank() || displayName.isBlank()) {
            _state.value = UiState.Error("أكمل كل الحقول")
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                accountRepository.createManager(username.trim(), password, displayName.trim())
                _state.value = UiState.Success
                onSuccess()
            } catch (e: AccountException) {
                _state.value = UiState.Error(e.message ?: "تعذّر إنشاء الحساب")
            } catch (e: Exception) {
                _state.value = UiState.Error("تعذّر إنشاء الحساب، حاول مرة أخرى")
            }
        }
    }
}
