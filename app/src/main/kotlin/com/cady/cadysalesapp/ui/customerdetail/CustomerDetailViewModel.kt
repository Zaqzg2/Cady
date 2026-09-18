package com.cady.cadysalesapp.ui.customerdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
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
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    invoiceRepository: InvoiceRepository,
    receiptRepository: ReceiptRepository,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    val uiState: StateFlow<CustomerDetailUiState> = combine(
        customerRepository.observeById(customerId),
        invoiceRepository.observeForCustomer(customerId),
        receiptRepository.observeForCustomer(customerId),
    ) { customer, invoices, receipts ->
        val timeline = (invoices.map { RecentActivityItem.Invoice(it) } + receipts.map { RecentActivityItem.Receipt(it) })
            .sortedByDescending { it.date }
        CustomerDetailUiState(customer = customer, timeline = timeline)
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
}
