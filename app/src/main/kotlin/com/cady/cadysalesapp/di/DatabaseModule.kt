package com.cady.cadysalesapp.di

import android.content.Context
import androidx.room.Room
import com.cady.cadysalesapp.data.local.CadyDatabase
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.ExportLogDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.dao.SyncLogDao
import com.cady.cadysalesapp.data.local.dao.UserAccountDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CadyDatabase =
        Room.databaseBuilder(context, CadyDatabase::class.java, CadyDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideCustomerDao(db: CadyDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideProductDao(db: CadyDatabase): ProductDao = db.productDao()

    @Provides
    fun provideInvoiceDao(db: CadyDatabase): InvoiceDao = db.invoiceDao()

    @Provides
    fun provideInvoiceItemDao(db: CadyDatabase): InvoiceItemDao = db.invoiceItemDao()

    @Provides
    fun provideReceiptDao(db: CadyDatabase): ReceiptDao = db.receiptDao()

    @Provides
    fun provideUserAccountDao(db: CadyDatabase): UserAccountDao = db.userAccountDao()

    @Provides
    fun provideSyncLogDao(db: CadyDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    fun provideExportLogDao(db: CadyDatabase): ExportLogDao = db.exportLogDao()
}
