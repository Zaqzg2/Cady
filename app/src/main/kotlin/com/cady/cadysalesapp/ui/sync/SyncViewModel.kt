package com.cady.cadysalesapp.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.IncomingFileHolder
import com.cady.cadysalesapp.data.sync.RecordCounts
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityKind
import com.cady.cadysalesapp.data.sync.SyncActivityStatus
import com.cady.cadysalesapp.data.sync.SyncFileService
import com.cady.cadysalesapp.data.sync.SyncImportOutcome
import com.cady.cadysalesapp.data.sync.SyncService
import com.cady.cadysalesapp.ui.common.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed interface SyncDialog {
    data class Message(val title: String, val text: String) : SyncDialog
    data class ExportReady(val counts: RecordCounts, val fileName: String, val share: ShareRequest) : SyncDialog
    data class ImportDone(val outcome: SyncImportOutcome) : SyncDialog
}

data class SyncUiState(
    val user: UserAccountEntity? = null,
    val pending: RecordCounts = RecordCounts(),
    val isOnline: Boolean = true,
    val syncing: Boolean = false,
    val progress: SyncService.Progress? = null,
    /** The newest "sync over the internet" attempt, whatever its outcome. */
    val lastRun: SyncActivityEntry? = null,
    val lastSuccessAt: Instant? = null,
    val recent: List<SyncActivityEntry> = emptyList(),
    /** A shared file waiting for the person's yes/no. */
    val incomingFile: Uri? = null,
    val busy: String? = null,
    val dialog: SyncDialog? = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val syncService: SyncService,
    private val syncFileService: SyncFileService,
    private val incomingFileHolder: IncomingFileHolder,
) : ViewModel() {

    private data class Core(val user: UserAccountEntity?, val pending: RecordCounts, val online: Boolean)
    private data class Run(val syncing: Boolean, val progress: SyncService.Progress?, val log: List<SyncActivityEntry>)
    private data class Local(val busy: String? = null, val dialog: SyncDialog? = null)

    private val local = MutableStateFlow(Local())

    private val core = combine(
        accountRepository.currentUser,
        accountRepository.currentUser.flatMapLatest { user ->
            if (user == null) flowOf(RecordCounts()) else syncService.observePending(user)
        },
        syncService.isOnline,
    ) { user, pending, online -> Core(user, pending, online) }

    private val run = combine(syncService.isSyncing, syncService.progress, syncService.activity) { syncing, progress, log ->
        Run(syncing, progress, log)
    }

    val state: StateFlow<SyncUiState> = combine(core, run, local, incomingFileHolder.pending) { c, r, l, incoming ->
        val firebaseRuns = r.log.filter { it.kind == SyncActivityKind.FIREBASE_SYNC }
        SyncUiState(
            user = c.user,
            pending = c.pending,
            isOnline = c.online,
            syncing = r.syncing,
            progress = r.progress,
            lastRun = firebaseRuns.firstOrNull(),
            lastSuccessAt = firebaseRuns.firstOrNull { it.status == SyncActivityStatus.SUCCESS }?.at
                ?: c.user?.lastSyncAt,
            recent = r.log.take(RECENT_COUNT),
            incomingFile = incoming,
            busy = l.busy,
            dialog = l.dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun syncNow() {
        val user = state.value.user ?: return
        viewModelScope.launch {
            // The run itself (and its log entry) lives in SyncService, so it finishes even if
            // this screen is left; a null result just means one is already running.
            syncService.syncNow(user)
        }
    }

    fun exportPending() {
        val user = state.value.user ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ تجهيز الملف…") }
            try {
                val result = syncFileService.exportPending(user)
                val dialog: SyncDialog = if (result == null) {
                    SyncDialog.Message("لا يوجد ما يُصدَّر", "كل سجلاتك مرفوعة أو مؤكَّدة — لا توجد سجلات معلّقة.")
                } else {
                    SyncDialog.ExportReady(
                        counts = result.entry.counts,
                        fileName = result.file.name,
                        share = ShareRequest(
                            uri = syncFileService.shareUri(result.file),
                            mimeType = "application/json",
                            chooserTitle = "إرسال ملف المزامنة إلى المدير",
                        ),
                    )
                }
                local.update { it.copy(busy = null, dialog = dialog) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update {
                    it.copy(busy = null, dialog = SyncDialog.Message("تعذّر التصدير", e.message ?: "خطأ غير متوقع"))
                }
            }
        }
    }

    fun importFile(uri: Uri) {
        val user = state.value.user ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ قراءة الملف…") }
            try {
                val outcome = syncFileService.importFrom(uri, user)
                local.update { it.copy(busy = null, dialog = SyncDialog.ImportDone(outcome)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update {
                    it.copy(busy = null, dialog = SyncDialog.Message("تعذّر الاستيراد", e.message ?: "خطأ غير متوقع"))
                }
            }
        }
    }

    fun acceptIncoming() {
        val uri = incomingFileHolder.consume() ?: return
        importFile(uri)
    }

    fun dismissIncoming() = incomingFileHolder.clear()

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    private companion object {
        const val RECENT_COUNT = 5
    }
}
