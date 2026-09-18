package com.cady.cadysalesapp.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

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
}
