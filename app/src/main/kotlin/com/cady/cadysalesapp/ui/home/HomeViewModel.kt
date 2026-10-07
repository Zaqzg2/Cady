package com.cady.cadysalesapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.docId
import com.cady.cadysalesapp.ui.common.observeVisibleDocuments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Today's numbers for the hero panel (sales are after discount; returns are shown apart, not netted). */
data class TodaySummary(
    val sales: Double = 0.0,
    val salesCount: Int = 0,
    val returns: Double = 0.0,
    val collections: Double = 0.0,
    val collectionsCount: Int = 0,
)

data class HomeUiState(
    val currentUser: UserAccountEntity? = null,
    val recentActivity: List<RecentActivityItem> = emptyList(),
    /** docId → the amount on its row: an invoice's total after discount, a receipt's amount. */
    val amounts: Map<String, Double> = emptyMap(),
    val today: TodaySummary = TodaySummary(),
    val isManager: Boolean = false,
    /** Privacy setting "إخفاء المبالغ بآخر العمليات": money on this screen is drawn as dots. */
    val hideAmounts: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val receiptRepository: ReceiptRepository,
    companySettingsRepository: CompanySettingsRepository,
) : ViewModel() {

    /**
     * The feed comes from [observeVisibleDocuments] — the same source as the documents log. Before, this
     * screen always read only the signed-in account's own documents, so on a manager account every
     * invoice a rep sent in appeared in the log but never under "آخر العمليات".
     */
    val uiState: StateFlow<HomeUiState> = combine(
        accountRepository.currentUser,
        observeVisibleDocuments(accountRepository, invoiceRepository, receiptRepository),
        invoiceRepository.observeSubTotals(),
        companySettingsRepository.settings.map { it.hideAmountsInRecents }.distinctUntilChanged(),
    ) { user, items, subTotals, hideAmounts -> buildState(user, items, subTotals, hideAmounts) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

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

    private fun buildState(
        user: UserAccountEntity?,
        items: List<RecentActivityItem>,
        subTotals: Map<String, Double>,
        hideAmounts: Boolean,
    ): HomeUiState {
        if (user == null) return HomeUiState(loaded = true)

        fun amountOf(item: RecentActivityItem): Double = when (item) {
            is RecentActivityItem.Invoice -> computeInvoiceTotals(item.invoice, subTotals[item.invoice.id] ?: 0.0).grandTotal
            is RecentActivityItem.Receipt -> item.receipt.amount
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var sales = 0.0
        var salesCount = 0
        var returns = 0.0
        var collections = 0.0
        var collectionsCount = 0
        for (item in items) {
            if (item.date.atZone(zone).toLocalDate() != today) continue
            when (item) {
                is RecentActivityItem.Invoice -> {
                    val total = amountOf(item)
                    if (item.invoice.kind == InvoiceKind.SALE_RETURN) {
                        returns += total
                    } else {
                        sales += total
                        salesCount++
                    }
                }
                is RecentActivityItem.Receipt -> {
                    collections += item.receipt.amount
                    collectionsCount++
                }
            }
        }

        val recent = items.take(20)
        return HomeUiState(
            currentUser = user,
            recentActivity = recent,
            amounts = recent.associate { it.docId to amountOf(it) },
            today = TodaySummary(sales, salesCount, returns, collections, collectionsCount),
            isManager = user.role == UserRole.MANAGER,
            hideAmounts = hideAmounts,
            loaded = true,
        )
    }
}
