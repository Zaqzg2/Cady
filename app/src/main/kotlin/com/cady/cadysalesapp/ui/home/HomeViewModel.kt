package com.cady.cadysalesapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/** One unified row type for the "recent activity" feed — the same idea as the
    current app's DocRow, kept minimal since the full documents list (with all
    its filters and per-row actions) is DocumentsListScreen's job, not Home's. */
sealed interface RecentActivityItem {
    val date: Instant
    data class Invoice(val invoice: InvoiceEntity) : RecentActivityItem {
        override val date get() = invoice.date
    }
    data class Receipt(val receipt: ReceiptEntity) : RecentActivityItem {
        override val date get() = receipt.date
    }
}

data class HomeUiState(
    val currentUser: UserAccountEntity? = null,
    val recentActivity: List<RecentActivityItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    invoiceRepository: InvoiceRepository,
    receiptRepository: ReceiptRepository,
) : ViewModel() {

    val uiState = accountRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(HomeUiState())
            } else {
                combine(
                    invoiceRepository.observeAll(user.id),
                    receiptRepository.observeAll(user.id),
                ) { invoices, receipts ->
                    val items = (invoices.map { RecentActivityItem.Invoice(it) } +
                        receipts.map { RecentActivityItem.Receipt(it) })
                        .sortedByDescending { it.date }
                        .take(20)
                    HomeUiState(currentUser = user, recentActivity = items)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
