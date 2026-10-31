package com.cady.cadysalesapp.data.weborders

import com.cady.cadysalesapp.data.sync.anyToInstant
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The life of an order that came in from the public sales page: it arrives as NEW and the manager moves it on.
 * [key] is what is written to the document's `status` field.
 */
enum class WebOrderStatus(val key: String, val label: String) {
    NEW("new", "جديد"),
    PROCESSING("processing", "قيد التجهيز"),
    DONE("done", "مكتمل"),
    CANCELLED("cancelled", "ملغي");

    companion object {
        /**
         * Reads whatever the document holds. The page may not write a status at all — an order without one IS a
         * new order. An unfamiliar word means somebody already dealt with it, so it never raises an alert.
         */
        fun from(raw: Any?): WebOrderStatus = when ((raw as? String)?.trim()?.lowercase().orEmpty()) {
            "", "new", "pending", "open", "received", "جديد" -> NEW
            "processing", "preparing", "in_progress", "in-progress", "confirmed", "accepted", "قيد التجهيز", "مؤكد" -> PROCESSING
            "done", "completed", "complete", "delivered", "fulfilled", "closed", "مكتمل", "تم التسليم" -> DONE
            "cancelled", "canceled", "rejected", "void", "ملغي", "ملغى" -> CANCELLED
            else -> PROCESSING
        }
    }
}

/** One product line of an order, kept as text. The page writes `{scent: "ليمون", cartons: 2}`. */
data class WebOrderItem(
    val name: String,
    val quantity: String?,
    val unit: String?,
    /** What one carton was agreed at (the AI seller's quote), when the page wrote it. */
    val price: Long? = null,
    /** The quote this line came from ("Q-AB12CD"). */
    val quoteRef: String? = null,
    /** The app product the seller linked this item to (picked from the product list in the seller's settings), when it did. */
    val productId: String? = null,
) {
    /** "ليمون: 2 كرتونة" — the same wording the page itself shows the customer in its summary. */
    fun display(): String = buildString {
        append(name)
        if (!quantity.isNullOrBlank()) {
            append(": ")
            append(quantity)
            if (!unit.isNullOrBlank()) {
                append(" ")
                append(unit)
            }
        }
    }
}

data class WebOrder(
    val id: String,
    /** The short code the page gave the customer ("K-AB12CD") — what they quote when they write on WhatsApp. */
    val ref: String?,
    val customerName: String,
    val phone: String?,
    val address: String?,
    /** What [address] is called on screen: the page asks for a "المنطقة"; another writer might send a full "العنوان". */
    val addressLabel: String,
    val note: String?,
    val items: List<WebOrderItem>,
    val status: WebOrderStatus,
    val createdAt: Instant?,
    /** Anything else the page stored that has no named place on the screen: label → text. Nothing is hidden. */
    val extras: List<Pair<String, String>>,
    /** The invoice issued for this order from the app, once there is one (id and printed number). */
    val invoiceId: String? = null,
    val invoiceNo: String? = null,
) {
    /** "ليمون: 2 كرتونة • رمان: 1 كرتونة" — the whole order in one line. */
    fun itemsText(): String = items.joinToString(" • ") { it.display() }

    /** The quote the customer accepted on the page ("Q-AB12CD"), when the order came from the AI seller. */
    val quoteRef: String? get() = items.firstNotNullOfOrNull { it.quoteRef }

    /** What the customer was promised: cartons × agreed price, when every line carries both. */
    val agreedTotal: Long?
        get() {
            if (items.isEmpty()) return null
            var total = 0L
            for (item in items) {
                val cartons = item.quantity?.toLongOrNull() ?: return null
                val price = item.price ?: return null
                total += cartons * price
            }
            return total
        }
}

fun List<WebOrder>.newestFirst(): List<WebOrder> =
    sortedWith(compareByDescending<WebOrder> { it.createdAt ?: Instant.MIN }.thenBy { it.id })

// ------------------------------------------------------------------ reading a document

/**
 * The page's order assistant decides what it writes, and the Firestore SDK's typed getters THROW on a field of
 * another type — so, like FirestoreMappers, this reads forgivingly: a few spellings per field, text or numbers,
 * a nested `customer` map, items as a list / a map / free text. What it cannot place goes to [WebOrder.extras]
 * instead of being dropped, so a manager always sees everything the customer sent.
 */
internal fun DocumentSnapshot.toWebOrder(): WebOrder? {
    val fields = data ?: return null
    return parseWebOrder(id, fields)
}

internal fun parseWebOrder(id: String, fields: Map<String, Any?>): WebOrder {
    val used = HashSet<String>()

    fun found(keys: List<String>, from: Map<String, Any?> = fields, track: Boolean = true): Pair<String, String>? {
        for (key in keys) {
            val value = scalarText(from[key]) ?: continue
            if (track) used.add(key)
            return key to value
        }
        return null
    }

    fun text(keys: List<String>, from: Map<String, Any?> = fields, track: Boolean = true): String? =
        found(keys, from, track)?.second

    val customer = (fields["customer"] as? Map<*, *>)?.let { stringKeyed(it) }
    if (customer != null) used.add("customer")

    val name = text(NAME_KEYS) ?: customer?.let { text(NAME_KEYS, it, track = false) }
    val phone = text(PHONE_KEYS) ?: customer?.let { text(PHONE_KEYS, it, track = false) }
    val addressHit = found(ADDRESS_KEYS) ?: customer?.let { found(ADDRESS_KEYS, it, track = false) }
    val note = text(NOTE_KEYS)
    val ref = text(REF_KEYS)
    // Written by this app when an invoice is issued from the order: part of the order, not "something else the page sent".
    val invoiceId = text(listOf("invoiceId"))
    val invoiceNo = text(listOf("invoiceNo"))

    var items: List<WebOrderItem> = emptyList()
    for (key in ITEMS_KEYS) {
        val parsed = parseItems(fields[key])
        if (parsed.isNotEmpty()) {
            used.add(key)
            items = parsed
            break
        }
    }
    if (items.isEmpty()) {
        // A flat order: "product": "…", "quantity": 2.
        val product = text(PRODUCT_KEYS)
        if (product != null) items = listOf(WebOrderItem(product, text(QTY_KEYS), text(UNIT_KEYS)))
    }

    var createdAt: Instant? = null
    for (key in DATE_KEYS) {
        val instant = anyToInstant(fields[key]) ?: continue
        used.add(key)
        createdAt = instant
        break
    }

    used.add("status")
    val extras = fields.entries
        .filter { it.key !in used && it.key !in HIDDEN_KEYS && !isPlainWebSource(it.key, it.value) }
        .mapNotNull { (key, value) -> describe(value)?.let { labelFor(key) to friendlyValue(key, it) } }

    return WebOrder(
        id = id,
        ref = ref,
        customerName = name ?: DEFAULT_NAME,
        phone = phone,
        address = addressHit?.second,
        addressLabel = addressHit?.first?.let { ADDRESS_LABELS[it] } ?: "العنوان",
        note = note,
        items = items,
        status = WebOrderStatus.from(fields["status"]),
        createdAt = createdAt,
        extras = extras,
        invoiceId = invoiceId,
        invoiceNo = invoiceNo,
    )
}

private const val DEFAULT_NAME = "عميل من الموقع"

private val NAME_KEYS = listOf(
    "customerName", "customer_name", "name", "customer", "fullName", "full_name", "clientName", "client",
    "buyerName", "buyer", "الاسم",
)
private val PHONE_KEYS = listOf(
    "phone", "phoneNumber", "phone_number", "mobile", "whatsapp", "tel", "customerPhone", "customer_phone",
    "الهاتف", "الجوال", "رقم الهاتف",
)
private val ADDRESS_KEYS = listOf("address", "deliveryAddress", "delivery_address", "location", "city", "area", "العنوان", "المدينة")
private val ADDRESS_LABELS = mapOf("area" to "المنطقة", "city" to "المدينة", "المدينة" to "المدينة", "location" to "الموقع")
private val REF_KEYS = listOf("ref", "orderRef", "order_ref", "orderNumber", "order_number", "رقم الطلب")
private val NOTE_KEYS = listOf("note", "notes", "comment", "comments", "message", "details", "ملاحظات")
private val ITEMS_KEYS = listOf("items", "products", "cart", "lines", "orderItems", "order_items", "الأصناف")
private val PRODUCT_KEYS = listOf("product", "productName", "product_name", "flavor", "flavour", "item", "المنتج")
private val QTY_KEYS = listOf("quantity", "qty", "cartons", "count", "الكمية")
private val UNIT_KEYS = listOf("unit", "الوحدة")
private val ITEM_NAME_KEYS = listOf("scent", "name", "product", "productName", "product_name", "title", "item", "flavor", "flavour", "label")
private val ITEM_QTY_KEYS = listOf("quantity", "qty", "cartons", "count", "amount")
private val DATE_KEYS = listOf("createdAt", "created_at", "timestamp", "time", "date", "orderedAt", "ts")

/** Written by this app, not by the customer — not worth a line on the order. */
private val HIDDEN_KEYS = setOf("updatedAt")

/** The page stamps every order `source: "web"`; that is not news on an orders-from-the-website screen. */
private fun isPlainWebSource(key: String, value: Any?): Boolean =
    key == "source" && (value as? String)?.trim().equals("web", ignoreCase = true)

private val EXTRA_LABELS = mapOf(
    "quantity" to "الكمية",
    "qty" to "الكمية",
    "cartons" to "الكراتين",
    "product" to "المنتج",
    "flavor" to "النكهة",
    "flavour" to "النكهة",
    "city" to "المدينة",
    "area" to "المنطقة",
    "total" to "الإجمالي",
    "source" to "المصدر",
    "channel" to "القناة",
    "language" to "اللغة",
    "lang" to "اللغة",
    "payment" to "الدفع",
    "delivery" to "التوصيل",
)

private val extraDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

private fun labelFor(key: String): String = EXTRA_LABELS[key] ?: key

/** The seller's own channels read better in Arabic than as their raw codes. */
private val SOURCE_NAMES = mapOf("whatsapp" to "واتساب", "whatsapp-voice" to "واتساب (ملاحظة صوتية)")

private fun friendlyValue(key: String, text: String): String =
    if (key == "source") SOURCE_NAMES[text.trim().lowercase()] ?: text else text

private fun stringKeyed(map: Map<*, *>): Map<String, Any?> = map.entries.associate { it.key.toString() to it.value }

/** Text or a number as text; anything structured (a map, a list, a timestamp) is not "a scalar". */
private fun scalarText(value: Any?): String? = when (value) {
    is String -> value.trim().ifEmpty { null }
    is Number -> if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
    is Boolean -> if (value) "نعم" else "لا"
    else -> null
}

private fun parseItems(raw: Any?): List<WebOrderItem> = when (raw) {
    null -> emptyList()
    is String -> raw.trim().ifEmpty { null }?.let { listOf(WebOrderItem(it, null, null)) }.orEmpty()
    is Map<*, *> -> {
        val single = stringKeyed(raw)
        if (ITEM_NAME_KEYS.any { it in single }) {
            listOfNotNull(itemFromMap(single))
        } else {
            // {"لمون": 2, "رمان": 1} — product name → quantity.
            single.entries.mapNotNull { (product, qty) ->
                if (product.isBlank()) null else WebOrderItem(product.trim(), scalarText(qty), null)
            }
        }
    }
    is Iterable<*> -> raw.mapNotNull { parseItem(it) }
    else -> emptyList()
}

private fun parseItem(element: Any?): WebOrderItem? = when (element) {
    is String -> element.trim().ifEmpty { null }?.let { WebOrderItem(it, null, null) }
    is Map<*, *> -> itemFromMap(stringKeyed(element))
    else -> null
}

private fun itemFromMap(map: Map<String, Any?>): WebOrderItem? {
    val name = ITEM_NAME_KEYS.firstNotNullOfOrNull { scalarText(map[it]) } ?: return null
    val quantityKey = ITEM_QTY_KEYS.firstOrNull { scalarText(map[it]) != null }
    val quantity = quantityKey?.let { scalarText(map[it]) }
    // The page stores `cartons: 2` — a count of cartons, which is what the unit then says (as the page's own summary does).
    val unit = UNIT_KEYS.firstNotNullOfOrNull { scalarText(map[it]) }
        ?: if (quantityKey == "cartons" || quantityKey == "carton") "كرتونة" else null
    val price = scalarText(map["price"])?.toLongOrNull()
    val productId = scalarText(map["pid"]) ?: scalarText(map["productId"])
    return WebOrderItem(name, quantity, unit, price, scalarText(map["quote"]), productId)
}

/** A field the screen has no named place for, as one line of text (null when there is nothing to show). */
private fun describe(value: Any?): String? = when (value) {
    null -> null
    is String -> value.trim().ifEmpty { null }
    is Number, is Boolean -> scalarText(value)
    is Timestamp, is java.util.Date -> anyToInstant(value)?.let { extraDateFormat.format(it) }
    is Map<*, *> -> value.entries
        .mapNotNull { (k, v) -> describe(v)?.let { "$k: $it" } }
        .joinToString("، ")
        .ifEmpty { null }
    is Iterable<*> -> value.mapNotNull { describe(it) }.joinToString("، ").ifEmpty { null }
    else -> value.toString()
}
