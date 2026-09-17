package com.cady.cadysalesapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A real child table + foreign key, unlike the Flutter app's approach of embedding
 * the item list directly inside the Invoice's Hive record. This is a genuine
 * relational upgrade (not just a port): it lets a later "which invoices contain
 * product X" report query items directly instead of deserializing every invoice.
 * CASCADE delete keeps line items from ever outliving their invoice.
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("invoiceId"), Index("productId")]
)
data class InvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val productId: String,
    /** Denormalized at the moment of sale so a later price change never rewrites history. */
    val productName: String,
    val price: Double,
    val quantity: Double,
)
