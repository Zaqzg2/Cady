package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class InvoiceLineInput(val productId: String, val productName: String, val price: Double, val quantity: Double)

@Singleton
class InvoiceRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val customerRepository: CustomerRepository,
) {
    fun observeAll(ownerUid: String): Flow<List<InvoiceEntity>> = invoiceDao.observeAll(ownerUid)

    fun observeForCustomer(customerId: String): Flow<List<InvoiceEntity>> = invoiceDao.observeForCustomer(customerId)

    fun observeItems(invoiceId: String): Flow<List<InvoiceItemEntity>> = invoiceItemDao.observeForInvoice(invoiceId)

    suspend fun getById(id: String): InvoiceEntity? = invoiceDao.getById(id)

    suspend fun getItems(invoiceId: String): List<InvoiceItemEntity> = invoiceItemDao.getForInvoice(invoiceId)

    /** Highest numeric doc number actually used, plus one — never a plain running
        counter, so a rep hand-editing a number never causes a collision later. */
    suspend fun suggestNextDocNumber(ownerUid: String, kind: InvoiceKind): String {
        val existing = invoiceDao.getDocNumbers(ownerUid, kind)
        val maxUsed = existing.mapNotNull { it.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
        return (maxUsed + 1).toString()
    }

    suspend fun saveInvoice(
        ownerUid: String,
        repName: String?,
        existingId: String?,
        docNumber: String,
        date: Instant = Instant.now(),
        kind: InvoiceKind,
        customerId: String,
        customerName: String,
        paymentMode: PaymentMode,
        lines: List<InvoiceLineInput>,
        discountPercent: Double,
        discountAmount: Double,
        notes: String?,
        signaturePath: String?,
    ): InvoiceEntity {
        val id = existingId ?: UUID.randomUUID().toString()
        // Safe even when editing: excludes this invoice's own (pre-edit) effect
        // before applying the just-saved version, exactly like the Flutter app's
        // getCustomerBalanceExcluding usage in its invoice save path.
        val balanceBeforeThis = customerRepository.computeBalance(customerId, excludeDocId = id)

        val items = lines.map { line ->
            InvoiceItemEntity(
                id = UUID.randomUUID().toString(),
                invoiceId = id,
                productId = line.productId,
                productName = line.productName,
                price = line.price,
                quantity = line.quantity,
            )
        }
        val totals = computeInvoiceTotals(
            invoice = InvoiceEntity(
                id = id, docNumber = docNumber, date = date, kind = kind, customerId = customerId,
                customerName = customerName, paymentMode = paymentMode, discountPercent = discountPercent,
                discountAmount = discountAmount, notes = notes, signaturePath = signaturePath, repName = repName,
                balanceAfter = 0.0, isPrinted = false, isShared = false, isPinned = false,
                syncStatus = SyncStatus.PENDING, ownerUid = ownerUid,
            ),
            items = items,
        )
        val effect = when (kind) {
            InvoiceKind.SALE -> totals.grandTotal
            InvoiceKind.SALE_RETURN -> -totals.grandTotal
        }

        val invoice = InvoiceEntity(
            id = id,
            docNumber = docNumber,
            date = date,
            kind = kind,
            customerId = customerId,
            customerName = customerName,
            paymentMode = paymentMode,
            discountPercent = discountPercent,
            discountAmount = discountAmount,
            notes = notes,
            signaturePath = signaturePath,
            repName = repName,
            balanceAfter = balanceBeforeThis + effect,
            isPrinted = false,
            isShared = false,
            isPinned = false,
            syncStatus = SyncStatus.PENDING,
            ownerUid = ownerUid,
        )
        invoiceDao.upsert(invoice)
        invoiceItemDao.deleteForInvoice(id)
        invoiceItemDao.upsertAll(items)
        return invoice
    }

    suspend fun deleteInvoice(id: String) = invoiceDao.deleteById(id)

    suspend fun markPrinted(id: String) {
        invoiceDao.getById(id)?.let { invoiceDao.upsert(it.copy(isPrinted = true)) }
    }

    suspend fun togglePin(id: String) {
        invoiceDao.getById(id)?.let { invoiceDao.upsert(it.copy(isPinned = !it.isPinned)) }
    }
}
