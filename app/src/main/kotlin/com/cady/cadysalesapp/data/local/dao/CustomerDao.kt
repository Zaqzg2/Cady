package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE ownerUid = :ownerUid ORDER BY isPinned DESC, name COLLATE NOCASE ASC")
    fun observeAll(ownerUid: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: String): Flow<CustomerEntity?>

    @Query(
        """SELECT * FROM customers WHERE ownerUid = :ownerUid
           AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
           ORDER BY isPinned DESC, name COLLATE NOCASE ASC"""
    )
    fun search(ownerUid: String, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE syncStatus = :status AND ownerUid = :ownerUid")
    suspend fun getByStatus(ownerUid: String, status: SyncStatus = SyncStatus.PENDING): List<CustomerEntity>

    /** Every manager needs to see every rep's customers, unlike a rep who only sees their own. */
    @Query("SELECT * FROM customers")
    suspend fun getAllForManager(): List<CustomerEntity>

    @Upsert
    suspend fun upsert(customer: CustomerEntity)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(customers: List<CustomerEntity>)

    // --- Phase 6: sync status, backup and CSV support (no schema change — queries only) ---

    /** Whole table, every owner — what a full backup snapshots. */
    @Query("SELECT * FROM customers")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE ownerUid = :ownerUid")
    suspend fun getAllByOwner(ownerUid: String): List<CustomerEntity>

    @Query("SELECT COUNT(*) FROM customers WHERE ownerUid = :ownerUid")
    fun observeCount(ownerUid: String): Flow<Int>

    /** Literal 'PENDING' matches Converters.syncStatusToString (enum stored by name). */
    @Query("SELECT COUNT(*) FROM customers WHERE ownerUid = :ownerUid AND syncStatus = 'PENDING'")
    fun observePendingCount(ownerUid: String): Flow<Int>

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    // --- Phase 6: flipping PENDING -> SYNCED once the cloud (or the manager's ack) confirmed a row ---

    @Query("UPDATE customers SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Callers chunk [ids] (<= 500) to stay far below SQLite's bound-variable limit. */
    @Query("UPDATE customers SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncedByIds(ids: List<String>)
}
