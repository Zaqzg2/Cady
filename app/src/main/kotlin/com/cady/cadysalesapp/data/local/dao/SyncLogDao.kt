package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cady.cadysalesapp.data.local.entity.SyncLogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_log_entries ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<SyncLogEntryEntity>>

    @Query("SELECT * FROM sync_log_entries WHERE repId = :repId ORDER BY importedAt DESC LIMIT 1")
    suspend fun getLatestForRep(repId: String): SyncLogEntryEntity?

    @Insert
    suspend fun insert(entry: SyncLogEntryEntity)
}
