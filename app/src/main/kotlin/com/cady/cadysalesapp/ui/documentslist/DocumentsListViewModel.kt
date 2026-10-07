package com.cady.cadysalesapp.ui.documentslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.docId
import com.cady.cadysalesapp.ui.common.docType
import com.cady.cadysalesapp.ui.common.isPinned
import com.cady.cadysalesapp.ui.common.matchesSearch
import com.cady.cadysalesapp.ui.common.observeVisibleDocuments
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DocumentFilter { ALL, SALE, RETURN, RECEIPT }

@HiltViewModel
class DocumentsListViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(DocumentFilter.ALL)
    val filter: StateFlow<DocumentFilter> = _filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val repFilter = MutableStateFlow<String?>(null)
    val selectedRepId: StateFlow<String?> = repFilter

    /** A manager sees every rep's documents (with a rep filter); a rep sees only his own. */
    val isManager: StateFlow<Boolean> = accountRepository.currentUser
        .map { it?.role == UserRole.MANAGER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reps: StateFlow<List<UserAccountEntity>> = accountRepository.observeReps()
        .map { list -> list.sortedBy { it.displayName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The very same feed Home's "آخر العمليات" reads ([observeVisibleDocuments]): a manager gets every rep's
     * documents, a rep only his own. One source, so the two screens can never disagree again.
     */
    private val allItems: StateFlow<List<RecentActivityItem>> =
        observeVisibleDocuments(accountRepository, invoiceRepository, receiptRepository)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Criteria(val filter: DocumentFilter, val rep: String?, val query: String)

    val filteredItems: StateFlow<List<RecentActivityItem>> =
        combine(allItems, combine(_filter, repFilter, _query) { f, r, q -> Criteria(f, r, q) }) { items, c ->
            items.filter { item ->
                val kindOk = when (c.filter) {
                    DocumentFilter.ALL -> true
                    DocumentFilter.SALE -> item is RecentActivityItem.Invoice && item.invoice.kind == InvoiceKind.SALE
                    DocumentFilter.RETURN -> item is RecentActivityItem.Invoice && item.invoice.kind == InvoiceKind.SALE_RETURN
                    DocumentFilter.RECEIPT -> item is RecentActivityItem.Receipt
                }
                val owner = when (item) {
                    is RecentActivityItem.Invoice -> item.invoice.ownerUid
                    is RecentActivityItem.Receipt -> item.receipt.ownerUid
                }
                val searchable = when (item) {
                    is RecentActivityItem.Invoice -> "${item.invoice.docNumber} ${item.invoice.customerName} ${item.invoice.repName.orEmpty()}"
                    is RecentActivityItem.Receipt -> "${item.receipt.docNumber} ${item.receipt.customerName} ${item.receipt.repName.orEmpty()}"
                }
                kindOk && (c.rep == null || owner == c.rep) && matchesSearch(searchable, c.query)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** docId → the amount shown on its row: an invoice's total after discount, a receipt's amount. */
    val amounts: StateFlow<Map<String, Double>> =
        combine(allItems, invoiceRepository.observeSubTotals()) { items, subTotals ->
            items.associate { item ->
                when (item) {
                    is RecentActivityItem.Invoice ->
                        item.invoice.id to computeInvoiceTotals(item.invoice, subTotals[item.invoice.id] ?: 0.0).grandTotal
                    is RecentActivityItem.Receipt -> item.receipt.id to item.receipt.amount
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFilter(filter: DocumentFilter) {
        _filter.value = filter
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun selectRep(repId: String?) {
        repFilter.value = repId
    }

    /** Deletes the document everywhere it is kept: this device, the cloud copy, and (via a tombstone) other devices. */
    fun delete(docType: String, docId: String) {
        viewModelScope.launch {
            if (docType == "invoice") invoiceRepository.deleteInvoice(docId) else receiptRepository.deleteReceipt(docId)
        }
    }

    fun togglePin(docType: String, docId: String) {
        viewModelScope.launch {
            if (docType == "invoice") invoiceRepository.togglePin(docId) else receiptRepository.togglePin(docId)
        }
    }

    /** Several documents at once (multi-select): each is deleted exactly like a single delete. */
    fun deleteMany(refs: List<Pair<String, String>>) {
        viewModelScope.launch {
            refs.forEach { (docType, docId) ->
                if (docType == "invoice") invoiceRepository.deleteInvoice(docId) else receiptRepository.deleteReceipt(docId)
            }
        }
    }

    /** Pins (or unpins) every selected document; ones already in the wanted state are left alone. */
    fun setPinnedMany(refs: List<Pair<String, String>>, pinned: Boolean) {
        val current = allItems.value.associateBy { it.docType + ":" + it.docId }
        viewModelScope.launch {
            refs.forEach { (docType, docId) ->
                val item = current[docType + ":" + docId] ?: return@forEach
                if (item.isPinned != pinned) {
                    if (docType == "invoice") invoiceRepository.togglePin(docId) else receiptRepository.togglePin(docId)
                }
            }
        }
    }
}
