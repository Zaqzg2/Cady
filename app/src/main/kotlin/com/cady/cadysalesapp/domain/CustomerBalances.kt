package com.cady.cadysalesapp.domain

import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity

/**
 * Every customer's balance in one pass: opening balance + sales − returns − receipts, exactly the formula of
 * [com.cady.cadysalesapp.data.repository.CustomerRepository.computeBalance] but over lists the screen already
 * holds, so a customers list can show a balance on every row without a database query per customer.
 * Positive = the customer owes; negative = the customer is in credit.
 */
fun computeCustomerBalances(
    customers: List<CustomerEntity>,
    invoices: List<InvoiceEntity>,
    receipts: List<ReceiptEntity>,
    subTotals: Map<String, Double>,
): Map<String, Double> {
    val owed = HashMap<String, Double>()
    for (invoice in invoices) {
        val total = computeInvoiceTotals(invoice, subTotals[invoice.id] ?: 0.0).grandTotal
        val signed = if (invoice.kind == InvoiceKind.SALE_RETURN) -total else total
        owed[invoice.customerId] = (owed[invoice.customerId] ?: 0.0) + signed
    }
    for (receipt in receipts) {
        owed[receipt.customerId] = (owed[receipt.customerId] ?: 0.0) - receipt.amount
    }
    val result = HashMap<String, Double>(customers.size * 2)
    for (customer in customers) {
        result[customer.id] = customer.openingBalance + (owed[customer.id] ?: 0.0)
    }
    return result
}
