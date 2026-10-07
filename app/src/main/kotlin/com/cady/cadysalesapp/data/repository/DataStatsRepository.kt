package com.cady.cadysalesapp.data.repository

import android.content.Context
import com.cady.cadysalesapp.data.local.CadyDatabase
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DataStats(
    val databaseSizeBytes: Long,
    val customersCount: Int,
    val productsCount: Int,
    val invoicesCount: Int,
    val receiptsCount: Int,
)

@Singleton
class DataStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val receiptDao: ReceiptDao,
) {
    suspend fun getStats(): DataStats = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(CadyDatabase.DATABASE_NAME)
        // Room also keeps a -wal file with uncommitted writes while the app is
        // open — included here so the size shown matches what's actually on
        // disk right now, not just the last-checkpointed main file.
        val walFile = context.getDatabasePath("${CadyDatabase.DATABASE_NAME}-wal")
        val size = (if (dbFile.exists()) dbFile.length() else 0L) + (if (walFile.exists()) walFile.length() else 0L)

        DataStats(
            databaseSizeBytes = size,
            customersCount = customerDao.count(),
            productsCount = productDao.count(),
            invoicesCount = invoiceDao.count(),
            receiptsCount = receiptDao.count(),
        )
    }
}
