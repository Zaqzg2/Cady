package com.cady.cadysalesapp.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.PendingSnapshot
import com.cady.cadysalesapp.data.sync.RecordCounts
import com.cady.cadysalesapp.data.sync.SyncFileService
import com.cady.cadysalesapp.data.sync.SyncService
import com.cady.cadysalesapp.ui.common.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingPreviewUiState(
    val loading: Boolean = true,
    val user: UserAccountEntity? = null,
    val snapshot: PendingSnapshot? = null,
    val exporting: Boolean = false,
    val message: String? = null,
)

/** Read-only look at exactly what an export / push would send, refreshed whenever the pending set changes. */
@HiltViewModel
class SyncPendingPreviewViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val syncService: SyncService,
    private val syncFileService: SyncFileService,
) : ViewModel() {

    private val _state = MutableStateFlow(PendingPreviewUiState())
    val state: StateFlow<PendingPreviewUiState> = _state

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            accountRepository.currentUser
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf<Pair<UserAccountEntity?, RecordCounts>>(Pair(null, RecordCounts()))
                    } else {
                        syncService.observePending(user).map { Pair<UserAccountEntity?, RecordCounts>(user, it) }
                    }
                }
                .collectLatest { (user, _) ->
                    val snapshot = user?.let { syncService.pendingSnapshot(it) }
                    _state.update { it.copy(loading = false, user = user, snapshot = snapshot) }
                }
        }
    }

    fun export() {
        val user = _state.value.user ?: return
        if (_state.value.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, message = null) }
            try {
                val result = syncFileService.exportPending(user)
                if (result == null) {
                    _state.update { it.copy(exporting = false, message = "لا توجد سجلات معلّقة للتصدير") }
                } else {
                    shareChannel.send(
                        ShareRequest(
                            uri = syncFileService.shareUri(result.file),
                            mimeType = "application/json",
                            chooserTitle = "إرسال ملف المزامنة إلى المدير",
                        )
                    )
                    _state.update { it.copy(exporting = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, message = e.message ?: "تعذّر التصدير") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
