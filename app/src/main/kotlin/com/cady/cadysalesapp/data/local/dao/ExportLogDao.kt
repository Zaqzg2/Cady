package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cady.cadysalesapp.data.local.entity.ExportLogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportLogDao {
    @Query("SELECT * FROM export_log_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportLogEntryEntity>>

    @Insert
    suspend fun insert(entry: ExportLogEntryEntity)
}
