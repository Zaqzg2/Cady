package com.cady.cadysalesapp.data.sync

import android.util.Log
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.dao.UserAccountDao
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rep-side orchestration of the Firebase channel: "what is still waiting?", "sync now",
 * and the activity log entry each run leaves behind.
 *
 * Honesty rules (the whole point of the sync screen):
 *  - a row is only SYNCED after Firestore acknowledged it (see SyncRepository.push*Now);
 *  - a pull only counts when it was answered by the server, not Firestore's local cache;
 *  - a run is only reported as a success when nothing failed to upload AND the pull worked.
 *
 * A run executes in this service's own scope, so leaving the sync screen mid-run does not
 * cancel it half-way; [isSyncing] / [progress] are shared so any screen can show them.
 */
@Singleton
class SyncService @Inject constructor(
    private val syncRepository: SyncRepository,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val userAccountDao: UserAccountDao,
    private val firestore: FirebaseFirestore,
    private val activityStore: SyncActivityStore,
    private val connectivity: ConnectivityObserver,
) {
    data class Progress(val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runLock = Mutex()
    private val syncing = MutableStateFlow(false)
    private val progressState = MutableStateFlow<Progress?>(null)

    val isSyncing: StateFlow<Boolean> = syncing.asStateFlow()
    val progress: StateFlow<Progress?> = progressState.asStateFlow()
    val isOnline: Flow<Boolean> get() = connectivity.isOnline
    val activity: Flow<List<SyncActivityEntry>> get() = activityStore.entries

    /**
     * Products are the shared catalog — only a manager writes them — so a rep's pending
     * count never includes products (they are pulled, never pushed, from a rep device).
     */
    fun observePending(user: UserAccountEntity): Flow<RecordCounts> {
        val products: Flow<Int> =
            if (user.role == UserRole.MANAGER) productDao.observePendingCount() else flowOf(0)
        return combine(
            customerDao.observePendingCount(user.id),
            products,
            invoiceDao.observePendingCount(user.id),
            receiptDao.observePendingCount(user.id),
        ) { customers, prods, invoices, receipts -> RecordCounts(customers, prods, invoices, receipts) }
    }

    suspend fun pendingSnapshot(user: UserAccountEntity): PendingSnapshot {
        val customers = customerDao.getByStatus(user.id, SyncStatus.PENDING)
            .sortedBy { it.name.lowercase() }
        val products = if (user.role == UserRole.MANAGER) {
            productDao.getPending().sortedBy { it.name.lowercase() }
        } else {
            emptyList()
        }
        val invoices = invoiceDao.getByStatus(user.id, SyncStatus.PENDING)
            .sortedByDescending { it.date }
            .map { PendingInvoice(it, invoiceItemDao.getForInvoice(it.id)) }
        val receipts = receiptDao.getByStatus(user.id, SyncStatus.PENDING)
            .sortedByDescending { it.date }
        return PendingSnapshot(customers, products, invoices, receipts)
    }

    /** Returns null when a run is already in progress (the caller should just keep showing it). */
    suspend fun syncNow(user: UserAccountEntity): SyncRunResult? {
        if (!runLock.tryLock()) return null
        val run: Deferred<SyncRunResult> = scope.async {
            try {
                runSync(user)
            } finally {
                runLock.unlock()
            }
        }
        return run.await()
    }

    private suspend fun runSync(user: UserAccountEntity): SyncRunResult {
        syncing.value = true
        progressState.value = null
        try {
            if (!connectivity.isOnlineNow()) {
                val result = SyncRunResult(
                    pushed = 0, failed = 0, pullSucceeded = false, offline = true,
                    errorMessage = "لا يوجد اتصال بالإنترنت",
                )
                log(result, RecordCounts(), RecordCounts())
                return result
            }

            val snapshot = pendingSnapshot(user)
            val total = snapshot.counts.total
            progressState.value = Progress(0, total)

            val done = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val timeouts = AtomicInteger(0)
            val aborted = AtomicBoolean(false)
            val firstError = AtomicReference<String?>(null)
            val pushedCustomers = AtomicInteger(0)
            val pushedProducts = AtomicInteger(0)
            val pushedInvoices = AtomicInteger(0)
            val pushedReceipts = AtomicInteger(0)
            val gate = Semaphore(PUSH_PARALLELISM)

            suspend fun attempt(counter: AtomicInteger, block: suspend () -> Unit) {
                gate.withPermit {
                    if (aborted.get()) {
                        // The network looks dead (repeated timeouts): don't spend 20 s on every
                        // remaining document — leave them PENDING and say so.
                        failed.incrementAndGet()
                    } else {
                        try {
                            withTimeout(PUSH_TIMEOUT_MS) { block() }
                            counter.incrementAndGet()
                        } catch (e: TimeoutCancellationException) {
                            if (timeouts.incrementAndGet() >= MAX_TIMEOUTS_BEFORE_ABORT) aborted.set(true)
                            failed.incrementAndGet()
                            firstError.compareAndSet(null, "انتهت مهلة الاتصال بالخادم")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed.incrementAndGet()
                            firstError.compareAndSet(null, friendlyMessage(e))
                        }
                    }
                    progressState.value = Progress(done.incrementAndGet(), total)
                }
            }

            coroutineScope {
                val jobs = ArrayList<Deferred<Unit>>()
                for (c in snapshot.customers) jobs += async { attempt(pushedCustomers) { syncRepository.pushCustomerNow(c) } }
                for (p in snapshot.products) jobs += async { attempt(pushedProducts) { syncRepository.pushProductNow(p) } }
                for (i in snapshot.invoices) jobs += async { attempt(pushedInvoices) { syncRepository.pushInvoiceNow(i.invoice, i.items) } }
                for (r in snapshot.receipts) jobs += async { attempt(pushedReceipts) { syncRepository.pushReceiptNow(r) } }
                jobs.awaitAll()
            }

            val pushedCounts = RecordCounts(
                pushedCustomers.get(), pushedProducts.get(), pushedInvoices.get(), pushedReceipts.get(),
            )

            var pulled = RecordCounts()
            var pullOk = false
            var pullError: String? = null
            if (aborted.get()) {
                pullError = "انقطع الاتصال أثناء الرفع"
            } else {
                try {
                    pulled = withTimeout(PULL_TIMEOUT_MS) {
                        syncRepository.pullFromFirestore(
                            ownerUid = user.id,
                            isManager = user.role == UserRole.MANAGER,
                            serverOnly = true,
                        )
                    }
                    pullOk = true
                } catch (e: TimeoutCancellationException) {
                    pullError = "انتهت مهلة سحب التحديثات"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pullError = friendlyMessage(e)
                }
            }

            val result = SyncRunResult(
                pushed = pushedCounts.total,
                failed = failed.get(),
                pullSucceeded = pullOk,
                offline = false,
                errorMessage = firstError.get() ?: pullError,
            )
            if (result.isSuccess) touchLastSync(user)
            log(result, pushedCounts, pulled)
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val result = SyncRunResult(
                pushed = 0, failed = 0, pullSucceeded = false, offline = false,
                errorMessage = friendlyMessage(e),
            )
            log(result, RecordCounts(), RecordCounts())
            return result
        } finally {
            syncing.value = false
            progressState.value = null
        }
    }

    private suspend fun log(result: SyncRunResult, pushed: RecordCounts, pulled: RecordCounts) {
        val status = when {
            result.isSuccess -> SyncActivityStatus.SUCCESS
            !result.offline && (result.pushed > 0 || result.pullSucceeded) -> SyncActivityStatus.PARTIAL
            else -> SyncActivityStatus.FAILED
        }
        val detail = buildString {
            append(if (result.pushed > 0) "رُفع ${result.pushed}" else "لم يُرفع شيء")
            if (result.failed > 0) append(" · تعذّر ${result.failed}")
            if (result.pullSucceeded) append(if (pulled.total > 0) " · سُحب ${pulled.total}" else " · لا تحديثات جديدة")
            if (!result.errorMessage.isNullOrBlank()) append(" — ").append(result.errorMessage)
        }
        try {
            activityStore.record(
                SyncActivityEntry(
                    id = UUID.randomUUID().toString(),
                    kind = SyncActivityKind.FIREBASE_SYNC,
                    at = Instant.now(),
                    status = status,
                    title = "مزامنة عبر الإنترنت",
                    detail = detail,
                    fileName = null,
                    counts = pushed,
                )
            )
        } catch (e: Exception) {
            Log.w("CadySync", "Could not write the sync activity log: ${e.message}")
        }
    }

    /**
     * Stamps "last synced" on the local account and (best effort) on the Firestore user doc,
     * which is what the manager's dashboard will read to show how fresh each rep is.
     */
    private suspend fun touchLastSync(user: UserAccountEntity) {
        val now = Instant.now()
        try {
            userAccountDao.getById(user.id)?.let { userAccountDao.upsert(it.copy(lastSyncAt = now)) }
        } catch (e: Exception) {
            Log.w("CadySync", "Could not stamp lastSyncAt locally: ${e.message}")
        }
        try {
            withTimeoutOrNull(10_000L) {
                firestore.collection("users").document(user.id)
                    .update("lastSyncAt", Timestamp(now.epochSecond, now.nano))
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CadySync", "Could not stamp lastSyncAt in Firestore: ${e.message}")
        }
    }

    private fun friendlyMessage(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "رفض الخادم العملية (صلاحيات) — تواصل مع المدير"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "الخادم غير متاح حاليًا — تحقق من الاتصال"
        e is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت"
        else -> e.message ?: (e::class.java.simpleName ?: "خطأ غير معروف")
    }

    private companion object {
        const val PUSH_PARALLELISM = 6
        const val PUSH_TIMEOUT_MS = 20_000L
        const val PULL_TIMEOUT_MS = 60_000L
        const val MAX_TIMEOUTS_BEFORE_ABORT = 3
    }
}
