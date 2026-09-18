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

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: String)
}
