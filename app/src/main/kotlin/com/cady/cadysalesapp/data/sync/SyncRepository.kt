package com.cady.cadysalesapp.data.sync

import android.util.Log
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * schemaVersion 1 — bump alongside CadyDatabase's Room version if a migration
 * ever changes the shape of what gets synced, so this and a still-installed
 * Flutter copy (or an older native build) can tell they've drifted apart.
 */
const val SYNC_SCHEMA_VERSION = 1

@Singleton
class SyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
) {
    // A SupervisorJob-scoped launcher for push calls: local writes must never
    // block on network, and one push failing must never cancel sibling pushes
    // — the same "best effort in the background" contract as the Flutter
    // app's unawaited()+catchError() push calls.
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun logPushFailure(kind: String, id: String, e: Exception) {
        Log.w("CadySync", "Background push failed for $kind/$id: ${e.message}")
    }

    fun pushCustomer(customer: CustomerEntity) {
        pushScope.launch {
            try {
                firestore.collection("customers").document(customer.id).set(customer.toFirestoreMap()).await()
            } catch (e: Exception) {
                logPushFailure("customer", customer.id, e)
            }
        }
    }

    fun pushProduct(product: ProductEntity) {
        pushScope.launch {
            try {
                firestore.collection("products").document(product.id).set(product.toFirestoreMap()).await()
            } catch (e: Exception) {
                logPushFailure("product", product.id, e)
            }
        }
    }

    fun pushInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        pushScope.launch {
            try {
                firestore.collection("invoices").document(invoice.id).set(invoice.toFirestoreMap(items)).await()
            } catch (e: Exception) {
                logPushFailure("invoice", invoice.id, e)
            }
        }
    }

    fun pushReceipt(receipt: ReceiptEntity) {
        pushScope.launch {
            try {
                firestore.collection("receipts").document(receipt.id).set(receipt.toFirestoreMap()).await()
            } catch (e: Exception) {
                logPushFailure("receipt", receipt.id, e)
            }
        }
    }

    /**
     * Full pull for the given owner: last-write-wins by comparing updatedAt
     * against what's already local, and — critically — never deletes a local
     * row just because it's momentarily missing from a partial/offline pull.
     * Products are the shared catalog (see ProductDao's own comment) and pull
     * for every user regardless of who authored them.
     */
    suspend fun pullFromFirestore(ownerUid: String, isManager: Boolean) {
        pullCustomers(ownerUid, isManager)
        pullProducts()
        pullInvoices(ownerUid, isManager)
        pullReceipts(ownerUid, isManager)
    }

    private suspend fun pullCustomers(ownerUid: String, isManager: Boolean) {
        val query = if (isManager) {
            firestore.collection("customers")
        } else {
            firestore.collection("customers").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.get().await()
        val incoming = snapshot.documents.mapNotNull { it.toCustomerEntity() }
        for (remote in incoming) {
            val local = customerDao.getById(remote.id)
            if (local == null || !remote.updatedAt.isBefore(local.updatedAt)) {
                customerDao.upsert(remote)
            }
        }
    }

    private suspend fun pullProducts() {
        val snapshot = firestore.collection("products").get().await()
        val incoming = snapshot.documents.mapNotNull { it.toProductEntity() }
        productDao.upsertAll(incoming)
    }

    private suspend fun pullInvoices(ownerUid: String, isManager: Boolean) {
        val query = if (isManager) {
            firestore.collection("invoices")
        } else {
            firestore.collection("invoices").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.get().await()
        for (doc in snapshot.documents) {
            val remote = doc.toInvoiceEntity() ?: continue
            // Simplification worth revisiting: invoices have no updatedAt field
            // (unlike customers), so an existing local copy is treated as
            // current rather than diffed — correct for the common
            // create-once case, but a rare post-creation edit synced from
            // elsewhere wouldn't overwrite an already-pulled local copy.
            if (invoiceDao.getById(remote.id) == null) {
                invoiceDao.upsert(remote)
                invoiceItemDao.upsertAll(doc.toInvoiceItemEntities(remote.id))
            }
        }
    }

    private suspend fun pullReceipts(ownerUid: String, isManager: Boolean) {
        val query = if (isManager) {
            firestore.collection("receipts")
        } else {
            firestore.collection("receipts").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.get().await()
        val incoming = snapshot.documents.mapNotNull { it.toReceiptEntity() }
        for (remote in incoming) {
            if (receiptDao.getById(remote.id) == null) {
                receiptDao.upsert(remote)
            }
        }
    }
}
