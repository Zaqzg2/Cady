package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "invoices",
    indices = [Index("customerId"), Index("ownerUid"), Index("syncStatus"), Index("docNumber")]
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val docNumber: String,
    val date: Instant,
    val kind: InvoiceKind,
    val customerId: String,
    /** Denormalized so the documents list / timeline don't need a join just to render a name. */
    val customerName: String,
    val paymentMode: PaymentMode,
    /** Percent and flat-amount discounts are independent, additive fields — matches
        the current invoice screen, which lets a rep apply either or both. */
    val discountPercent: Double,
    val discountAmount: Double,
    val notes: String?,
    val signaturePath: String?,
    val repName: String?,
    /** Snapshot of the customer's running balance right after this document —
        drives the fast statement/timeline render without recomputing the ledger. */
    val balanceAfter: Double,
    val isPrinted: Boolean,
    val isShared: Boolean,
    val isPinned: Boolean,
    val syncStatus: SyncStatus,
    val ownerUid: String,
)
