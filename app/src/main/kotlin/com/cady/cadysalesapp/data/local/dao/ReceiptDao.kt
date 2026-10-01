package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts WHERE ownerUid = :ownerUid ORDER BY date DESC")
    fun observeAll(ownerUid: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE customerId = :customerId ORDER BY date DESC")
    fun observeForCustomer(customerId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE customerId = :customerId")
    suspend fun getForCustomerOnce(customerId: String): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE date BETWEEN :from AND :to AND ownerUid = :ownerUid")
    suspend fun getInRange(ownerUid: String, from: Instant, to: Instant): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE syncStatus = :status AND ownerUid = :ownerUid")
    suspend fun getByStatus(ownerUid: String, status: SyncStatus = SyncStatus.PENDING): List<ReceiptEntity>

    @Query("SELECT docNumber FROM receipts WHERE ownerUid = :ownerUid")
    suspend fun getDocNumbers(ownerUid: String): List<String>

    @Upsert
    suspend fun upsert(receipt: ReceiptEntity)

    @Upsert
    suspend fun upsertAll(receipts: List<ReceiptEntity>)

    @Query("SELECT COUNT(*) FROM receipts")
    suspend fun count(): Int

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: String)

    // --- Phase 6: sync status and backup support (no schema change — queries only) ---

    @Query("SELECT * FROM receipts")
    suspend fun getAll(): List<ReceiptEntity>

    @Query("SELECT COUNT(*) FROM receipts WHERE ownerUid = :ownerUid")
    fun observeCount(ownerUid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts WHERE ownerUid = :ownerUid AND syncStatus = 'PENDING'")
    fun observePendingCount(ownerUid: String): Flow<Int>

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()

    // --- Phase 6: flipping PENDING -> SYNCED once the cloud (or the manager's ack) confirmed a row ---

    @Query("UPDATE receipts SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Callers chunk [ids] (<= 500) to stay far below SQLite's bound-variable limit. */
    @Query("UPDATE receipts SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncedByIds(ids: List<String>)
}
