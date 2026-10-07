package com.cady.cadysalesapp.ui.invoice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceLineInput
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.OfferRepository
import com.cady.cadysalesapp.data.repository.ProductRepository
import com.cady.cadysalesapp.domain.InvoiceTotals
import com.cady.cadysalesapp.domain.OfferRules
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.parseNumber
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
    /** Set when an offer priced this line: the product's normal price and the percentage taken off. */
    val listPrice: Double? = null,
    val offerPercent: Double? = null,
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
                discountPercent = parseNumber(discountPercent) ?: 0.0,
                discountAmount = parseNumber(discountAmount) ?: 0.0,
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
    offerRepository: OfferRepository,
) : ViewModel() {

    private val existingInvoiceId: String? = savedStateHandle.get<String>("invoiceId")?.takeIf { it.isNotBlank() }
    private val preselectedCustomerId: String? = savedStateHandle.get<String>("customerId")?.takeIf { it.isNotBlank() }

    /** Home's "عميل نقدي": a new sale that starts with the walk-in customer already chosen. */
    private val startAsCashSale: Boolean = savedStateHandle.get<String>("cash") == "1"

    /** Which kind a *new* invoice starts as — "return" from Home's مرتجع or a customer's menu. */
    private val initialKind: InvoiceKind =
        if (savedStateHandle.get<String>("kind") == "return") InvoiceKind.SALE_RETURN else InvoiceKind.SALE

    /** Tracks whether `docNumber` still equals the last value *we* suggested —
        used by setKind() to know whether it's still safe to re-suggest, or
        whether the rep has already typed their own number over it. */
    private var lastSuggestedDocNumber: String? = null

    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every promotion; which one applies to a product is decided by OfferRules at the invoice date. */
    val offers: StateFlow<List<OfferEntity>> = offerRepository.observeAll()
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
                    _form.value = _form.value.copy(kind = initialKind)
                    val customer = when {
                        preselectedCustomerId != null -> customerRepository.getById(preselectedCustomerId)
                        startAsCashSale && currentUser != null -> customerRepository.findOrCreateCashCustomer(currentUser.id)
                        else -> null
                    }
                    if (startAsCashSale) _form.value = _form.value.copy(paymentMode = PaymentMode.CASH)
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
            discountPercent = formatQuantity(invoice.discountPercent),
            discountAmount = formatQuantity(invoice.discountAmount),
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

    /** Clears an old error as soon as the person changes anything, so a stale message never lingers. */
    fun dismissError() {
        if (_saveState.value is UiState.Error) _saveState.value = UiState.Idle
    }

    fun selectCustomer(customer: CustomerEntity) {
        _form.value = _form.value.copy(selectedCustomer = customer, customerBalanceBeforeThis = null)
        viewModelScope.launch { refreshBalanceBefore(customer.id, excludeDocId = existingInvoiceId) }
    }

    /** The offer that prices [product] on this invoice's date, if any. */
    fun offerFor(product: ProductEntity): OfferEntity? =
        OfferRules.bestFor(product.id, offers.value, _form.value.date)

    private fun newLine(product: ProductEntity, quantity: Double): EditableLine {
        val offer = offerFor(product)
        return if (offer == null) {
            EditableLine(productId = product.id, productName = product.name, price = product.price, quantity = quantity)
        } else {
            EditableLine(
                productId = product.id,
                // The offer is named on the line so the printed invoice says why the price is lower.
                productName = "${product.name} (عرض ${OfferRules.percentText(offer.percent)}%)",
                price = OfferRules.discountedPrice(product.price, offer.percent),
                quantity = quantity,
                listPrice = product.price,
                offerPercent = offer.percent,
            )
        }
    }

    fun addLine(product: ProductEntity) {
        val current = _form.value
        val existingLine = current.lines.find { it.productId == product.id }
        _form.value = if (existingLine != null) {
            current.copy(lines = current.lines.map {
                if (it.key == existingLine.key) it.copy(quantity = it.quantity + 1) else it
            })
        } else {
            current.copy(lines = current.lines + newLine(product, 1.0))
        }
    }

    /** The product picker's quantity control: 0 removes the line, anything else sets it (adding the line if new). */
    fun setProductQuantity(product: ProductEntity, quantity: Double) {
        val current = _form.value
        val existingLine = current.lines.find { it.productId == product.id }
        _form.value = when {
            quantity <= 0.0 -> current.copy(lines = current.lines.filterNot { it.productId == product.id })
            existingLine != null -> current.copy(lines = current.lines.map {
                if (it.key == existingLine.key) it.copy(quantity = quantity) else it
            })
            else -> current.copy(lines = current.lines + newLine(product, quantity))
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
        if (state.lines.any { it.quantity <= 0.0 }) {
            _saveState.value = UiState.Error("يوجد صنف بكمية صفر — عدّل الكمية أو احذف الصنف")
            return
        }
        val percent = parseNumber(state.discountPercent) ?: 0.0
        val amount = parseNumber(state.discountAmount) ?: 0.0
        if (percent < 0.0 || percent > 100.0) {
            _saveState.value = UiState.Error("نسبة الخصم يجب أن تكون بين 0 و 100")
            return
        }
        if (amount < 0.0) {
            _saveState.value = UiState.Error("مبلغ الخصم لا يمكن أن يكون سالبًا")
            return
        }
        if (state.totals.discountValue > state.totals.subTotal) {
            _saveState.value = UiState.Error("الخصم أكبر من إجمالي الفاتورة")
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
                // No fallback to the saved default signature here on purpose:
                // that default represents the REP (used for receipts, where the
                // rep is the one signing), and showing it under "customer
                // signature" on an unsigned invoice would misrepresent who
                // actually signed. An unsigned invoice prints with blank space
                // for a handwritten signature instead — see PdfService.
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
                    discountPercent = percent,
                    discountAmount = amount,
                    notes = state.notes.ifBlank { null },
                    signaturePath = state.signaturePath,
                )
                _saveState.value = UiState.Success
                onSuccess(saved.id)
            } catch (e: Exception) {
                _saveState.value = UiState.Error("تعذّر الحفظ: ${e.message}")
            }
        }
    }
}
