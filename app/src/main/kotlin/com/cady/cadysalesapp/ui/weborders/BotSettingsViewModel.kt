package com.cady.cadysalesapp.ui.weborders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.repository.ProductRepository
import com.cady.cadysalesapp.data.weborders.BotConfig
import com.cady.cadysalesapp.data.weborders.BotConfigRepository
import com.cady.cadysalesapp.data.weborders.BotDefaults
import com.cady.cadysalesapp.data.weborders.BotItem
import com.cady.cadysalesapp.data.weborders.newBotItemId
import com.cady.cadysalesapp.ui.common.parseNumber
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One item card as it is being typed: numbers stay text, so a half-typed number is never rejected or rewritten. */
data class BotItemDraft(
    val id: String,
    val name: String,
    val priceText: String,
    val maxText: String,
    val stepText: String,
    val active: Boolean,
    val note: String,
    val productId: String,
)

private fun BotItem.toDraft() = BotItemDraft(
    id = id,
    name = name,
    priceText = price.toString(),
    maxText = maxDiscount.toString(),
    stepText = step.toString(),
    active = active,
    note = note,
    productId = productId,
)

/** The first thing wrong with this card, or null when it can be saved. */
internal fun BotItemDraft.problem(): String? {
    val clean = name.trim()
    if (clean.isEmpty()) return "اكتب اسم الصنف"
    if (clean.length > MAX_NAME) return "اسم الصنف أطول من $MAX_NAME حرفًا"
    val price = parseNumber(priceText)?.toLong()
    if (price == null || price <= 0) return "اكتب سعر الكرتونة"
    val max = parseNumber(maxText)?.toLong()
    if (max == null || max < 0) return "اكتب أقصى خصم للكرتونة (0 يوقف التفاوض)"
    if (max >= price) return "أقصى خصم يجب أن يكون أقل من سعر الكرتونة"
    val step = parseNumber(stepText)?.toLong()
    if (step == null || step <= 0) return "اكتب حجم خطوة التنازل"
    if (note.trim().length > MAX_NOTE) return "الوصف أطول من $MAX_NOTE حرفًا"
    return null
}

/** The item this card says, or null while [problem] is not null. */
internal fun BotItemDraft.toBotItemOrNull(): BotItem? {
    if (problem() != null) return null
    val price = parseNumber(priceText)?.toLong() ?: return null
    val max = parseNumber(maxText)?.toLong() ?: return null
    val step = parseNumber(stepText)?.toLong() ?: return null
    return BotItem(
        id = id,
        name = name.trim(),
        price = price,
        maxDiscount = max,
        step = step,
        active = active,
        note = note.trim(),
        productId = productId,
    )
}

/** The settings as typed, ready to save — or the first thing that is wrong with them. */
internal fun checkDraft(
    enabled: Boolean,
    hoursText: String,
    drafts: List<BotItemDraft>,
    voiceEnabled: Boolean = true,
): Pair<BotConfig?, String?> {
    val hours = parseNumber(hoursText)?.toLong()
    if (hours == null || hours <= 0) return null to "اكتب مدة صلاحية العرض بالساعات"
    if (drafts.isEmpty()) return null to "أضف صنفًا واحدًا على الأقل"
    for (draft in drafts) {
        val problem = draft.problem()
        if (problem != null) return null to "«${draft.name.trim().ifEmpty { "صنف بلا اسم" }}»: $problem"
    }
    val items = drafts.mapNotNull { it.toBotItemOrNull() }
    if (items.map { it.name }.toSet().size != items.size) return null to "اسم صنف مكرر — لكل صنف اسم مختلف"
    if (enabled && items.none { it.active }) return null to "فعّل صنفًا واحدًا على الأقل، أو أوقف البائع الآلي"
    return BotConfig(enabled = enabled, validHours = hours, items = items, voiceEnabled = voiceEnabled) to null
}

private const val MAX_NAME = 40
private const val MAX_NOTE = 200

data class BotSettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** True once the fields were filled from what is saved (or the defaults) — only once, so typing is never overwritten. */
    val seeded: Boolean = false,
    val enabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val hoursText: String = "",
    val items: List<BotItemDraft> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BotSettingsViewModel @Inject constructor(
    private val repository: BotConfigRepository,
    productRepository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BotSettingsUiState())
    val state: StateFlow<BotSettingsUiState> = _state.asStateFlow()

    /** The app's products, for "إضافة صنف": picking one links the item to it, so invoices match it exactly. */
    val products: StateFlow<List<ProductEntity>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.observe()
                .catch { e ->
                    _state.update { seed(it, null).copy(loading = false, error = readError(e)) }
                }
                .collect { snapshot ->
                    _state.update { seed(it, snapshot.config).copy(loading = false, error = null) }
                }
        }
    }

    private fun seed(state: BotSettingsUiState, saved: BotConfig?): BotSettingsUiState {
        if (state.seeded) return state
        val current = saved ?: BotConfig.initial()
        return state.copy(
            seeded = true,
            enabled = current.enabled,
            voiceEnabled = current.voiceEnabled,
            hoursText = current.validHours.toString(),
            items = current.items.map { it.toDraft() },
        )
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _state.update { it.copy(voiceEnabled = enabled) }
    }

    fun setHours(text: String) {
        _state.update { it.copy(hoursText = text) }
    }

    fun updateItem(id: String, change: (BotItemDraft) -> BotItemDraft) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) change(it) else it }) }
    }

    fun removeItem(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
    }

    /** A new card: the product's own price when it was picked from the list, the usual defaults otherwise. */
    fun addItem(name: String, product: ProductEntity?) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        val price = product?.price?.toLong()?.takeIf { it > 0 } ?: BotDefaults.PRICE
        val max = minOf(BotDefaults.MAX_DISCOUNT, price - 1).coerceAtLeast(0)
        val draft = BotItemDraft(
            id = newBotItemId(),
            name = clean,
            priceText = price.toString(),
            maxText = max.toString(),
            stepText = BotDefaults.STEP.toString(),
            active = true,
            note = "",
            productId = product?.id.orEmpty(),
        )
        _state.update { it.copy(items = it.items + draft) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val config = checkDraft(current.enabled, current.hoursText, current.items, current.voiceEnabled).first ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val note = try {
                if (repository.save(config)) {
                    "تم الحفظ — يسري على المحادثات الجديدة خلال دقيقة"
                } else {
                    "لا يوجد اتصال الآن — سيُحفظ تلقائيًا عند عودة الإنترنت"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isDenied(e)) {
                    "لا توجد صلاحية للحفظ — انشر قواعد Firestore الجديدة (bot_config)"
                } else {
                    "تعذّر الحفظ — تحقّق من الإنترنت وأعد المحاولة"
                }
            }
            _state.update { it.copy(saving = false, message = note) }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun readError(e: Throwable): String =
        if (isDenied(e)) {
            "لا توجد صلاحية لقراءة الإعدادات — انشر قواعد Firestore الجديدة (bot_config)"
        } else {
            "تعذّر تحميل الإعدادات — تحقّق من الإنترنت"
        }

    private fun isDenied(e: Throwable): Boolean =
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}
