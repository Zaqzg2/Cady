package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY endsAt DESC")
    fun observeAll(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers")
    suspend fun getAll(): List<OfferEntity>

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun getById(id: String): OfferEntity?

    @Query("SELECT * FROM offers WHERE syncStatus = 'PENDING'")
    suspend fun getPending(): List<OfferEntity>

    @Query("SELECT id FROM offers WHERE productId = :productId")
    suspend fun idsForProduct(productId: String): List<String>

    @Upsert
    suspend fun upsert(offer: OfferEntity)

    @Upsert
    suspend fun upsertAll(offers: List<OfferEntity>)

    @Query("DELETE FROM offers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offers WHERE productId = :productId")
    suspend fun deleteForProduct(productId: String)

    @Query("UPDATE offers SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT COUNT(*) FROM offers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM offers WHERE syncStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}
