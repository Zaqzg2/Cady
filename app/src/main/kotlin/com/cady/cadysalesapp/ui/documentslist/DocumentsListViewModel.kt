package com.cady.cadysalesapp.ui.documentslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
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

    private val allItems: StateFlow<List<RecentActivityItem>> = accountRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                combine(invoiceRepository.observeAll(user.id), receiptRepository.observeAll(user.id)) { invoices, receipts ->
                    (invoices.map { RecentActivityItem.Invoice(it) } + receipts.map { RecentActivityItem.Receipt(it) })
                        .sortedByDescending { it.date }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<RecentActivityItem>> = combine(allItems, _filter) { items, filter ->
        when (filter) {
            DocumentFilter.ALL -> items
            DocumentFilter.SALE -> items.filter {
                it is RecentActivityItem.Invoice && it.invoice.kind == InvoiceKind.SALE
            }
            DocumentFilter.RETURN -> items.filter {
                it is RecentActivityItem.Invoice && it.invoice.kind == InvoiceKind.SALE_RETURN
            }
            DocumentFilter.RECEIPT -> items.filter { it is RecentActivityItem.Receipt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: DocumentFilter) {
        _filter.value = filter
    }
}
