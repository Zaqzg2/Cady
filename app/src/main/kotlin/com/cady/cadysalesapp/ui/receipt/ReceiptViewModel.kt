package com.cady.cadysalesapp.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import com.cady.cadysalesapp.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptFormState(
    val selectedCustomer: CustomerEntity? = null,
    val amount: String = "",
    val method: ReceiptMethod = ReceiptMethod.CASH,
    val notes: String = "",
    val existingDocNumber: String? = null,
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val receiptRepository: ReceiptRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val existingReceiptId: String? = savedStateHandle.get<String>("receiptId")?.takeIf { it.isNotBlank() }
    private val preselectedCustomerId: String? = savedStateHandle.get<String>("customerId")?.takeIf { it.isNotBlank() }

    val availableCustomers: StateFlow<List<CustomerEntity>> = accountRepository.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else customerRepository.observeAll(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow(ReceiptFormState())
    val form: StateFlow<ReceiptFormState> = _form

    private val _saveState = MutableStateFlow<UiState>(UiState.Idle)
    val saveState: StateFlow<UiState> = _saveState

    init {
        viewModelScope.launch {
            when {
                existingReceiptId != null -> {
                    val receipt = receiptRepository.getById(existingReceiptId) ?: return@launch
                    val customer = customerRepository.getById(receipt.customerId)
                    _form.value = ReceiptFormState(
                        selectedCustomer = customer,
                        amount = receipt.amount.toString(),
                        method = receipt.method,
                        notes = receipt.notes.orEmpty(),
                        existingDocNumber = receipt.docNumber,
                    )
                }
                preselectedCustomerId != null -> {
                    val customer = customerRepository.getById(preselectedCustomerId)
                    _form.value = _form.value.copy(selectedCustomer = customer)
                }
            }
        }
    }

    fun selectCustomer(customer: CustomerEntity) {
        _form.value = _form.value.copy(selectedCustomer = customer)
    }

    fun setAmount(value: String) {
        _form.value = _form.value.copy(amount = value)
    }

    fun setMethod(method: ReceiptMethod) {
        _form.value = _form.value.copy(method = method)
    }

    fun setNotes(value: String) {
        _form.value = _form.value.copy(notes = value)
    }

    fun save(onSuccess: () -> Unit) {
        val state = _form.value
        val customer = state.selectedCustomer
        val amount = state.amount.toDoubleOrNull()
        if (customer == null) {
            _saveState.value = UiState.Error("اختر عميلًا أولًا")
            return
        }
        if (amount == null || amount <= 0) {
            _saveState.value = UiState.Error("أدخل مبلغًا صحيحًا")
            return
        }
        _saveState.value = UiState.Loading
        viewModelScope.launch {
            val currentUser = accountRepository.currentUser.first()
            if (currentUser == null) {
                _saveState.value = UiState.Error("الجلسة غير متاحة — سجّل الدخول من جديد")
                return@launch
            }
            try {
                receiptRepository.saveReceipt(
                    ownerUid = currentUser.id,
                    repName = currentUser.displayName,
                    existingId = existingReceiptId,
                    docNumber = state.existingDocNumber ?: receiptRepository.suggestNextDocNumber(currentUser.id),
                    customerId = customer.id,
                    customerName = customer.name,
                    amount = amount,
                    method = state.method,
                    repSignaturePath = null, // TODO(polish pass): wire the real signature pad
                    notes = state.notes.ifBlank { null },
                )
                _saveState.value = UiState.Success
                onSuccess()
            } catch (e: Exception) {
                _saveState.value = UiState.Error("تعذّر الحفظ: ${e.message}")
            }
        }
    }
}
