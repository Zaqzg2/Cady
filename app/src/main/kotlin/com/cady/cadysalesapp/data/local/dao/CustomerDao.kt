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
}
