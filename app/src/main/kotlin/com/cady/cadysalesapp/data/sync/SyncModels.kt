package com.cady.cadysalesapp.data.sync

import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import java.time.Instant

/** A count per record kind — used for "pending", for totals, and for log entries. */
data class RecordCounts(
    val customers: Int = 0,
    val products: Int = 0,
    val invoices: Int = 0,
    val receipts: Int = 0,
) {
    val total: Int get() = customers + products + invoices + receipts
}

/**
 * Outcome of one "sync now" run over the Firebase channel. Honest by
 * construction: [isSuccess] is only true when nothing failed to upload AND the
 * pull afterwards worked — a half-successful run is never reported as done.
 */
data class SyncRunResult(
    val pushed: Int,
    val failed: Int,
    val pullSucceeded: Boolean,
    val offline: Boolean,
    val errorMessage: String?,
) {
    val isSuccess: Boolean get() = !offline && failed == 0 && pullSucceeded
}

enum class SyncActivityKind { FIREBASE_SYNC, MANUAL_EXPORT, MANUAL_IMPORT }

enum class SyncActivityStatus { SUCCESS, PARTIAL, FAILED }

/** One line of the sync activity log (SyncScreen's recent activity + the outbox/inbox screen). */
data class SyncActivityEntry(
    val id: String,
    val kind: SyncActivityKind,
    val at: Instant,
    val status: SyncActivityStatus,
    val title: String,
    val detail: String?,
    /** Manual exports only: the file kept in the outbox directory, so it can be re-sent. */
    val fileName: String?,
    val counts: RecordCounts,
    /** Manual exports only: true once the manager's receipt-confirmation file was imported. */
    val acknowledged: Boolean = false,
)

data class PendingInvoice(val invoice: InvoiceEntity, val items: List<InvoiceItemEntity>)

/** Everything still waiting to reach the manager, exactly as the export/push would send it. */
data class PendingSnapshot(
    val customers: List<CustomerEntity>,
    val products: List<ProductEntity>,
    val invoices: List<PendingInvoice>,
    val receipts: List<ReceiptEntity>,
) {
    val counts: RecordCounts
        get() = RecordCounts(customers.size, products.size, invoices.size, receipts.size)
    val isEmpty: Boolean get() = counts.total == 0
}
