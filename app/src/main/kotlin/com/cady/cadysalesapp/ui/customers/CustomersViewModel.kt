package com.cady.cadysalesapp.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val customers: StateFlow<List<CustomerEntity>> = accountRepository.currentUser
        .combine(searchQuery) { user, query -> user to query }
        .flatMapLatest { (user, query) ->
            when {
                user == null -> flowOf(emptyList())
                query.isBlank() -> customerRepository.observeAll(user.id)
                else -> customerRepository.search(user.id, query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun togglePin(customerId: String) {
        viewModelScope.launch { customerRepository.togglePin(customerId) }
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
