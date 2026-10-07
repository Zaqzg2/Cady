package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import kotlinx.coroutines.flow.Flow

/** One invoice's line total (price × quantity, summed), as computed by the database. */
data class InvoiceSubTotal(val invoiceId: String, val subTotal: Double)

/** What one invoice sold of one product (lines of the same product added up) — the dashboard's "top products". */
data class InvoiceProductLine(
    val invoiceId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val amount: Double,
)

@Dao
interface InvoiceItemDao {
    /** One small row per invoice — what the reports need, without loading every line. */
    @Query("SELECT invoiceId AS invoiceId, SUM(price * quantity) AS subTotal FROM invoice_items GROUP BY invoiceId")
    fun observeSubTotals(): Flow<List<InvoiceSubTotal>>

    /** One row per (invoice, product): enough for product rankings without loading every line. */
    @Query(
        "SELECT invoiceId AS invoiceId, productId AS productId, MAX(productName) AS productName, " +
            "SUM(quantity) AS quantity, SUM(price * quantity) AS amount " +
            "FROM invoice_items GROUP BY invoiceId, productId"
    )
    fun observeProductLines(): Flow<List<InvoiceProductLine>>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun observeForInvoice(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getForInvoice(invoiceId: String): List<InvoiceItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<InvoiceItemEntity>)

    // No explicit delete-by-invoice needed — the CASCADE foreign key on InvoiceEntity
    // handles that automatically when an invoice row is deleted.
    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteForInvoice(invoiceId: String)

    // --- Phase 6: backup/restore support ---

    @Query("SELECT * FROM invoice_items")
    suspend fun getAll(): List<InvoiceItemEntity>

    @Query("DELETE FROM invoice_items")
    suspend fun deleteAll()
}
