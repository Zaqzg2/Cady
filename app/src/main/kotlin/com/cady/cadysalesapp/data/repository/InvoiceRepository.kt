package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.sync.SyncRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val syncRepository: SyncRepository,
) {
    fun observeAll(ownerUid: String): Flow<List<InvoiceEntity>> = invoiceDao.observeAll(ownerUid)

    /** Manager only: every rep's invoices. */
    fun observeEveryone(): Flow<List<InvoiceEntity>> = invoiceDao.observeEveryone()

    fun observeForCustomer(customerId: String): Flow<List<InvoiceEntity>> = invoiceDao.observeForCustomer(customerId)

    fun observeItems(invoiceId: String): Flow<List<InvoiceItemEntity>> = invoiceItemDao.observeForInvoice(invoiceId)

    /** invoiceId → the sum of its lines (before discount), for lists that show an amount per invoice. */
    fun observeSubTotals(): Flow<Map<String, Double>> =
        invoiceItemDao.observeSubTotals().map { rows -> rows.associate { it.invoiceId to it.subTotal } }

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
        // Ownership is immutable: when a document is being edited (by its rep, or by a manager
        // correcting a rep's invoice) it keeps its original owner and rep name — the editor's own
        // account never takes it over.
        val previous = existingId?.let { invoiceDao.getById(it) }
        val ownerUid = previous?.ownerUid ?: ownerUid
        val repName = previous?.repName ?: repName
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
            isPinned = previous?.isPinned ?: false,
            syncStatus = SyncStatus.PENDING,
            ownerUid = ownerUid,
        )
        invoiceDao.upsert(invoice)
        invoiceItemDao.deleteForInvoice(id)
        invoiceItemDao.upsertAll(items)
        syncRepository.pushInvoice(invoice, items)
        return invoice
    }

    suspend fun deleteInvoice(id: String) {
        val owner = invoiceDao.getById(id)?.ownerUid
        invoiceDao.deleteById(id) // the lines go with it (ON DELETE CASCADE)
        syncRepository.deleteInvoiceRemote(id, owner)
    }

    suspend fun markPrinted(id: String) {
        invoiceDao.getById(id)?.let {
            val updated = it.copy(isPrinted = true)
            invoiceDao.upsert(updated)
            syncRepository.pushInvoice(updated, invoiceItemDao.getForInvoice(id))
        }
    }

    suspend fun togglePin(id: String) {
        invoiceDao.getById(id)?.let {
            val updated = it.copy(isPinned = !it.isPinned)
            invoiceDao.upsert(updated)
            syncRepository.pushInvoice(updated, invoiceItemDao.getForInvoice(id))
        }
    }
}
