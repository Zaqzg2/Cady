package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE ownerUid = :ownerUid ORDER BY date DESC")
    fun observeAll(ownerUid: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY date DESC")
    fun observeForCustomer(customerId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE date BETWEEN :from AND :to AND ownerUid = :ownerUid")
    suspend fun getInRange(ownerUid: String, from: Instant, to: Instant): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE syncStatus = :status AND ownerUid = :ownerUid")
    suspend fun getByStatus(ownerUid: String, status: SyncStatus = SyncStatus.PENDING): List<InvoiceEntity>

    /** Raw doc numbers for the numbering service to scan for the real highest-used
        value — never a simple counter, since a rep can hand-edit a document number. */
    @Query("SELECT docNumber FROM invoices WHERE kind = :kind AND ownerUid = :ownerUid")
    suspend fun getDocNumbers(ownerUid: String, kind: InvoiceKind): List<String>

    @Upsert
    suspend fun upsert(invoice: InvoiceEntity)

    @Upsert
    suspend fun upsertAll(invoices: List<InvoiceEntity>)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: String)
}
