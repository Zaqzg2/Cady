package com.cady.cadysalesapp.ui.weborders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.weborders.BotConfig
import com.cady.cadysalesapp.data.weborders.BotConfigRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BotSettingsUiState(
    val loading: Boolean = true,
    /** What the document holds now; null when the manager has not saved anything yet. */
    val saved: BotConfig? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BotSettingsViewModel @Inject constructor(
    private val repository: BotConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BotSettingsUiState())
    val state: StateFlow<BotSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe()
                .catch { e ->
                    _state.update { it.copy(loading = false, error = readError(e)) }
                }
                .collect { snapshot ->
                    _state.update { it.copy(loading = false, saved = snapshot.config, error = null) }
                }
        }
    }

    fun save(config: BotConfig) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val note = try {
                if (repository.save(config)) {
                    "تم الحفظ — يسري على المحادثات الجديدة خلال دقيقة"
                } else {
                    "لا يوجد اتصال الآن — سيُحفظ تلقائيًا عند عودة الإنترنت"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isDenied(e)) {
                    "لا توجد صلاحية للحفظ — انشر قواعد Firestore الجديدة (bot_config)"
                } else {
                    "تعذّر الحفظ — تحقّق من الإنترنت وأعد المحاولة"
                }
            }
            _state.update { it.copy(saving = false, message = note) }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun readError(e: Throwable): String =
        if (isDenied(e)) {
            "لا توجد صلاحية لقراءة الإعدادات — انشر قواعد Firestore الجديدة (bot_config)"
        } else {
            "تعذّر تحميل الإعدادات — تحقّق من الإنترنت"
        }

    private fun isDenied(e: Throwable): Boolean =
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}
