package com.cady.cadysalesapp.data.sync

import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** What a rep has actually got into the cloud today. */
data class RepDayActivity(val invoices: Int, val receipts: Int)

enum class LiveDocKind { SALE, RETURN, RECEIPT }

/** One line of the manager's live feed — enough to show a row, never the full document. */
data class LiveDoc(
    val id: String,
    val kind: LiveDocKind,
    val docNumber: String,
    val customerName: String,
    val repId: String,
    val repName: String?,
    val amount: Double,
    val date: Instant,
)

/**
 * The manager's direct line to Firestore, for the two screens that are about what the reps
 * have *really* sent over the internet — as opposed to what is on this device. Both are
 * honest about that: a record a rep created but could not upload yet is not here, and the
 * screens say so.
 */
@Singleton
class ManagerCloudService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Today's invoices and receipts per rep id, read from the cloud (not from this device). */
    suspend fun todayActivityByRep(): Map<String, RepDayActivity> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val since = Timestamp(start.epochSecond, start.nano)
        val invoices = firestore.collection("invoices").whereGreaterThanOrEqualTo("date", since).get().await()
        val receipts = firestore.collection("receipts").whereGreaterThanOrEqualTo("date", since).get().await()
        val invoiceCounts = invoices.documents.mapNotNull { it.getString("ownerUid") }.groupingBy { it }.eachCount()
        val receiptCounts = receipts.documents.mapNotNull { it.getString("ownerUid") }.groupingBy { it }.eachCount()
        return (invoiceCounts.keys + receiptCounts.keys).associateWith { repId ->
            RepDayActivity(invoices = invoiceCounts[repId] ?: 0, receipts = receiptCounts[repId] ?: 0)
        }
    }

    /**
     * The newest [limit] invoices and receipts of all reps, live: a document appears here the
     * moment Firestore has it, with no export/import in between. Closes with the error if the
     * listener is refused (offline from the start, or no permission) so the screen can say why.
     */
    fun observeLive(limit: Int = 100): Flow<List<LiveDoc>> = callbackFlow {
        var invoiceDocs: List<LiveDoc> = emptyList()
        var receiptDocs: List<LiveDoc> = emptyList()

        fun publish() {
            trySend((invoiceDocs + receiptDocs).sortedByDescending { it.date }.take(limit))
        }

        val invoiceListener = firestore.collection("invoices")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                invoiceDocs = snapshot?.documents.orEmpty().mapNotNull { it.toLiveInvoice() }
                publish()
            }
        val receiptListener = firestore.collection("receipts")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                receiptDocs = snapshot?.documents.orEmpty().mapNotNull { it.toLiveReceipt() }
                publish()
            }

        awaitClose {
            invoiceListener.remove()
            receiptListener.remove()
        }
    }

    private fun DocumentSnapshot.toLiveInvoice(): LiveDoc? {
        val invoice = toInvoiceEntity() ?: return null
        val items = toInvoiceItemEntities(id)
        return LiveDoc(
            id = id,
            kind = if (invoice.kind == InvoiceKind.SALE_RETURN) LiveDocKind.RETURN else LiveDocKind.SALE,
            docNumber = invoice.docNumber,
            customerName = invoice.customerName,
            repId = invoice.ownerUid,
            repName = invoice.repName,
            amount = computeInvoiceTotals(invoice, items).grandTotal,
            date = invoice.date,
        )
    }

    private fun DocumentSnapshot.toLiveReceipt(): LiveDoc? {
        val receipt = toReceiptEntity() ?: return null
        return LiveDoc(
            id = id,
            kind = LiveDocKind.RECEIPT,
            docNumber = receipt.docNumber,
            customerName = receipt.customerName,
            repId = receipt.ownerUid,
            repName = receipt.repName,
            amount = receipt.amount,
            date = receipt.date,
        )
    }
}
