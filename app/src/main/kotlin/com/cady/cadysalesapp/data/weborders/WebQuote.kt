package com.cady.cadysalesapp.data.weborders

import com.cady.cadysalesapp.data.sync.anyToInstant
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

data class WebQuoteItem(val scent: String, val cartons: Long, val unit: Long, val sum: Long)

/**
 * An offer the AI seller built for a visitor of the sales page (collection `quotes`). Only the Worker writes
 * these (with a service account), so a quote here is what the seller really offered, whatever a customer's
 * order later claims.
 */
data class WebQuote(
    /** The code the customer sees on the card ("Q-AB12CD") and that their order carries. */
    val ref: String,
    val items: List<WebQuoteItem>,
    /** The price of one carton in this offer. */
    val unit: Long,
    /** The list price at the time. */
    val list: Long,
    val total: Long,
    val cartons: Long,
    /** True when this was the lowest price the seller could give. */
    val isFinal: Boolean,
    val updatedAt: Instant?,
)

internal fun DocumentSnapshot.toWebQuote(): WebQuote? {
    val fields = data ?: return null
    fun number(value: Any?): Long? = (value as? Number)?.toLong()
    val items = (fields["items"] as? List<*>).orEmpty().mapNotNull { raw ->
        val map = (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: return@mapNotNull null
        val scent = map["scent"] as? String ?: return@mapNotNull null
        WebQuoteItem(scent, number(map["cartons"]) ?: 0L, number(map["unit"]) ?: 0L, number(map["sum"]) ?: 0L)
    }
    return WebQuote(
        ref = fields["ref"] as? String ?: id,
        items = items,
        unit = number(fields["unit"]) ?: 0L,
        list = number(fields["list"]) ?: 0L,
        total = number(fields["total"]) ?: 0L,
        cartons = number(fields["cartons"]) ?: 0L,
        isFinal = fields["final"] as? Boolean ?: false,
        updatedAt = anyToInstant(fields["updatedAt"]),
    )
}
