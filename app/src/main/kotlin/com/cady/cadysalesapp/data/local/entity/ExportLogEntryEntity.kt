package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** One row per "create update" package a manager has pushed out to one rep or all reps. */
@Entity(tableName = "export_log_entries")
data class ExportLogEntryEntity(
    @PrimaryKey val id: String,
    val createdAt: Instant,
    /** Null means the update targeted every rep, not one specific device. */
    val targetRepId: String?,
    val targetRepDisplayName: String,
    val fileName: String,
    val includesProducts: Boolean,
    val includesCustomers: Boolean,
    val includesSettings: Boolean,
)
