package com.cady.cadysalesapp.data.sync

import android.util.Log
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.OfferDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * schemaVersion 1 — bump alongside CadyDatabase's Room version if a migration
 * ever changes the shape of what gets synced, so this and a still-installed
 * Flutter copy (or an older native build) can tell they've drifted apart.
 */
const val SYNC_SCHEMA_VERSION = 1

private const val TAG = "CadySync"
private const val COL_CUSTOMERS = "customers"
private const val COL_PRODUCTS = "products"
private const val COL_INVOICES = "invoices"
private const val COL_RECEIPTS = "receipts"
private const val COL_OFFERS = "offers"

/** Deletion tombstones ("شواهد الحذف"): one small document per deleted record. */
private const val COL_DELETIONS = "deletions"
private const val KEY_SEPARATOR = "__"

/** The pull ran, but one or more collections could not be read — [message] names which and why. */
class PullIncompleteException(message: String) : Exception(message)

@Singleton
class SyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val offerDao: OfferDao,
) {
    // A SupervisorJob-scoped launcher for push calls: local writes must never
    // block on network, and one push failing must never cancel sibling pushes
    // — the same "best effort in the background" contract as the Flutter
    // app's unawaited()+catchError() push calls.
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Documents the last pull could not read (wrong shape / missing required fields). */
    private val skipped = AtomicInteger(0)

    @Volatile
    var lastPullSkipped: Int = 0
        private set

    private fun logPushFailure(kind: String, id: String, e: Exception) {
        Log.w(TAG, "Background push failed for $kind/$id: ${e.message}")
    }

    // ---- "Now" variants: suspend, throw on failure, flip the row to SYNCED on success ----
    //
    // A row only flips to SYNCED if it is *still exactly what was pushed* (compare-and-set) —
    // an edit made while the push was in flight keeps its own PENDING flag until its own push
    // is acknowledged. While offline these suspend (Firestore queues the write and sends it on
    // reconnect); the manual "sync now" wraps each call in a timeout.

    suspend fun pushCustomerNow(customer: CustomerEntity) {
        firestore.collection(COL_CUSTOMERS).document(customer.id).set(customer.toFirestoreMap()).await()
        val current = customerDao.getById(customer.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == customer.copy(syncStatus = SyncStatus.PENDING)) {
            customerDao.markSynced(customer.id)
        }
    }

    suspend fun pushProductNow(product: ProductEntity) {
        firestore.collection(COL_PRODUCTS).document(product.id).set(product.toFirestoreMap()).await()
        val current = productDao.getById(product.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == product.copy(syncStatus = SyncStatus.PENDING)) {
            productDao.markSynced(product.id)
        }
    }

    suspend fun pushOfferNow(offer: OfferEntity) {
        firestore.collection(COL_OFFERS).document(offer.id).set(offer.toFirestoreMap()).await()
        val current = offerDao.getById(offer.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == offer.copy(syncStatus = SyncStatus.PENDING)) {
            offerDao.markSynced(offer.id)
        }
    }

    suspend fun pushInvoiceNow(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        firestore.collection(COL_INVOICES).document(invoice.id).set(invoice.toFirestoreMap(items)).await()
        val current = invoiceDao.getById(invoice.id) ?: return
        if (current.copy(syncStatus = SyncStatus.PENDING) == invoice.copy(syncStatus = SyncStatus.PENDING)) {
            invoiceDao.markSynced(invoice.id)
        }
    }

    suspend fun pushReceiptNow(receipt: ReceiptEntity) {
        firestore.collection(COL_RECEIPTS).document(receipt.id).set(receipt.toFirestoreMap()).await()
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

    fun pushOffer(offer: OfferEntity) {
        pushScope.launch {
            try {
                pushOfferNow(offer)
            } catch (e: Exception) {
                logPushFailure("offer", offer.id, e)
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

    // ---- Deletions: remove the cloud copy AND leave a tombstone for the other devices ----
    //
    // Deleting the document alone stops the record coming straight back on THIS device's next
    // pull, but every other device that already pulled it kept its copy forever (a pull never
    // deletes locally just because something is missing — it might merely be a partial or
    // offline read). The tombstone — a tiny `deletions/{collection}__{id}` document — is the
    // positive proof "this record was deleted on purpose": every pull reads them and removes
    // the matching local rows, and also clears out any copy that a device that was offline at
    // the time uploads afterwards.
    //
    // Same background contract as the pushes: the local delete never waits for the network. If
    // the cloud rules do not allow the `deletions` collection yet, only the tombstone fails
    // (logged) — the document delete still goes through exactly as before.

    fun deleteInvoiceRemote(id: String, ownerUid: String? = null) = deleteRemote(COL_INVOICES, id, ownerUid)

    fun deleteReceiptRemote(id: String, ownerUid: String? = null) = deleteRemote(COL_RECEIPTS, id, ownerUid)

    fun deleteCustomerRemote(id: String, ownerUid: String? = null) = deleteRemote(COL_CUSTOMERS, id, ownerUid)

    fun deleteProductRemote(id: String) = deleteRemote(COL_PRODUCTS, id, null)

    fun deleteOfferRemote(id: String) = deleteRemote(COL_OFFERS, id, null)

    private fun tombstoneKey(collection: String, id: String) = "$collection$KEY_SEPARATOR$id"

    private fun deleteRemote(collection: String, id: String, ownerUid: String?) {
        pushScope.launch {
            val now = Instant.now()
            // Both requests are issued before either is awaited, so they stay in order in
            // Firestore's offline queue even if the app is closed before the network returns.
            val tombstone = firestore.collection(COL_DELETIONS).document(tombstoneKey(collection, id)).set(
                mapOf(
                    "collection" to collection,
                    "docId" to id,
                    "ownerUid" to ownerUid,
                    "deletedAt" to Timestamp(now.epochSecond, now.nano),
                )
            )
            val removal = firestore.collection(collection).document(id).delete()
            try {
                tombstone.await()
            } catch (e: Exception) {
                logPushFailure("tombstone $collection", id, e)
            }
            try {
                removal.await()
            } catch (e: Exception) {
                logPushFailure("delete $collection", id, e)
            }
        }
    }

    /** A cloud copy that a tombstone says should not exist (uploaded late by an offline device). */
    private fun deleteZombie(collection: String, id: String) {
        pushScope.launch {
            try {
                firestore.collection(collection).document(id).delete().await()
            } catch (e: Exception) {
                logPushFailure("zombie $collection", id, e)
            }
        }
    }

    // ---- Pull ----

    /**
     * Full pull for the given owner: last-write-wins by comparing updatedAt
     * against what's already local, and — critically — never deletes a local
     * row just because it's momentarily missing from a partial/offline pull (only an explicit
     * tombstone deletes). Products and offers are the shared catalog and pull for every user
     * regardless of who authored them.
     *
     * Each collection is pulled on its own: one that fails (permissions, a bad network moment)
     * no longer stops the others from being saved. If anything failed, a
     * [PullIncompleteException] naming the failed collections is thrown AFTER everything that
     * could be read has been stored, so the caller reports an honest "not fully synced".
     *
     * [serverOnly] = true forces every read to hit the server (Source.SERVER) and throw if
     * it can't — used by the manual "sync now", so a pull answered from Firestore's local
     * cache is never reported as a successful sync. Login keeps the default (cache fallback).
     *
     * Returns how many local rows were actually written (new or changed), per kind (offers are
     * counted with the products — both are the manager's catalog).
     */
    suspend fun pullFromFirestore(ownerUid: String, isManager: Boolean, serverOnly: Boolean = false): RecordCounts {
        skipped.set(0)
        val failures = ArrayList<String>()

        // Tombstones first, so nothing deleted elsewhere is re-inserted below. Never fatal: if the
        // cloud rules don't know the collection yet, the rest of the sync must still work.
        val tombstones: Set<String> = try {
            fetchTombstoneKeys(serverOnly)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read deletion tombstones: ${e.message}")
            emptySet()
        }
        try {
            applyDeletions(tombstones)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply deletion tombstones: ${e.message}")
        }

        val customers = guarded("العملاء", failures) { pullCustomers(ownerUid, isManager, serverOnly, tombstones) }
        val products = guarded("المنتجات", failures) { pullProducts(serverOnly, tombstones) }
        val invoices = guarded("الفواتير", failures) { pullInvoices(ownerUid, isManager, serverOnly, tombstones) }
        val receipts = guarded("السندات", failures) { pullReceipts(ownerUid, isManager, serverOnly, tombstones) }
        // Offers are a newer collection that the cloud rules may not cover yet — a refusal here
        // is logged, never reported as a failed sync.
        val offers = try {
            pullOffers(serverOnly, tombstones)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not pull offers: ${e.message}")
            0
        }

        lastPullSkipped = skipped.get()
        if (failures.isNotEmpty()) {
            throw PullIncompleteException("تعذّر سحب " + failures.joinToString("، "))
        }
        return RecordCounts(customers, products + offers, invoices, receipts)
    }

    private suspend fun guarded(label: String, failures: MutableList<String>, block: suspend () -> Int): Int =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pull of $label failed: ${e.message}")
            failures += "$label (${describe(e)})"
            0
        }

    private fun describe(e: Exception): String {
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "رفض الخادم القراءة — صلاحيات"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "الخادم غير متاح"
                else -> e.code.name
            }
        }
        return e.message ?: e::class.java.simpleName
    }

    private suspend fun Query.fetch(serverOnly: Boolean): QuerySnapshot =
        (if (serverOnly) get(Source.SERVER) else get()).await()

    private suspend fun fetchTombstoneKeys(serverOnly: Boolean): Set<String> =
        firestore.collection(COL_DELETIONS).fetch(serverOnly).documents.map { it.id }.toSet()

    private fun isTombstoned(collection: String, id: String, tombstones: Set<String>): Boolean =
        tombstones.contains(tombstoneKey(collection, id))

    /** Removes the local copy of everything a tombstone says was deleted somewhere else. */
    private suspend fun applyDeletions(keys: Set<String>) {
        for (key in keys) {
            val sep = key.indexOf(KEY_SEPARATOR)
            if (sep <= 0) continue
            val collection = key.substring(0, sep)
            val id = key.substring(sep + KEY_SEPARATOR.length)
            when (collection) {
                COL_INVOICES -> if (invoiceDao.getById(id) != null) invoiceDao.deleteById(id)
                COL_RECEIPTS -> if (receiptDao.getById(id) != null) receiptDao.deleteById(id)
                COL_PRODUCTS -> if (productDao.getById(id) != null) productDao.deleteById(id)
                COL_OFFERS -> if (offerDao.getById(id) != null) offerDao.deleteById(id)
                COL_CUSTOMERS -> {
                    // Never orphan documents: a customer that still has invoices or receipts
                    // on this device is kept (the deletion was made without seeing them).
                    val hasDocuments = invoiceDao.getForCustomerOnce(id).isNotEmpty() ||
                        receiptDao.getForCustomerOnce(id).isNotEmpty()
                    if (!hasDocuments && customerDao.getById(id) != null) customerDao.deleteById(id)
                }
            }
        }
    }

    private suspend fun pullCustomers(
        ownerUid: String,
        isManager: Boolean,
        serverOnly: Boolean,
        tombstones: Set<String>,
    ): Int {
        val query: Query = if (isManager) {
            firestore.collection(COL_CUSTOMERS)
        } else {
            firestore.collection(COL_CUSTOMERS).whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        var written = 0
        for (doc in snapshot.documents) {
            if (isTombstoned(COL_CUSTOMERS, doc.id, tombstones)) {
                deleteZombie(COL_CUSTOMERS, doc.id)
                continue
            }
            val remote = try { doc.toCustomerEntity() } catch (e: Exception) { null }
            if (remote == null) {
                skipped.incrementAndGet()
                continue
            }
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

    private suspend fun pullProducts(serverOnly: Boolean, tombstones: Set<String>): Int {
        val snapshot = firestore.collection(COL_PRODUCTS).fetch(serverOnly)
        val localById = productDao.getAll().associateBy { it.id }
        val changed = ArrayList<ProductEntity>()
        for (doc in snapshot.documents) {
            if (isTombstoned(COL_PRODUCTS, doc.id, tombstones)) {
                deleteZombie(COL_PRODUCTS, doc.id)
                continue
            }
            val remote = try { doc.toProductEntity() } catch (e: Exception) { null }
            if (remote == null) {
                skipped.incrementAndGet()
                continue
            }
            val mine = localById[remote.id]
            // A catalog edit that has not been uploaded yet must not be overwritten by the older cloud copy.
            if (mine != null && mine.syncStatus == SyncStatus.PENDING) continue
            if (mine != remote) changed += remote
        }
        if (changed.isNotEmpty()) productDao.upsertAll(changed)
        return changed.size
    }

    private suspend fun pullOffers(serverOnly: Boolean, tombstones: Set<String>): Int {
        val snapshot = firestore.collection(COL_OFFERS).fetch(serverOnly)
        val localById = offerDao.getAll().associateBy { it.id }
        val changed = ArrayList<OfferEntity>()
        for (doc in snapshot.documents) {
            if (isTombstoned(COL_OFFERS, doc.id, tombstones)) {
                deleteZombie(COL_OFFERS, doc.id)
                continue
            }
            val remote = try { doc.toOfferEntity() } catch (e: Exception) { null }
            if (remote == null) {
                skipped.incrementAndGet()
                continue
            }
            val mine = localById[remote.id]
            if (mine != null && mine.syncStatus == SyncStatus.PENDING) continue
            if (mine != remote) changed += remote
        }
        if (changed.isNotEmpty()) offerDao.upsertAll(changed)
        return changed.size
    }

    private suspend fun pullInvoices(
        ownerUid: String,
        isManager: Boolean,
        serverOnly: Boolean,
        tombstones: Set<String>,
    ): Int {
        val query: Query = if (isManager) {
            firestore.collection(COL_INVOICES)
        } else {
            firestore.collection(COL_INVOICES).whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        var written = 0
        for (doc in snapshot.documents) {
            if (isTombstoned(COL_INVOICES, doc.id, tombstones)) {
                deleteZombie(COL_INVOICES, doc.id)
                continue
            }
            val remote = try { doc.toInvoiceEntity() } catch (e: Exception) { null }
            if (remote == null) {
                skipped.incrementAndGet()
                continue
            }
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

    private suspend fun pullReceipts(
        ownerUid: String,
        isManager: Boolean,
        serverOnly: Boolean,
        tombstones: Set<String>,
    ): Int {
        val query: Query = if (isManager) {
            firestore.collection(COL_RECEIPTS)
        } else {
            firestore.collection(COL_RECEIPTS).whereEqualTo("ownerUid", ownerUid)
        }
        val snapshot = query.fetch(serverOnly)
        var written = 0
        for (doc in snapshot.documents) {
            if (isTombstoned(COL_RECEIPTS, doc.id, tombstones)) {
                deleteZombie(COL_RECEIPTS, doc.id)
                continue
            }
            val remote = try { doc.toReceiptEntity() } catch (e: Exception) { null }
            if (remote == null) {
                skipped.incrementAndGet()
                continue
            }
            if (receiptDao.getById(remote.id) == null) {
                receiptDao.upsert(remote)
                written++
            }
        }
        return written
    }

    // ---- Single-document pulls (the manager's live feed opens a document straight from the cloud) ----

    /** Brings one invoice (with its lines) from the cloud if this device doesn't have it. False if it isn't readable. */
    suspend fun pullInvoiceById(id: String): Boolean {
        val doc = firestore.collection(COL_INVOICES).document(id).get().await()
        val remote = doc.toInvoiceEntity() ?: return false
        if (invoiceDao.getById(id) == null) {
            invoiceDao.upsert(remote)
            invoiceItemDao.upsertAll(doc.toInvoiceItemEntities(id))
        }
        return true
    }

    suspend fun pullReceiptById(id: String): Boolean {
        val doc = firestore.collection(COL_RECEIPTS).document(id).get().await()
        val remote = doc.toReceiptEntity() ?: return false
        if (receiptDao.getById(id) == null) receiptDao.upsert(remote)
        return true
    }
}
