package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.sync.SyncRepository
import com.cady.cadysalesapp.domain.LedgerRow
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of [CustomerRepository.deleteCustomer]. */
sealed interface CustomerDeleteResult {
    data object Deleted : CustomerDeleteResult

    /** Refused: deleting would orphan this many invoices/receipts — the customer can be deactivated instead. */
    data class HasDocuments(val count: Int) : CustomerDeleteResult
}

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val syncRepository: SyncRepository,
) {
    fun observeAll(ownerUid: String): Flow<List<CustomerEntity>> = customerDao.observeAll(ownerUid)

    /** Manager only: every rep's customers. */
    fun observeEveryone(): Flow<List<CustomerEntity>> = customerDao.observeEveryone()

    fun searchEveryone(query: String): Flow<List<CustomerEntity>> = customerDao.searchEveryone(query)

    fun observeById(id: String): Flow<CustomerEntity?> = customerDao.observeById(id)

    fun search(ownerUid: String, query: String): Flow<List<CustomerEntity>> =
        customerDao.search(ownerUid, query)

    suspend fun getById(id: String): CustomerEntity? = customerDao.getById(id)

    companion object {
        const val CASH_CUSTOMER_NAME = "عميل نقدي"
    }

    suspend fun createCustomer(
        ownerUid: String,
        name: String,
        phone: String?,
        address: String?,
        openingBalance: Double,
        creditLimit: Double?,
        notes: String?,
    ): CustomerEntity {
        val customer = CustomerEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            phone = phone,
            address = address,
            openingBalance = openingBalance,
            isPinned = false,
            isActive = true,
            creditLimit = creditLimit,
            notes = notes,
            syncStatus = SyncStatus.PENDING,
            updatedAt = Instant.now(),
            ownerUid = ownerUid,
        )
        customerDao.upsert(customer)
        syncRepository.pushCustomer(customer)
        return customer
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val updated = customer.copy(syncStatus = SyncStatus.PENDING, updatedAt = Instant.now())
        customerDao.upsert(updated)
        syncRepository.pushCustomer(updated)
    }

    /**
     * Deletes a customer — only one with no invoices or receipts. A customer who has documents
     * is never deleted (their history would be left pointing at nobody and the balance would
     * vanish); the caller offers to deactivate instead ([setActive]).
     */
    suspend fun deleteCustomer(customerId: String): CustomerDeleteResult {
        val customer = customerDao.getById(customerId) ?: return CustomerDeleteResult.Deleted
        val documents = invoiceDao.getForCustomerOnce(customerId).size + receiptDao.getForCustomerOnce(customerId).size
        if (documents > 0) return CustomerDeleteResult.HasDocuments(documents)
        customerDao.deleteById(customerId)
        syncRepository.deleteCustomerRemote(customerId, customer.ownerUid)
        return CustomerDeleteResult.Deleted
    }

    /**
     * The walk-in "عميل نقدي" of this account: found by name, created the first time it is
     * needed. Home's "عميل نقدي" shortcut opens a sale with it already chosen.
     */
    suspend fun findOrCreateCashCustomer(ownerUid: String): CustomerEntity {
        customerDao.getAllByOwner(ownerUid).firstOrNull { it.name == CASH_CUSTOMER_NAME }?.let { return it }
        return createCustomer(
            ownerUid = ownerUid, name = CASH_CUSTOMER_NAME, phone = null, address = null,
            openingBalance = 0.0, creditLimit = null, notes = null,
        )
    }

    suspend fun togglePin(customerId: String) {
        val customer = customerDao.getById(customerId) ?: return
        val updated = customer.copy(isPinned = !customer.isPinned, syncStatus = SyncStatus.PENDING, updatedAt = Instant.now())
        customerDao.upsert(updated)
        syncRepository.pushCustomer(updated)
    }

    suspend fun setActive(customerId: String, isActive: Boolean) {
        val customer = customerDao.getById(customerId) ?: return
        val updated = customer.copy(isActive = isActive, syncStatus = SyncStatus.PENDING, updatedAt = Instant.now())
        customerDao.upsert(updated)
        syncRepository.pushCustomer(updated)
    }

    /**
     * openingBalance + every SALE grandTotal - every SALE_RETURN grandTotal -
     * every receipt amount. Mirrors AppProvider.getCustomerBalanceExcluding
     * exactly, including excludeDocId: the invoice/receipt edit screens pass the
     * document's own id so editing an existing document previews the correct
     * post-edit balance instead of double-counting the pre-edit version.
     */
    suspend fun computeBalance(customerId: String, excludeDocId: String? = null): Double {
        val customer = customerDao.getById(customerId) ?: return 0.0

        val invoiceEffect = invoiceDao.getForCustomerOnce(customerId)
            .filter { it.id != excludeDocId }
            .sumOf { invoice ->
                val items = invoiceItemDao.getForInvoice(invoice.id)
                val grandTotal = computeInvoiceTotals(invoice, items).grandTotal
                when (invoice.kind) {
                    InvoiceKind.SALE -> grandTotal
                    InvoiceKind.SALE_RETURN -> -grandTotal
                }
            }

        val receiptsTotal = receiptDao.getForCustomerOnce(customerId)
            .filter { it.id != excludeDocId }
            .sumOf { it.amount }

        return customer.openingBalance + invoiceEffect - receiptsTotal
    }

    /** Full chronological statement for PDF/print — same math as computeBalance,
        but keeps every step's running balance instead of just the final number. */
    suspend fun getLedger(customerId: String): List<LedgerRow> {
        val customer = customerDao.getById(customerId) ?: return emptyList()
        val invoices = invoiceDao.getForCustomerOnce(customerId)
        val receipts = receiptDao.getForCustomerOnce(customerId)

        data class RawEntry(val date: Instant, val description: String, val docNumber: String?, val debit: Double, val credit: Double)

        val entries = invoices.map { invoice ->
            val items = invoiceItemDao.getForInvoice(invoice.id)
            val grandTotal = computeInvoiceTotals(invoice, items).grandTotal
            when (invoice.kind) {
                InvoiceKind.SALE -> RawEntry(invoice.date, "فاتورة بيع", invoice.docNumber, debit = grandTotal, credit = 0.0)
                InvoiceKind.SALE_RETURN -> RawEntry(invoice.date, "فاتورة مرتجع", invoice.docNumber, debit = 0.0, credit = grandTotal)
            }
        } + receipts.map { receipt ->
            RawEntry(receipt.date, "سند قبض", receipt.docNumber, debit = 0.0, credit = receipt.amount)
        }

        var running = customer.openingBalance
        val rows = mutableListOf(
            LedgerRow(
                date = customer.updatedAt, description = "رصيد افتتاحي", docNumber = null,
                debit = 0.0, credit = 0.0, runningBalance = running,
            )
        )
        entries.sortedBy { it.date }.forEach { entry ->
            running += entry.debit - entry.credit
            rows += LedgerRow(
                date = entry.date, description = entry.description, docNumber = entry.docNumber,
                debit = entry.debit, credit = entry.credit, runningBalance = running,
            )
        }
        return rows
    }
}
