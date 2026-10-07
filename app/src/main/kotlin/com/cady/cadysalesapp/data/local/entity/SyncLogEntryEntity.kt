package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** One row per manual (file-based) import a manager has approved from a rep. */
@Entity(tableName = "sync_log_entries")
data class SyncLogEntryEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val repId: String,
    val repDisplayName: String,
    val importedAt: Instant,
    val customersCount: Int,
    val productsCount: Int,
    val invoicesCount: Int,
    val receiptsCount: Int,
    val duplicatesCount: Int,
    val errorsCount: Int,
)
