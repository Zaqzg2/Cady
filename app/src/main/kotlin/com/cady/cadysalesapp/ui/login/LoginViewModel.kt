package com.cady.cadysalesapp.ui.login

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

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = UiState.Error("أدخل اسم المستخدم وكلمة المرور")
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                accountRepository.login(username.trim(), password)
                _state.value = UiState.Success
                onSuccess()
            } catch (e: AccountException) {
                _state.value = UiState.Error(e.message ?: "تعذّر تسجيل الدخول")
            } catch (e: Exception) {
                // Surfaces the real exception message while we're still actively
                // testing against a real Firebase project — a silent generic
                // message here is exactly what cost a round-trip just now.
                _state.value = UiState.Error("تعذّر تسجيل الدخول: ${e.message ?: e::class.simpleName}")
            }
        }
    }
}
