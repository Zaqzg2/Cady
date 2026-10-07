package com.cady.cadysalesapp.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.backup.AutoBackupFrequency
import com.cady.cadysalesapp.data.backup.AutoBackupScheduler
import com.cady.cadysalesapp.data.backup.BackupException
import com.cady.cadysalesapp.data.backup.BackupInfo
import com.cady.cadysalesapp.data.backup.BackupKind
import com.cady.cadysalesapp.data.backup.BackupPreview
import com.cady.cadysalesapp.data.backup.BackupService
import com.cady.cadysalesapp.data.backup.BackupSettingsRepository
import com.cady.cadysalesapp.data.backup.CsvDataset
import com.cady.cadysalesapp.data.backup.CsvException
import com.cady.cadysalesapp.data.backup.CsvImportPreview
import com.cady.cadysalesapp.data.backup.CsvService
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.files.fileProviderUri
import com.cady.cadysalesapp.ui.common.ShareRequest
import com.cady.cadysalesapp.ui.common.summaryText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface BackupDialog {
    /** [staged] = a copy the person picked from outside the app (cache) — deleted once the dialog closes. */
    data class ConfirmRestore(
        val file: File,
        val staged: Boolean,
        val preview: BackupPreview,
        val ownerMismatch: Boolean,
    ) : BackupDialog

    data class ConfirmDelete(val info: BackupInfo) : BackupDialog
    data object PickCsvExport : BackupDialog
    data object PickCsvImport : BackupDialog
    data class CsvImportReady(val preview: CsvImportPreview) : BackupDialog
    data class Message(val title: String, val text: String) : BackupDialog
}

data class BackupUiState(
    val user: UserAccountEntity? = null,
    val backups: List<BackupInfo> = emptyList(),
    val listLoaded: Boolean = false,
    val autoEnabled: Boolean = false,
    val autoFrequency: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    val busy: String? = null,
    val dialog: BackupDialog? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val backupService: BackupService,
    private val csvService: CsvService,
    private val backupSettings: BackupSettingsRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private data class Local(
        val backups: List<BackupInfo> = emptyList(),
        val listLoaded: Boolean = false,
        val busy: String? = null,
        val dialog: BackupDialog? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<BackupUiState> = combine(
        accountRepository.currentUser,
        backupSettings.autoBackupEnabled,
        backupSettings.autoBackupFrequency,
        local,
    ) { user, enabled, frequency, l ->
        BackupUiState(
            user = user,
            backups = l.backups,
            listLoaded = l.listLoaded,
            autoEnabled = enabled,
            autoFrequency = frequency,
            busy = l.busy,
            dialog = l.dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val list = backupService.listBackups()
                local.update { it.copy(backups = list, listLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(listLoaded = true) }
            }
        }
    }

    // ------------------------------------------------------------------ create / auto

    fun createBackupNow() {
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ إنشاء النسخة الاحتياطية…") }
            try {
                val info = backupService.createBackup(BackupKind.MANUAL)
                val list = backupService.listBackups()
                local.update {
                    it.copy(
                        busy = null,
                        backups = list,
                        dialog = BackupDialog.Message(
                            "تم إنشاء النسخة",
                            "${info.counts?.summaryText().orEmpty()}\n\nللحماية من حذف التطبيق أو ضياع الجهاز، شارك النسخة أو احفظها خارج التطبيق.",
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر إنشاء النسخة", e)
            }
        }
    }

    fun setAutoBackup(enabled: Boolean, frequency: AutoBackupFrequency) {
        viewModelScope.launch {
            backupSettings.setAutoBackup(enabled, frequency)
            AutoBackupScheduler.apply(appContext, enabled, frequency)
        }
    }

    // ------------------------------------------------------------------ share / save / delete

    fun share(info: BackupInfo) {
        viewModelScope.launch {
            shareChannel.send(
                ShareRequest(
                    uri = backupService.shareUri(info.file),
                    mimeType = "application/zip",
                    chooserTitle = "مشاركة النسخة الاحتياطية",
                )
            )
        }
    }

    fun saveTo(fileName: String, destination: Uri) {
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ حفظ النسخة…") }
            try {
                // Only names from our own folder: never a path the UI (or a stale state) made up.
                val file = File(backupService.backupDir, File(fileName).name)
                if (!file.isFile) throw BackupException("النسخة لم تعد موجودة")
                backupService.copyTo(file, destination)
                local.update { it.copy(busy = null, dialog = BackupDialog.Message("تم الحفظ", "حُفظت النسخة في المكان الذي اخترته.")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر حفظ النسخة", e)
            }
        }
    }

    fun requestDelete(info: BackupInfo) = local.update { it.copy(dialog = BackupDialog.ConfirmDelete(info)) }

    fun confirmDelete() {
        val dialog = local.value.dialog as? BackupDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            local.update { it.copy(dialog = null) }
            try {
                backupService.delete(dialog.info.file)
            } finally {
                refresh()
            }
        }
    }

    // ------------------------------------------------------------------ restore

    fun requestRestore(info: BackupInfo) {
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ فحص النسخة…") }
            try {
                val preview = backupService.previewOf(info.file)
                local.update {
                    it.copy(
                        busy = null,
                        dialog = BackupDialog.ConfirmRestore(info.file, staged = false, preview, mismatch(preview)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر فحص النسخة", e)
            }
        }
    }

    fun requestRestoreFromFile(uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ قراءة الملف…") }
            val staged: File = try {
                backupService.stageExternal(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر فتح الملف", e)
                return@launch
            }
            try {
                val preview = backupService.previewOf(staged)
                local.update {
                    it.copy(
                        busy = null,
                        dialog = BackupDialog.ConfirmRestore(staged, staged = true, preview, mismatch(preview)),
                    )
                }
            } catch (e: CancellationException) {
                backupService.discardStaged(staged)
                throw e
            } catch (e: Exception) {
                backupService.discardStaged(staged)
                fail("تعذّر فتح الملف", e)
            }
        }
    }

    fun confirmRestore() {
        val dialog = local.value.dialog as? BackupDialog.ConfirmRestore ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ الاسترجاع… لا تُغلق التطبيق", dialog = null) }
            try {
                // A half-finished restore would be worse than either end state — don't let leaving the screen cancel it.
                val result = withContext(NonCancellable) { backupService.restore(dialog.file) }
                if (dialog.staged) backupService.discardStaged(dialog.file)
                val lines = ArrayList<String>()
                lines.add("استُرجع: ${result.restored.summaryText()}")
                if (result.filesRestored > 0) lines.add("ملفات (توقيعات/صور): ${result.filesRestored}")
                if (result.settingsRestored) lines.add("وأُعيدت بيانات الشركة وإعدادات الطباعة.")
                if (result.unreadableRows > 0) lines.add("تعذّرت قراءة ${result.unreadableRows} سجل داخل النسخة فتُركت.")
                lines.add("أُخذت نسخة أمان من وضعك السابق — تجدها في القائمة باسم «قبل الاسترجاع».")
                val list = backupService.listBackups()
                local.update {
                    it.copy(busy = null, backups = list, dialog = BackupDialog.Message("تم الاسترجاع", lines.joinToString("\n")))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (dialog.staged) backupService.discardStaged(dialog.file)
                fail("تعذّر الاسترجاع", e)
            }
        }
    }

    private fun mismatch(preview: BackupPreview): Boolean {
        val current = state.value.user?.id
        return preview.ownerUid != null && current != null && preview.ownerUid != current
    }

    // ------------------------------------------------------------------ CSV

    fun openCsvExportPicker() = local.update { it.copy(dialog = BackupDialog.PickCsvExport) }

    fun openCsvImportPicker() = local.update { it.copy(dialog = BackupDialog.PickCsvImport) }

    fun exportCsv(dataset: CsvDataset) {
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ تجهيز ملف ${dataset.label}…", dialog = null) }
            try {
                val file = csvService.export(dataset)
                local.update { it.copy(busy = null) }
                shareChannel.send(
                    ShareRequest(
                        uri = appContext.fileProviderUri(file),
                        mimeType = "text/csv",
                        chooserTitle = "تصدير ${dataset.label} (CSV)",
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر تصدير الملف", e)
            }
        }
    }

    fun previewCsvImport(dataset: CsvDataset, uri: Uri) {
        val user = state.value.user ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ قراءة الملف…", dialog = null) }
            try {
                val preview = when (dataset) {
                    CsvDataset.CUSTOMERS -> csvService.previewCustomers(uri, user)
                    CsvDataset.PRODUCTS -> csvService.previewProducts(uri, user)
                    else -> throw CsvException("هذا النوع متاح للتصدير فقط")
                }
                local.update { it.copy(busy = null, dialog = BackupDialog.CsvImportReady(preview)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر قراءة الملف", e)
            }
        }
    }

    fun confirmCsvImport() {
        val dialog = local.value.dialog as? BackupDialog.CsvImportReady ?: return
        val user = state.value.user ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = "جارٍ الحفظ…", dialog = null) }
            try {
                val result = withContext(NonCancellable) { csvService.apply(dialog.preview, user) }
                val text = buildString {
                    append("أُضيف ${result.created}")
                    if (result.updated > 0) append(" وحُدِّث ${result.updated}")
                    append(" من ${dialog.preview.dataset.label}.")
                }
                local.update { it.copy(busy = null, dialog = BackupDialog.Message("تم الاستيراد", text)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("تعذّر الاستيراد", e)
            }
        }
    }

    // ------------------------------------------------------------------ dialogs

    fun dismissDialog() {
        val dialog = local.value.dialog
        if (dialog is BackupDialog.ConfirmRestore && dialog.staged) backupService.discardStaged(dialog.file)
        local.update { it.copy(dialog = null) }
    }

    private fun fail(title: String, e: Exception) {
        val text = when (e) {
            is BackupException, is CsvException -> e.message ?: "خطأ غير متوقع"
            else -> e.message ?: e::class.java.simpleName ?: "خطأ غير متوقع"
        }
        local.update { it.copy(busy = null, dialog = BackupDialog.Message(title, text)) }
    }
}
