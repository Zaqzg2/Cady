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
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
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

    // ---- "Now" variants: suspend, throw on failure, flip the row to SYNCED on success ----
    //
    // Phase 6 fix: the original push functions never touched the local syncStatus, so
    // every row a rep created stayed PENDING forever and "what is still waiting?" could
    // not be answered honestly. A row only flips to SYNCED if it is *still exactly what
    // was pushed* (compare-and-set) — an edit made while the push was in flight keeps
    // its own PENDING flag until its own push is acknowledged.
    //
    // Note on offline behaviour: Firestore's set() Task only completes once the server
    // has acknowledged the write, so while the device is offline these suspend (the write
    // stays queued inside Firestore and is sent on reconnect). The fire-and-forget
    // wrappers below are fine with that; the manual "sync now" wraps each call in a timeout.

    suspend fun pushCustomerNow(customer: CustomerEntity) {
        firestore.collection("customers").document(customer.id).set(customer.toFirestoreMap()).await()
        val current = customerDao.getById(customer.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == customer.copy(syncStatus = SyncStatus.PENDING)) {
            customerDao.markSynced(customer.id)
        }
    }

    suspend fun pushProductNow(product: ProductEntity) {
        firestore.collection("products").document(product.id).set(product.toFirestoreMap()).await()
        val current = productDao.getById(product.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == product.copy(syncStatus = SyncStatus.PENDING)) {
            productDao.markSynced(product.id)
        }
    }

    suspend fun pushInvoiceNow(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        firestore.collection("invoices").document(invoice.id).set(invoice.toFirestoreMap(items)).await()
        val current = invoiceDao.getById(invoice.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == invoice.copy(syncStatus = SyncStatus.PENDING)) {
            invoiceDao.markSynced(invoice.id)
        }
    }

    suspend fun pushReceiptNow(receipt: ReceiptEntity) {
        firestore.collection("receipts").document(receipt.id).set(receipt.toFirestoreMap()).await()
        val current = receiptDao.getById(receipt.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == receipt.copy(syncStatus = SyncStatus.PENDING)) {
            receiptDao.markSynced(receipt.id)
        }
    }

    // ---- Fire-and-forget variants (unchanged contract for the repositories) ----

    fun pushCustomer(customer: CustomerEntity) {
        pushScope.launch {
            try {
                pushCustomerNow(customer)
            } catch (e: Exception) {
                logPushFailure("customer", customer.id, e)
            }
        }
    }

    fun pushProduct(product: ProductEntity) {
        pushScope.launch {
            try {
                pushProductNow(product)
            } catch (e: Exception) {
                logPushFailure("product", product.id, e)
            }
        }
    }

    fun pushInvoice(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        pushScope.launch {
            try {
                pushInvoiceNow(invoice, items)
            } catch (e: Exception) {
                logPushFailure("invoice", invoice.id, e)
            }
        }
    }

    fun pushReceipt(receipt: ReceiptEntity) {
        pushScope.launch {
            try {
                pushReceiptNow(receipt)
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
     *
     * [serverOnly] = true forces every read to hit the server (Source.SERVER) and throw if
     * it can't — used by the manual "sync now", so a pull answered from Firestore's local
     * cache is never reported as a successful sync. Login keeps the default (cache fallback).
     *
     * Returns how many local rows were actually written (new or changed), per kind.
     */
    suspend fun pullFromFirestore(ownerUid: String, isManager: Boolean, serverOnly: Boolean = false): RecordCounts {
        val customers = pullCustomers(ownerUid, isManager, serverOnly)
        val products = pullProducts(serverOnly)
        val invoices = pullInvoices(ownerUid, isManager, serverOnly)
        val receipts = pullReceipts(ownerUid, isManager, serverOnly)
        return RecordCounts(customers, products, invoices, receipts)
    }

    private suspend fun Query.fetch(serverOnly: Boolean): QuerySnapshot =
        (if (serverOnly) get(Source.SERVER) else get()).await()

    private suspend fun pullCustomers(ownerUid: String, isManager: Boolean, serverOnly: Boolean): Int {
        val query: Query = if (isManager) {
            firestore.collection("customers")
        } else {
            firestore.collection("customers").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        val incoming = snapshot.documents.mapNotNull { it.toCustomerEntity() }
        var written = 0
        for (remote in incoming) {
            val local = customerDao.getById(remote.id)
            if (local == null || !remote.updatedAt.isBefore(local.updatedAt)) {
                if (local != remote) {
                    customerDao.upsert(remote)
                    written++
                }
            }
        }
        return written
    }

    private suspend fun pullProducts(serverOnly: Boolean): Int {
        val snapshot = firestore.collection("products").fetch(serverOnly)
        val incoming = snapshot.documents.mapNotNull { it.toProductEntity() }
        val localById = productDao.getAll().associateBy { it.id }
        val changed = incoming.filter { localById[it.id] != it }
        if (changed.isNotEmpty()) productDao.upsertAll(changed)
        return changed.size
    }

    private suspend fun pullInvoices(ownerUid: String, isManager: Boolean, serverOnly: Boolean): Int {
        val query: Query = if (isManager) {
            firestore.collection("invoices")
        } else {
            firestore.collection("invoices").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        var written = 0
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
                written++
            }
        }
        return written
    }

    private suspend fun pullReceipts(ownerUid: String, isManager: Boolean, serverOnly: Boolean): Int {
        val query: Query = if (isManager) {
            firestore.collection("receipts")
        } else {
            firestore.collection("receipts").whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        val incoming = snapshot.documents.mapNotNull { it.toReceiptEntity() }
        var written = 0
        for (remote in incoming) {
            if (receiptDao.getById(remote.id) == null) {
                receiptDao.upsert(remote)
                written++
            }
        }
        return written
    }
}
