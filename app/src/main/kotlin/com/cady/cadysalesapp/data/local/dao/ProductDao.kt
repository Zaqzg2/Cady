package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    // Shared catalog — every active user reads the same table, no ownerUid filter.
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Delete
    suspend fun delete(product: ProductEntity)

    // --- Phase 6: sync status and backup support (no schema change — queries only) ---

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE syncStatus = 'PENDING'")
    suspend fun getPending(): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE syncStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    // --- Phase 6: flipping PENDING -> SYNCED once the cloud (or the manager's ack) confirmed a row ---

    @Query("UPDATE products SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)
}
