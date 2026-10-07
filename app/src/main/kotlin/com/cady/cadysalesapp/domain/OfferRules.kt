package com.cady.cadysalesapp.domain

import com.cady.cadysalesapp.data.local.entity.OfferEntity
import java.time.Instant
import kotlin.math.round

enum class OfferStatus { ACTIVE, UPCOMING, EXPIRED, PAUSED }

/**
 * The one place that decides when a promotion applies, so the invoice, the offers list and the
 * products list can never disagree. An offer is "a percentage off one product, between two dates
 * (both days included)"; the manager can also pause it by hand without touching the dates.
 */
object OfferRules {

    fun statusOf(offer: OfferEntity, moment: Instant): OfferStatus = when {
        !offer.isActive -> OfferStatus.PAUSED
        moment.isBefore(offer.startsAt) -> OfferStatus.UPCOMING
        moment.isAfter(offer.endsAt) -> OfferStatus.EXPIRED
        else -> OfferStatus.ACTIVE
    }

    /** The offer that applies to [productId] at [moment]; when several overlap, the biggest discount wins. */
    fun bestFor(productId: String, offers: List<OfferEntity>, moment: Instant): OfferEntity? =
        offers
            .filter { it.productId == productId && statusOf(it, moment) == OfferStatus.ACTIVE }
            .maxByOrNull { it.percent }

    /** The unit price after the discount, rounded to two decimals. */
    fun discountedPrice(price: Double, percent: Double): Double =
        round(price * (100.0 - percent) / 100.0 * 100.0) / 100.0

    /** "10" for 10.0 and "12.5" for 12.5 — never "10.0". */
    fun percentText(percent: Double): String =
        if (percent == percent.toLong().toDouble()) percent.toLong().toString() else percent.toString()
}
