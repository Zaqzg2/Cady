package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
) {
    fun observeAll(ownerUid: String): Flow<List<CustomerEntity>> = customerDao.observeAll(ownerUid)

    fun observeById(id: String): Flow<CustomerEntity?> = customerDao.observeById(id)

    fun search(ownerUid: String, query: String): Flow<List<CustomerEntity>> =
        customerDao.search(ownerUid, query)

    suspend fun getById(id: String): CustomerEntity? = customerDao.getById(id)

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
        return customer
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.upsert(customer.copy(syncStatus = SyncStatus.PENDING, updatedAt = Instant.now()))
    }

    suspend fun togglePin(customerId: String) {
        val customer = customerDao.getById(customerId) ?: return
        customerDao.upsert(
            customer.copy(isPinned = !customer.isPinned, syncStatus = SyncStatus.PENDING, updatedAt = Instant.now())
        )
    }

    suspend fun setActive(customerId: String, isActive: Boolean) {
        val customer = customerDao.getById(customerId) ?: return
        customerDao.upsert(customer.copy(isActive = isActive, syncStatus = SyncStatus.PENDING, updatedAt = Instant.now()))
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
}
