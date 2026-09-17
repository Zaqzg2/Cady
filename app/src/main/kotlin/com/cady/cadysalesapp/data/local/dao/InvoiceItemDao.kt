package com.cady.cadysalesapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceItemDao {
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
}
