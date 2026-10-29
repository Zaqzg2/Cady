package com.cady.cadysalesapp.data.weborders

import com.google.firebase.firestore.DocumentSnapshot
import java.util.UUID

/** What a brand-new seller starts with, and what the older single-price settings turn into. */
object BotDefaults {
    const val PRICE = 7_000L
    const val MAX_DISCOUNT = 200L
    const val STEP = 100L
    const val VALID_HOURS = 24L

    /** The three scents of the first product the page sold (a dishwashing liquid). */
    val SCENTS = listOf("ليمون", "رمان", "تفاح أخضر")
    const val SCENT_NOTE = "سائل غسيل صحون؛ الجالون 5 لتر والكرتونة 4 جالونات مغلّفة بشرنك (20 لترًا)"
}

fun newBotItemId(): String = UUID.randomUUID().toString().take(8)

/**
 * One thing the page's AI seller may sell — a product or a scent — with its own price rules. The lowest price
 * ([floor]) is manager-only on purpose: it is applied in code by the seller and never sent to a language model.
 */
data class BotItem(
    /** Stable key made by the app; the seller never shows it. */
    val id: String = newBotItemId(),
    /** What the customer sees and the seller says: a scent ("ليمون") or a product ("غسيل ملابس"). */
    val name: String,
    /** The list price of one carton. */
    val price: Long = BotDefaults.PRICE,
    /** The most the seller can take off one carton in total. */
    val maxDiscount: Long = BotDefaults.MAX_DISCOUNT,
    /** How much one concession takes off. */
    val step: Long = BotDefaults.STEP,
    /** Off = the seller does not offer it. */
    val active: Boolean = true,
    /** A short fact for the seller, such as "الكرتونة 4 جالونات". */
    val note: String = "",
    /** The app product this item is, when it was picked from the product list — an exact match for invoices. */
    val productId: String = "",
) {
    /** The lowest price per carton the seller can ever reach. */
    val floor: Long get() = price - maxDiscount

    /** The prices the seller walks through while negotiating: the list price first, the lowest price last. */
    val ladder: List<Long>
        get() {
            if (price <= 0 || step <= 0 || maxDiscount <= 0) return listOf(price)
            val steps = ((maxDiscount + step - 1) / step).toInt()
            return (0..steps).map { level -> price - minOf(maxDiscount, level * step) }
        }
}

/**
 * What the page's AI seller may charge (document `bot_config/pricing`). It is manager-only on purpose: the lowest
 * prices must never be readable by a customer. The Worker / Apps Script read it with a service account and apply it
 * in code, so the language model only ever sees the price it is currently allowed to say.
 */
data class BotConfig(
    val enabled: Boolean = true,
    /** How long a quote stays valid, in hours. */
    val validHours: Long = BotDefaults.VALID_HOURS,
    val items: List<BotItem> = emptyList(),
) {
    companion object {
        /** What the settings screen starts from when nothing is saved yet. */
        fun initial(): BotConfig = BotConfig(
            items = scentItems(BotDefaults.PRICE, BotDefaults.MAX_DISCOUNT, BotDefaults.STEP),
        )

        /** The three scents sharing one price — also what the older single-price settings mean. */
        fun scentItems(price: Long, maxDiscount: Long, step: Long): List<BotItem> =
            BotDefaults.SCENTS.map { name ->
                BotItem(name = name, price = price, maxDiscount = maxDiscount, step = step, note = BotDefaults.SCENT_NOTE)
            }
    }
}

private fun stringKeyed(map: Map<*, *>): Map<String, Any?> = map.entries.associate { it.key.toString() to it.value }

/**
 * Null when nothing has been saved yet (the seller then uses its own defaults). Reads forgivingly: numbers may be
 * Long or Double. A document saved before items existed holds one price; it is shown as the three scents at that
 * price, so nothing changes until the manager edits it.
 */
internal fun DocumentSnapshot.toBotConfig(): BotConfig? {
    if (!exists()) return null
    val fields = data ?: return null
    fun number(value: Any?, fallback: Long): Long = (value as? Number)?.toLong() ?: fallback

    val items = (fields["items"] as? List<*>).orEmpty().mapNotNull { raw ->
        val map = stringKeyed(raw as? Map<*, *> ?: return@mapNotNull null)
        val name = (map["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return@mapNotNull null
        BotItem(
            id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: newBotItemId(),
            name = name,
            price = number(map["price"], BotDefaults.PRICE),
            maxDiscount = number(map["maxDiscount"], BotDefaults.MAX_DISCOUNT),
            step = number(map["step"], BotDefaults.STEP),
            active = map["active"] as? Boolean ?: true,
            note = (map["note"] as? String).orEmpty(),
            productId = (map["productId"] as? String).orEmpty(),
        )
    }
    return BotConfig(
        enabled = fields["enabled"] as? Boolean ?: true,
        validHours = number(fields["validHours"], BotDefaults.VALID_HOURS),
        items = items.ifEmpty {
            BotConfig.scentItems(
                price = number(fields["price"], BotDefaults.PRICE),
                maxDiscount = number(fields["maxDiscount"], BotDefaults.MAX_DISCOUNT),
                step = number(fields["step"], BotDefaults.STEP),
            )
        },
    )
}
