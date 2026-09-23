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
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
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
    /** Editable document number — pre-filled with a suggestion (new invoice)
        or the original number (editing). setKind() re-suggests it on a fresh
        invoice only while it still matches the last auto-suggestion, so a
        rep's own manual correction is never silently overwritten. */
    val docNumber: String = "",
    val date: Instant = Instant.now(),
    /** Only set once the rep actually draws something this time; null means
        "use the saved default signature, if any" at save time. */
    val signaturePath: String? = null,
    /** Customer's balance *before* this document's own effect — fetched once
        when the customer is selected/loaded, then combined with the live
        `totals` below so the balance-after preview updates on every keystroke
        without a fresh DB query each time. Null while unknown/no customer. */
    val customerBalanceBeforeThis: Double? = null,
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

    /** Same balanceBeforeThis + effect math as InvoiceRepository.saveInvoice,
        so the number shown here always matches what will actually be saved. */
    val balanceAfterPreview: Double?
        get() {
            val before = customerBalanceBeforeThis ?: return null
            val effect = when (kind) {
                InvoiceKind.SALE -> totals.grandTotal
                InvoiceKind.SALE_RETURN -> -totals.grandTotal
            }
            return before + effect
        }
}

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val invoiceRepository: InvoiceRepository,
    private val customerRepository: CustomerRepository,
    private val companySettingsRepository: CompanySettingsRepository,
    productRepository: ProductRepository,
) : ViewModel() {

    private val existingInvoiceId: String? = savedStateHandle.get<String>("invoiceId")?.takeIf { it.isNotBlank() }
    private val preselectedCustomerId: String? = savedStateHandle.get<String>("customerId")?.takeIf { it.isNotBlank() }

    /** Tracks whether `docNumber` still equals the last value *we* suggested —
        used by setKind() to know whether it's still safe to re-suggest, or
        whether the rep has already typed their own number over it. */
    private var lastSuggestedDocNumber: String? = null

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Only populated for the "quick sale from Home" entry point, where no
        customer was pre-selected — CustomerDetail's entry point already knows
        its customer and never needs this list. */
    val availableCustomers: StateFlow<List<CustomerEntity>> = accountRepository.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else customerRepository.observeAll(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** So the screen can show "leave blank to use the saved default" under the
        signature pad only when a default actually exists. */
    val companySettings: StateFlow<CompanySettings> = companySettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    private val _form = MutableStateFlow(InvoiceFormState())
    val form: StateFlow<InvoiceFormState> = _form

    private val _saveState = MutableStateFlow<UiState>(UiState.Idle)
    val saveState: StateFlow<UiState> = _saveState

    init {
        viewModelScope.launch {
            val currentUser = accountRepository.currentUser.first()
            when {
                existingInvoiceId != null -> loadExistingInvoice(existingInvoiceId)
                else -> {
                    val customer = preselectedCustomerId?.let { customerRepository.getById(it) }
                    val suggested = currentUser?.let {
                        invoiceRepository.suggestNextDocNumber(it.id, _form.value.kind)
                    }.orEmpty()
                    lastSuggestedDocNumber = suggested
                    _form.value = _form.value.copy(docNumber = suggested, selectedCustomer = customer)
                    if (customer != null) refreshBalanceBefore(customer.id, excludeDocId = null)
                }
            }
        }
    }

    private suspend fun refreshBalanceBefore(customerId: String, excludeDocId: String?) {
        val before = customerRepository.computeBalance(customerId, excludeDocId = excludeDocId)
        _form.value = _form.value.copy(customerBalanceBeforeThis = before)
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
            docNumber = invoice.docNumber,
            date = invoice.date,
            signaturePath = invoice.signaturePath,
        )
        if (customer != null) refreshBalanceBefore(customer.id, excludeDocId = id)
    }

    fun setKind(kind: InvoiceKind) {
        val current = _form.value
        _form.value = current.copy(kind = kind)
        if (existingInvoiceId == null && current.docNumber == lastSuggestedDocNumber) {
            viewModelScope.launch {
                val currentUser = accountRepository.currentUser.first() ?: return@launch
                val suggested = invoiceRepository.suggestNextDocNumber(currentUser.id, kind)
                lastSuggestedDocNumber = suggested
                _form.value = _form.value.copy(docNumber = suggested)
            }
        }
    }

    fun setPaymentMode(mode: PaymentMode) {
        _form.value = _form.value.copy(paymentMode = mode)
    }

    fun selectCustomer(customer: CustomerEntity) {
        _form.value = _form.value.copy(selectedCustomer = customer, customerBalanceBeforeThis = null)
        viewModelScope.launch { refreshBalanceBefore(customer.id, excludeDocId = existingInvoiceId) }
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

    fun setDocNumber(value: String) {
        _form.value = _form.value.copy(docNumber = value)
    }

    fun setDate(value: Instant) {
        _form.value = _form.value.copy(date = value)
    }

    fun setSignaturePath(path: String?) {
        _form.value = _form.value.copy(signaturePath = path)
    }

    fun save(onSuccess: (invoiceId: String) -> Unit) {
        val state = _form.value
        if (state.selectedCustomer == null) {
            _saveState.value = UiState.Error("اختر عميلًا أولًا")
            return
        }
        if (state.lines.isEmpty()) {
            _saveState.value = UiState.Error("أضف منتجًا واحدًا على الأقل")
            return
        }
        if (state.docNumber.isBlank()) {
            _saveState.value = UiState.Error("أدخل رقم الفاتورة")
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
                val defaultSignature = companySettingsRepository.settings.first().repSignaturePath
                val saved = invoiceRepository.saveInvoice(
                    ownerUid = currentUser.id,
                    repName = currentUser.displayName,
                    existingId = existingInvoiceId,
                    docNumber = state.docNumber,
                    date = state.date,
                    kind = state.kind,
                    customerId = customer.id,
                    customerName = customer.name,
                    paymentMode = state.paymentMode,
                    lines = state.lines.map { InvoiceLineInput(it.productId, it.productName, it.price, it.quantity) },
                    discountPercent = state.discountPercent.toDoubleOrNull() ?: 0.0,
                    discountAmount = state.discountAmount.toDoubleOrNull() ?: 0.0,
                    notes = state.notes.ifBlank { null },
                    signaturePath = state.signaturePath ?: defaultSignature,
                )
                _saveState.value = UiState.Success
                onSuccess(saved.id)
            } catch (e: Exception) {
                _saveState.value = UiState.Error("تعذّر الحفظ: ${e.message}")
            }
        }
    }
}
