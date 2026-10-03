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
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DocumentFilter { ALL, SALE, RETURN, RECEIPT }

@HiltViewModel
class DocumentsListViewModel @Inject constructor(
    accountRepository: AccountRepository,
    invoiceRepository: InvoiceRepository,
    receiptRepository: ReceiptRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(DocumentFilter.ALL)
    val filter: StateFlow<DocumentFilter> = _filter

    private val repFilter = MutableStateFlow<String?>(null)
    val selectedRepId: StateFlow<String?> = repFilter

    /** A manager sees every rep's documents (with a rep filter); a rep sees only his own. */
    val isManager: StateFlow<Boolean> = accountRepository.currentUser
        .map { it?.role == UserRole.MANAGER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reps: StateFlow<List<UserAccountEntity>> = accountRepository.observeReps()
        .map { list -> list.sortedBy { it.displayName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun merge(invoices: List<InvoiceEntity>, receipts: List<ReceiptEntity>): List<RecentActivityItem> =
        (invoices.map { RecentActivityItem.Invoice(it) } + receipts.map { RecentActivityItem.Receipt(it) })
            .sortedByDescending { it.date }

    private val allItems: StateFlow<List<RecentActivityItem>> = accountRepository.currentUser
        .flatMapLatest { user ->
            when {
                user == null -> flowOf(emptyList())
                user.role == UserRole.MANAGER -> combine(
                    invoiceRepository.observeEveryone(),
                    receiptRepository.observeEveryone(),
                ) { invoices, receipts -> merge(invoices, receipts) }
                else -> combine(
                    invoiceRepository.observeAll(user.id),
                    receiptRepository.observeAll(user.id),
                ) { invoices, receipts -> merge(invoices, receipts) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<RecentActivityItem>> = combine(allItems, _filter, repFilter) { items, filter, rep ->
        val ofRep = if (rep == null) {
            items
        } else {
            items.filter {
                when (it) {
                    is RecentActivityItem.Invoice -> it.invoice.ownerUid == rep
                    is RecentActivityItem.Receipt -> it.receipt.ownerUid == rep
                }
            }
        }
        when (filter) {
            DocumentFilter.ALL -> ofRep
            DocumentFilter.SALE -> ofRep.filter {
                it is RecentActivityItem.Invoice && it.invoice.kind == InvoiceKind.SALE
            }
            DocumentFilter.RETURN -> ofRep.filter {
                it is RecentActivityItem.Invoice && it.invoice.kind == InvoiceKind.SALE_RETURN
            }
            DocumentFilter.RECEIPT -> ofRep.filter { it is RecentActivityItem.Receipt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: DocumentFilter) {
        _filter.value = filter
    }

    fun selectRep(repId: String?) {
        repFilter.value = repId
    }
}
