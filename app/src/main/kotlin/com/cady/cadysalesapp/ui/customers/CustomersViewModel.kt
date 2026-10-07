package com.cady.cadysalesapp.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CustomerDeleteResult
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.domain.computeCustomerBalances
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val customerRepository: CustomerRepository,
    private val invoiceRepository: InvoiceRepository,
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val repFilter = MutableStateFlow<String?>(null)

    /** A manager sees every rep's customers (with a rep filter); a rep sees only his own. */
    val isManager: StateFlow<Boolean> = accountRepository.currentUser
        .map { it?.role == UserRole.MANAGER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reps: StateFlow<List<UserAccountEntity>> = accountRepository.observeReps()
        .map { list -> list.sortedBy { it.displayName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Account id → display name, to say whose customer a card is. */
    val ownerNames: StateFlow<Map<String, String>> =
        combine(accountRepository.currentUser, accountRepository.observeReps()) { user, repList ->
            (repList + listOfNotNull(user)).associate { it.id to it.displayName }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedRepId: StateFlow<String?> = repFilter

    val customers: StateFlow<List<CustomerEntity>> =
        combine(accountRepository.currentUser, searchQuery, repFilter) { user, query, rep -> Triple(user, query, rep) }
            .flatMapLatest { (user, query, rep) ->
                when {
                    user == null -> flowOf(emptyList())
                    user.role == UserRole.MANAGER -> {
                        val everyone = if (query.isBlank()) {
                            customerRepository.observeEveryone()
                        } else {
                            customerRepository.searchEveryone(query)
                        }
                        everyone.map { list -> if (rep == null) list else list.filter { it.ownerUid == rep } }
                    }
                    query.isBlank() -> customerRepository.observeAll(user.id)
                    else -> customerRepository.search(user.id, query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * customerId → balance (positive = the customer owes), for the balance shown on every row. Computed from
     * the documents this account can see, the same scoping as the reports dashboard — and in one pass over
     * the lists rather than one query per customer.
     */
    val balances: StateFlow<Map<String, Double>> = accountRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyMap())
            } else {
                val manager = user.role == UserRole.MANAGER
                combine(
                    if (manager) customerRepository.observeEveryone() else customerRepository.observeAll(user.id),
                    if (manager) invoiceRepository.observeEveryone() else invoiceRepository.observeAll(user.id),
                    if (manager) receiptRepository.observeEveryone() else receiptRepository.observeAll(user.id),
                    invoiceRepository.observeSubTotals(),
                ) { all, invoices, receipts, subTotals -> computeCustomerBalances(all, invoices, receipts, subTotals) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun selectRep(repId: String?) {
        repFilter.value = repId
    }

    fun togglePin(customerId: String) {
        viewModelScope.launch { customerRepository.togglePin(customerId) }
    }

    /** Edits a customer's details. The owner never changes (even when a manager edits a rep's customer). */
    fun updateCustomer(existing: CustomerEntity, name: String, phone: String?, address: String?, openingBalance: Double) {
        viewModelScope.launch {
            customerRepository.updateCustomer(
                existing.copy(name = name, phone = phone, address = address, openingBalance = openingBalance)
            )
        }
    }

    fun setActive(customerId: String, active: Boolean) {
        viewModelScope.launch { customerRepository.setActive(customerId, active) }
    }

    /**
     * Deletes the customer when nothing refers to it. A customer with invoices/receipts is refused
     * ([onBlocked] gets how many documents), so no history is ever left pointing at nobody.
     */
    fun deleteCustomer(customerId: String, onBlocked: (documents: Int) -> Unit) {
        viewModelScope.launch {
            val result = customerRepository.deleteCustomer(customerId)
            if (result is CustomerDeleteResult.HasDocuments) onBlocked(result.count)
        }
    }

    fun createCustomer(name: String, phone: String?, address: String?, openingBalance: Double) {
        viewModelScope.launch {
            val user = accountRepository.currentUser.first() ?: return@launch
            customerRepository.createCustomer(
                ownerUid = user.id, name = name, phone = phone, address = address,
                openingBalance = openingBalance, creditLimit = null, notes = null,
            )
        }
    }
}
