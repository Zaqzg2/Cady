package com.cady.cadysalesapp.data.weborders

import com.google.firebase.firestore.DocumentSnapshot

/**
 * What the page's AI seller may charge (document `bot_config/pricing`). It is manager-only on purpose: the lowest
 * price must never be readable by a customer. The Worker reads it with a service account and applies it in code,
 * so the language model only ever sees the price it is currently allowed to say.
 */
data class BotConfig(
    val enabled: Boolean = true,
    /** The list price of one carton. */
    val price: Long = 7_000,
    /** The most the seller can take off one carton in total. */
    val maxDiscount: Long = 200,
    /** How much one concession takes off. */
    val step: Long = 100,
    /** How long a quote stays valid, in hours. */
    val validHours: Long = 24,
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

/** Null when nothing has been saved yet (the Worker then uses its own defaults). Reads forgivingly: numbers may be Long or Double. */
internal fun DocumentSnapshot.toBotConfig(): BotConfig? {
    if (!exists()) return null
    val fields = data ?: return null
    val defaults = BotConfig()
    fun number(key: String, fallback: Long): Long = (fields[key] as? Number)?.toLong() ?: fallback
    return BotConfig(
        enabled = fields["enabled"] as? Boolean ?: defaults.enabled,
        price = number("price", defaults.price),
        maxDiscount = number("maxDiscount", defaults.maxDiscount),
        step = number("step", defaults.step),
        validHours = number("validHours", defaults.validHours),
    )
}
