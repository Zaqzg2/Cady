package com.cady.cadysalesapp.data.repository

import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val customerRepository: CustomerRepository,
) {
    fun observeAll(ownerUid: String): Flow<List<ReceiptEntity>> = receiptDao.observeAll(ownerUid)

    fun observeForCustomer(customerId: String): Flow<List<ReceiptEntity>> = receiptDao.observeForCustomer(customerId)

    suspend fun getById(id: String): ReceiptEntity? = receiptDao.getById(id)

    suspend fun suggestNextDocNumber(ownerUid: String): String {
        val existing = receiptDao.getDocNumbers(ownerUid)
        val maxUsed = existing.mapNotNull { it.filter(Char::isDigit).toIntOrNull() }.maxOrNull() ?: 0
        return (maxUsed + 1).toString()
    }

    suspend fun saveReceipt(
        ownerUid: String,
        repName: String?,
        existingId: String?,
        docNumber: String,
        customerId: String,
        customerName: String,
        amount: Double,
        method: ReceiptMethod,
        repSignaturePath: String?,
        notes: String?,
    ): ReceiptEntity {
        val id = existingId ?: UUID.randomUUID().toString()
        val balanceBeforeThis = customerRepository.computeBalance(customerId, excludeDocId = id)

        val receipt = ReceiptEntity(
            id = id,
            docNumber = docNumber,
            date = Instant.now(),
            amount = amount,
            method = method,
            customerId = customerId,
            customerName = customerName,
            repSignaturePath = repSignaturePath,
            repName = repName,
            notes = notes,
            // A receipt reduces what the customer owes, unlike an invoice's effect.
            balanceAfter = balanceBeforeThis - amount,
            isPrinted = false,
            isShared = false,
            isPinned = false,
            syncStatus = SyncStatus.PENDING,
            ownerUid = ownerUid,
        )
        receiptDao.upsert(receipt)
        return receipt
    }

    suspend fun deleteReceipt(id: String) = receiptDao.deleteById(id)

    suspend fun markPrinted(id: String) {
        receiptDao.getById(id)?.let { receiptDao.upsert(it.copy(isPrinted = true)) }
    }

    suspend fun togglePin(id: String) {
        receiptDao.getById(id)?.let { receiptDao.upsert(it.copy(isPinned = !it.isPinned)) }
    }
}
