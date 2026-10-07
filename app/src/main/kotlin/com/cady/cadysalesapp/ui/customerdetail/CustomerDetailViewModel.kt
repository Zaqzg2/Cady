package com.cady.cadysalesapp.ui.customerdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.mergeDocuments
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerDetailUiState(
    val customer: CustomerEntity? = null,
    val timeline: List<RecentActivityItem> = emptyList(),
    /** docId → the amount on its row: an invoice's total after discount, a receipt's amount. */
    val amounts: Map<String, Double> = emptyMap(),
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    val uiState: StateFlow<CustomerDetailUiState> = combine(
        customerRepository.observeById(customerId),
        invoiceRepository.observeForCustomer(customerId),
        receiptRepository.observeForCustomer(customerId),
        invoiceRepository.observeSubTotals(),
    ) { customer, invoices, receipts, subTotals ->
        val amounts = HashMap<String, Double>()
        invoices.forEach { amounts[it.id] = computeInvoiceTotals(it, subTotals[it.id] ?: 0.0).grandTotal }
        receipts.forEach { amounts[it.id] = it.amount }
        CustomerDetailUiState(
            customer = customer,
            timeline = mergeDocuments(invoices, receipts),
            amounts = amounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerDetailUiState())

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance

    init {
        // computeBalance joins invoice items and is a one-shot suspend calculation,
        // not something Room streams as a Flow — recomputed every time the
        // timeline itself changes, which is exactly when the balance can change.
        uiState.onEach { refreshBalance() }.launchIn(viewModelScope)
    }

    fun refreshBalance() {
        viewModelScope.launch {
            _balance.value = customerRepository.computeBalance(customerId)
        }
    }

    fun togglePin() {
        viewModelScope.launch { customerRepository.togglePin(customerId) }
    }

    /** Swipe / long-press actions on a row of this customer's own timeline. */
    fun togglePinDocument(docType: String, docId: String) {
        viewModelScope.launch {
            if (docType == "invoice") invoiceRepository.togglePin(docId) else receiptRepository.togglePin(docId)
        }
    }

    fun deleteDocument(docType: String, docId: String) {
        viewModelScope.launch {
            if (docType == "invoice") invoiceRepository.deleteInvoice(docId) else receiptRepository.deleteReceipt(docId)
        }
    }
}
