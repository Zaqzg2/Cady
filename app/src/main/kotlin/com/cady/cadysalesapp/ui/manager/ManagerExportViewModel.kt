package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.ManagerSyncService
import com.cady.cadysalesapp.data.sync.RecordCounts
import com.cady.cadysalesapp.data.sync.SyncFileService
import com.cady.cadysalesapp.ui.common.ShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportDone(
    val fileName: String,
    val counts: RecordCounts,
    val includesSettings: Boolean,
    val share: ShareRequest,
)

data class ManagerExportState(
    val reps: List<UserAccountEntity> = emptyList(),
    /** Null = every rep, in one file. */
    val targetRepId: String? = null,
    val includeCustomers: Boolean = true,
    val includeProducts: Boolean = true,
    val includeSettings: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val result: ExportDone? = null,
)

@HiltViewModel
class ManagerExportViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val managerSyncService: ManagerSyncService,
    private val syncFileService: SyncFileService,
) : ViewModel() {

    private data class Local(
        val targetRepId: String? = null,
        val customers: Boolean = true,
        val products: Boolean = true,
        val settings: Boolean = true,
        val busy: Boolean = false,
        val message: String? = null,
        val result: ExportDone? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ManagerExportState> = combine(accountRepository.observeReps(), local) { reps, l ->
        ManagerExportState(
            reps = reps.filter { it.isActive }.sortedWith(compareBy({ it.repNumber ?: Int.MAX_VALUE }, { it.displayName })),
            targetRepId = l.targetRepId,
            includeCustomers = l.customers,
            includeProducts = l.products,
            includeSettings = l.settings,
            busy = l.busy,
            message = l.message,
            result = l.result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerExportState())

    // Any change to the choices invalidates a file made with the old ones.
    fun selectTarget(repId: String?) = local.update { it.copy(targetRepId = repId, result = null, message = null) }
    fun setCustomers(on: Boolean) = local.update { it.copy(customers = on, result = null, message = null) }
    fun setProducts(on: Boolean) = local.update { it.copy(products = on, result = null, message = null) }
    fun setSettings(on: Boolean) = local.update { it.copy(settings = on, result = null, message = null) }

    fun create() {
        val choices = local.value
        if (choices.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true, message = null, result = null) }
            try {
                val manager = accountRepository.currentUser.first()
                if (manager == null) {
                    local.update { it.copy(busy = false, message = "لا يوجد حساب مسجّل الدخول") }
                    return@launch
                }
                val target = choices.targetRepId?.let { id -> accountRepository.observeReps().first().firstOrNull { it.id == id } }
                val made = managerSyncService.exportUpdate(
                    manager = manager,
                    target = target,
                    includeCustomers = choices.customers,
                    includeProducts = choices.products,
                    includeSettings = choices.settings,
                )
                if (made == null) {
                    local.update { it.copy(busy = false, message = "لا يوجد ما يُصدَّر بالخيارات المحددة — فعّل خيارًا فيه بيانات.") }
                } else {
                    val done = ExportDone(
                        fileName = made.file.name,
                        counts = made.entry.counts,
                        includesSettings = choices.settings,
                        share = ShareRequest(
                            uri = syncFileService.shareUri(made.file),
                            mimeType = "application/json",
                            chooserTitle = "إرسال التحديث إلى ${target?.displayName ?: "المندوبين"}",
                        ),
                    )
                    local.update { it.copy(busy = false, result = done) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, message = "تعذّر إنشاء الملف: ${e.message ?: "خطأ غير متوقع"}") }
            }
        }
    }
}
