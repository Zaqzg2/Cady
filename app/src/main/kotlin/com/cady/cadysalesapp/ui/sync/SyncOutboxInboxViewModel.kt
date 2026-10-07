package com.cady.cadysalesapp.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityKind
import com.cady.cadysalesapp.data.sync.SyncFileService
import com.cady.cadysalesapp.data.sync.SyncService
import com.cady.cadysalesapp.ui.common.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter(val label: String) {
    ALL("الكل"),
    OUTGOING("الصادر"),
    INCOMING("الوارد"),
    ONLINE("عبر الإنترنت"),
}

/** One row of the log plus whether its exported file is still on disk (so "resend" can be offered). */
data class LogRow(val entry: SyncActivityEntry, val canResend: Boolean)

data class OutboxInboxUiState(
    val filter: LogFilter = LogFilter.ALL,
    val rows: List<LogRow> = emptyList(),
    val totalCount: Int = 0,
)

@HiltViewModel
class SyncOutboxInboxViewModel @Inject constructor(
    private val syncService: SyncService,
    private val syncFileService: SyncFileService,
) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)

    val state: StateFlow<OutboxInboxUiState> = combine(syncService.activity, filter) { log, selected ->
        val visible = log.filter { entry ->
            when (selected) {
                LogFilter.ALL -> true
                LogFilter.OUTGOING -> entry.kind == SyncActivityKind.MANUAL_EXPORT
                LogFilter.INCOMING -> entry.kind == SyncActivityKind.MANUAL_IMPORT
                LogFilter.ONLINE -> entry.kind == SyncActivityKind.FIREBASE_SYNC
            }
        }
        OutboxInboxUiState(
            filter = selected,
            rows = visible.map { LogRow(it, canResend = syncFileService.outboxFileFor(it) != null) },
            totalCount = log.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutboxInboxUiState())

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    fun select(newFilter: LogFilter) {
        filter.value = newFilter
    }

    fun resend(entry: SyncActivityEntry) {
        val file = syncFileService.outboxFileFor(entry) ?: return
        viewModelScope.launch {
            shareChannel.send(
                ShareRequest(
                    uri = syncFileService.shareUri(file),
                    mimeType = "application/json",
                    chooserTitle = "إعادة إرسال ملف المزامنة",
                )
            )
        }
    }
}
