package com.cady.cadysalesapp.domain

import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity

/**
 * The same three numbers the current app's Invoice model computes on the fly
 * (subTotal / discountValue / grandTotal) — kept as one small pure-function
 * file instead of methods on the entity itself, since Room entities are
 * deliberately kept as plain data holders with no business logic attached.
 */
data class InvoiceTotals(
    val subTotal: Double,
    val discountValue: Double,
    val grandTotal: Double,
)

fun computeInvoiceTotals(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): InvoiceTotals =
    computeInvoiceTotals(invoice, items.sumOf { it.price * it.quantity })

/**
 * The same arithmetic from a line total that is already summed — the reports let the
 * database add up each invoice's lines, so they can total thousands of invoices without
 * loading every line into memory.
 */
fun computeInvoiceTotals(invoice: InvoiceEntity, subTotal: Double): InvoiceTotals {
    val percentDiscount = subTotal * (invoice.discountPercent / 100.0)
    val discountValue = percentDiscount + invoice.discountAmount
    val grandTotal = (subTotal - discountValue).coerceAtLeast(0.0)
    return InvoiceTotals(subTotal = subTotal, discountValue = discountValue, grandTotal = grandTotal)
}
