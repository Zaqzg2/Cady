package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "customers",
    indices = [Index("ownerUid"), Index("syncStatus")]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val openingBalance: Double,
    val isPinned: Boolean,
    val isActive: Boolean,
    val creditLimit: Double?,
    val notes: String?,
    val syncStatus: SyncStatus,
    val updatedAt: Instant,
    /** Locked at first write, never reassigned even if a manager later edits the record —
        matches firestore.rules, which checks this field for read/update/delete ownership. */
    val ownerUid: String,
)
