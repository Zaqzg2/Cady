package com.cady.cadysalesapp.ui.manager

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.sync.LiveDoc
import com.cady.cadysalesapp.data.sync.LiveDocKind
import com.cady.cadysalesapp.data.sync.ManagerCloudService
import com.cady.cadysalesapp.data.sync.SyncRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LiveKindFilter(val label: String) {
    ALL("الكل"),
    SALE("بيع"),
    RETURN("مرتجع"),
    RECEIPT("سند قبض"),
}

data class LiveUiState(
    val docs: List<LiveDoc> = emptyList(),
    val reps: List<UserAccountEntity> = emptyList(),
    val selectedRepId: String? = null,
    val kind: LiveKindFilter = LiveKindFilter.ALL,
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ManagerLiveActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository,
    cloudService: ManagerCloudService,
    private val invoiceDao: InvoiceDao,
    private val receiptDao: ReceiptDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data class Data(val docs: List<LiveDoc>) : Feed
        data class Failed(val message: String) : Feed
    }

    private val repFilter = MutableStateFlow(savedStateHandle.get<String>("repId")?.takeIf { it.isNotBlank() })
    private val kindFilter = MutableStateFlow(LiveKindFilter.ALL)
    private val message = MutableStateFlow<String?>(null)

    private val feed: Flow<Feed> = cloudService.observeLive()
        .map<List<LiveDoc>, Feed> { Feed.Data(it) }
        .onStart { emit(Feed.Loading) }
        .catch { e ->
            val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            emit(
                Feed.Failed(
                    if (denied) "لا توجد صلاحية لقراءة النشاط المباشر بهذا الحساب"
                    else "تعذّر الاتصال بالنشاط المباشر — تحقّق من الإنترنت"
                )
            )
        }

    private data class Filters(val repId: String?, val kind: LiveKindFilter, val message: String?)

    private val filters = combine(repFilter, kindFilter, message) { r, k, m -> Filters(r, k, m) }

    val state: StateFlow<LiveUiState> = combine(feed, accountRepository.observeReps(), filters) { f, reps, flt ->
        val docs = (f as? Feed.Data)?.docs.orEmpty()
            .filter { flt.repId == null || it.repId == flt.repId }
            .filter {
                when (flt.kind) {
                    LiveKindFilter.ALL -> true
                    LiveKindFilter.SALE -> it.kind == LiveDocKind.SALE
                    LiveKindFilter.RETURN -> it.kind == LiveDocKind.RETURN
                    LiveKindFilter.RECEIPT -> it.kind == LiveDocKind.RECEIPT
                }
            }
        LiveUiState(
            docs = docs,
            reps = reps.sortedBy { it.displayName },
            selectedRepId = flt.repId,
            kind = flt.kind,
            loading = f is Feed.Loading,
            error = (f as? Feed.Failed)?.message,
            message = flt.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    fun selectRep(repId: String?) {
        repFilter.value = repId
    }

    fun selectKind(kind: LiveKindFilter) {
        kindFilter.value = kind
    }

    fun dismissMessage() {
        message.value = null
    }

    /**
     * Opens the document. The live feed shows what is in the cloud, which may not have been pulled
     * to this device yet — in that case this one document is fetched on the spot (it no longer
     * needs a full sync first), then opened with the usual preview/print screen.
     */
    fun open(doc: LiveDoc, onFound: (docType: String, docId: String) -> Unit) {
        viewModelScope.launch {
            val isInvoice = doc.kind != LiveDocKind.RECEIPT
            val docType = if (isInvoice) "invoice" else "receipt"
            val present = if (isInvoice) invoiceDao.getById(doc.id) != null else receiptDao.getById(doc.id) != null
            if (present) {
                onFound(docType, doc.id)
                return@launch
            }
            message.value = "جارٍ جلب المستند من السحابة…"
            val fetched = try {
                if (isInvoice) syncRepository.pullInvoiceById(doc.id) else syncRepository.pullReceiptById(doc.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (fetched) {
                message.value = null
                onFound(docType, doc.id)
            } else {
                message.value = "تعذّر جلب هذا المستند — تحقّق من الإنترنت ثم أعد المحاولة، أو زامن من «مزامنة هذا الجهاز»."
            }
        }
    }
}
