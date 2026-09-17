package com.cady.cadysalesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.ExportLogDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.dao.SyncLogDao
import com.cady.cadysalesapp.data.local.dao.UserAccountDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.ExportLogEntryEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.SyncLogEntryEntity
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity

/**
 * schemaVersion 1 here mirrors the Flutter app's own DbService.schemaVersion constant
 * (shown in the sync-hub UI) — bump both together if a migration ever changes the shape
 * of synced data, so the two apps' sync payloads stay mutually legible during rollout.
 */
@Database(
    entities = [
        CustomerEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        ReceiptEntity::class,
        UserAccountEntity::class,
        SyncLogEntryEntity::class,
        ExportLogEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CadyDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceItemDao(): InvoiceItemDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun exportLogDao(): ExportLogDao

    companion object {
        const val DATABASE_NAME = "cady.db"
    }
}
