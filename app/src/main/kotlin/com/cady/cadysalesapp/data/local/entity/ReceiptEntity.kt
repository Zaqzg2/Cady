package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "receipts",
    indices = [Index("customerId"), Index("ownerUid"), Index("syncStatus"), Index("docNumber")]
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val docNumber: String,
    val date: Instant,
    val amount: Double,
    val method: ReceiptMethod,
    val customerId: String,
    val customerName: String,
    val repSignaturePath: String?,
    val repName: String?,
    val notes: String?,
    val balanceAfter: Double,
    val isPrinted: Boolean,
    val isShared: Boolean,
    val isPinned: Boolean,
    val syncStatus: SyncStatus,
    val ownerUid: String,
)
