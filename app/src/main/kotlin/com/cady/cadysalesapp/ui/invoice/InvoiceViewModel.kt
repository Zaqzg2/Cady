package com.cady.cadysalesapp.ui.invoice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceLineInput
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ProductRepository
import com.cady.cadysalesapp.domain.InvoiceTotals
import com.cady.cadysalesapp.domain.computeInvoiceTotals
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** One editable line in the invoice being built — a UI-side wrapper around
    InvoiceLineInput, keyed so rows can be updated/removed individually. */
data class EditableLine(
    val key: String = UUID.randomUUID().toString(),
    val productId: String,
    val productName: String,
    val price: Double,
    val quantity: Double,
)

data class InvoiceFormState(
    val kind: InvoiceKind = InvoiceKind.SALE,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val selectedCustomer: CustomerEntity? = null,
    val lines: List<EditableLine> = emptyList(),
    val discountPercent: String = "0",
    val discountAmount: String = "0",
    val notes: String = "",
    /** Preserved from the original document when editing — never regenerated,
        or every edit would silently reassign a new document number. */
    val existingDocNumber: String? = null,
) {
    /** Live totals recomputed from the current draft, reusing the exact same
        pure function InvoiceRepository uses at save time — the preview on
        screen and the number actually persisted can never drift apart. */
    val totals: InvoiceTotals
        get() {
            val items = lines.map { InvoiceItemEntity(it.key, "", it.productId, it.productName, it.price, it.quantity) }
            val draftInvoice = InvoiceEntity(
                id = "", docNumber = "", date = Instant.now(), kind = kind, customerId = "",
                customerName = "", paymentMode = paymentMode,
                discountPercent = discountPercent.toDoubleOrNull() ?: 0.0,
                discountAmount = discountAmount.toDoubleOrNull() ?: 0.0,
                notes = null, signaturePath = null, repName = null, balanceAfter = 0.0,
                isPrinted = false, isShared = false, isPinned = false,
                syncStatus = SyncStatus.PENDING, ownerUid = "",
            )
            return computeInvoiceTotals(draftInvoice, items)
        }
}

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val customerRepository: CustomerRepository,
    productRepository: ProductRepository,
) : ViewModel() {

    private val existingInvoiceId: String? = savedStateHandle.get<String>("invoiceId")?.takeIf { it.isNotBlank() }
    private val preselectedCustomerId: String? = savedStateHandle.get<String>("customerId")?.takeIf { it.isNotBlank() }

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Only populated for the "quick sale from Home" entry point, where no
        customer was pre-selected — CustomerDetail's entry point already knows
        its customer and never needs this list. */
    val availableCustomers: StateFlow<List<CustomerEntity>> = accountRepository.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else customerRepository.observeAll(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow(InvoiceFormState())
    val form: StateFlow<InvoiceFormState> = _form

    private val _saveState = MutableStateFlow<UiState>(UiState.Idle)
    val saveState: StateFlow<UiState> = _saveState

    init {
        viewModelScope.launch {
            when {
                existingInvoiceId != null -> loadExistingInvoice(existingInvoiceId)
                preselectedCustomerId != null -> {
                    val customer = customerRepository.getById(preselectedCustomerId)
                    _form.value = _form.value.copy(selectedCustomer = customer)
                }
            }
        }
    }

    private suspend fun loadExistingInvoice(id: String) {
        val invoice = invoiceRepository.getById(id) ?: return
        val items = invoiceRepository.getItems(id)
        val customer = customerRepository.getById(invoice.customerId)
        _form.value = InvoiceFormState(
            kind = invoice.kind,
            paymentMode = invoice.paymentMode,
            selectedCustomer = customer,
            lines = items.map {
                EditableLine(productId = it.productId, productName = it.productName, price = it.price, quantity = it.quantity)
            },
            discountPercent = invoice.discountPercent.toString(),
            discountAmount = invoice.discountAmount.toString(),
            notes = invoice.notes.orEmpty(),
            existingDocNumber = invoice.docNumber,
        )
    }

    fun setKind(kind: InvoiceKind) {
        _form.value = _form.value.copy(kind = kind)
    }

    fun setPaymentMode(mode: PaymentMode) {
        _form.value = _form.value.copy(paymentMode = mode)
    }

    fun selectCustomer(customer: CustomerEntity) {
        _form.value = _form.value.copy(selectedCustomer = customer)
    }

    fun addLine(product: ProductEntity) {
        val current = _form.value
        val existingLine = current.lines.find { it.productId == product.id }
        _form.value = if (existingLine != null) {
            current.copy(lines = current.lines.map {
                if (it.key == existingLine.key) it.copy(quantity = it.quantity + 1) else it
            })
        } else {
            current.copy(
                lines = current.lines + EditableLine(
                    productId = product.id, productName = product.name, price = product.price, quantity = 1.0
                )
            )
        }
    }

    fun updateLineQuantity(key: String, quantity: Double) {
        _form.value = _form.value.copy(lines = _form.value.lines.map { if (it.key == key) it.copy(quantity = quantity) else it })
    }

    fun removeLine(key: String) {
        _form.value = _form.value.copy(lines = _form.value.lines.filterNot { it.key == key })
    }

    fun setDiscountPercent(value: String) {
        _form.value = _form.value.copy(discountPercent = value)
    }

    fun setDiscountAmount(value: String) {
        _form.value = _form.value.copy(discountAmount = value)
    }

    fun setNotes(value: String) {
        _form.value = _form.value.copy(notes = value)
    }

    fun save(onSuccess: () -> Unit) {
        val state = _form.value
        if (state.selectedCustomer == null) {
            _saveState.value = UiState.Error("اختر عميلًا أولًا")
            return
        }
        if (state.lines.isEmpty()) {
            _saveState.value = UiState.Error("أضف منتجًا واحدًا على الأقل")
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
                val customer = state.selectedCustomer
                invoiceRepository.saveInvoice(
                    ownerUid = currentUser.id,
                    repName = currentUser.displayName,
                    existingId = existingInvoiceId,
                    docNumber = state.existingDocNumber ?: invoiceRepository.suggestNextDocNumber(currentUser.id, state.kind),
                    kind = state.kind,
                    customerId = customer.id,
                    customerName = customer.name,
                    paymentMode = state.paymentMode,
                    lines = state.lines.map { InvoiceLineInput(it.productId, it.productName, it.price, it.quantity) },
                    discountPercent = state.discountPercent.toDoubleOrNull() ?: 0.0,
                    discountAmount = state.discountAmount.toDoubleOrNull() ?: 0.0,
                    notes = state.notes.ifBlank { null },
                    signaturePath = null, // TODO(polish pass): wire the real signature pad
                )
                _saveState.value = UiState.Success
                onSuccess()
            } catch (e: Exception) {
                _saveState.value = UiState.Error("تعذّر الحفظ: ${e.message}")
            }
        }
    }
}
