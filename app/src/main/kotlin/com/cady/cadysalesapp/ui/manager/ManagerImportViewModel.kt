package com.cady.cadysalesapp.ui.manager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.sync.IncomingFileHolder
import com.cady.cadysalesapp.data.sync.ManagerSyncService
import com.cady.cadysalesapp.data.sync.RepExportPreview
import com.cady.cadysalesapp.data.sync.RepImportResult
import com.cady.cadysalesapp.data.sync.SyncFileException
import com.cady.cadysalesapp.data.sync.SyncFileService
import com.cady.cadysalesapp.ui.common.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportStage {
    data object Idle : ImportStage
    data class Busy(val message: String) : ImportStage

    /** The file has been read and checked; nothing has been imported yet. */
    data class Preview(val preview: RepExportPreview) : ImportStage
    data class Done(val preview: RepExportPreview, val result: RepImportResult, val share: ShareRequest) : ImportStage
    data class Failed(val message: String) : ImportStage
}

@HiltViewModel
class ManagerImportViewModel @Inject constructor(
    private val managerSyncService: ManagerSyncService,
    private val syncFileService: SyncFileService,
    private val incomingFileHolder: IncomingFileHolder,
) : ViewModel() {

    private val _stage = MutableStateFlow<ImportStage>(ImportStage.Idle)
    val stage: StateFlow<ImportStage> = _stage.asStateFlow()

    init {
        // A file shared into Cady from another app (WhatsApp, a file manager…) lands here
        // for a manager — and it still only gets as far as the preview, never straight in.
        viewModelScope.launch {
            incomingFileHolder.pending.filterNotNull().collect { uri ->
                incomingFileHolder.consume()
                loadPreview(uri)
            }
        }
    }

    fun pick(uri: Uri) {
        viewModelScope.launch { loadPreview(uri) }
    }

    private suspend fun loadPreview(uri: Uri) {
        _stage.value = ImportStage.Busy("جارٍ قراءة الملف وفحصه…")
        _stage.value = try {
            ImportStage.Preview(managerSyncService.previewRepExport(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncFileException) {
            ImportStage.Failed(e.message ?: "ملف غير صالح")
        } catch (e: Exception) {
            ImportStage.Failed(e.message ?: "تعذّرت قراءة الملف")
        }
    }

    fun approve() {
        val current = _stage.value as? ImportStage.Preview ?: return
        viewModelScope.launch {
            _stage.value = ImportStage.Busy("جارٍ الاستيراد وتجهيز تأكيد الاستلام…")
            _stage.value = try {
                val result = managerSyncService.applyRepExport(current.preview)
                ImportStage.Done(
                    preview = current.preview,
                    result = result,
                    share = ShareRequest(
                        uri = syncFileService.shareUri(result.ackFile),
                        mimeType = "application/json",
                        chooserTitle = "إرسال تأكيد الاستلام إلى ${current.preview.sender.displayName}",
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportStage.Failed("تعذّر الاستيراد: ${e.message ?: "خطأ غير متوقع"} — لم يُحفظ شيء.")
            }
        }
    }

    fun reset() {
        _stage.value = ImportStage.Idle
    }
}
