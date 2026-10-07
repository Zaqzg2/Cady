package com.cady.cadysalesapp.data.sync

import android.net.Uri
import androidx.room.withTransaction
import com.cady.cadysalesapp.data.files.safeFileToken
import com.cady.cadysalesapp.data.local.CadyDatabase
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.dao.UserAccountDao
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Who a rep's export file says it came from. */
data class RepSender(
    val uid: String,
    val username: String?,
    val displayName: String,
    val deviceName: String?,
    val repNumber: Int?,
)

data class PreviewInvoiceRow(val invoice: InvoiceEntity, val total: Double, val isNew: Boolean)

data class PreviewReceiptRow(val receipt: ReceiptEntity, val isNew: Boolean)

/**
 * Everything the manager sees before approving a rep's file: what is in it, and — checked
 * against this device's own data — what is new, what is already here, and what is off.
 * Holds the parsed records too, so approving does not read the file a second time.
 */
data class RepExportPreview(
    val fileId: String,
    val createdAt: Instant,
    val sender: RepSender,
    /** False when no account with this id is on the device — usually "refresh the rep list first". */
    val senderKnown: Boolean,
    val customers: List<CustomerEntity>,
    val invoices: List<PendingInvoice>,
    val receipts: List<ReceiptEntity>,
    val newCustomers: Int,
    val updatedCustomers: Int,
    val duplicateCustomers: Int,
    val newInvoices: Int,
    val duplicateInvoices: Int,
    val newReceipts: Int,
    val duplicateReceipts: Int,
    /** Same id already here but with different content — kept as this device has it, never overwritten. */
    val differing: Int,
    /** The catalog is the manager's to write, so a rep's product records are not taken. */
    val ignoredProducts: Int,
    /** Records whose owner is not the sender — a tampered or mixed-up file; never imported. */
    val notOwned: Int,
    /** Records that could not be read at all. */
    val invalid: Int,
    val invoiceRows: List<PreviewInvoiceRow>,
    val receiptRows: List<PreviewReceiptRow>,
) {
    val hasAnythingNew: Boolean get() = newCustomers + updatedCustomers + newInvoices + newReceipts > 0
    val totalRecords: Int get() = customers.size + invoices.size + receipts.size
}

data class RepImportResult(
    val applied: RecordCounts,
    val alreadyPresent: RecordCounts,
    /** The receipt-confirmation file to send back to the rep. */
    val ackFile: File,
    val ackEntry: SyncActivityEntry,
)

/**
 * The manager's half of the manual (no-internet) file channel — the exact same envelope
 * the rep side writes and reads (see SyncFileService and the README):
 *  - [previewRepExport] / [applyRepExport]: take in a `rep_export`, and answer with a
 *    `receipt_ack` listing exactly what is now safe here, which is the only thing that lets
 *    the rep's records stop being "pending".
 *  - [exportUpdate]: build a `manager_update` (customers / products / company settings) for
 *    one rep or for all of them.
 *
 * Nothing is imported without the preview step being confirmed by the person, and nothing
 * here touches the network.
 */
@Singleton
class ManagerSyncService @Inject constructor(
    private val database: CadyDatabase,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val userAccountDao: UserAccountDao,
    private val companySettingsRepository: CompanySettingsRepository,
    private val syncFileService: SyncFileService,
    private val activityStore: SyncActivityStore,
) {
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneId.systemDefault())

    private inline fun <T> readRecords(array: JSONArray?, parse: (JSONObject) -> T): Pair<List<T>, Int> {
        if (array == null) return Pair(emptyList(), 0)
        val out = ArrayList<T>(array.length())
        var invalid = 0
        for (i in 0 until array.length()) {
            try {
                out.add(parse(array.getJSONObject(i)))
            } catch (e: Exception) {
                invalid++
            }
        }
        return Pair(out, invalid)
    }

    // ------------------------------------------------------------------ import: preview

    /** Reads and checks a rep's file and describes it against this device's data. Changes nothing. */
    suspend fun previewRepExport(uri: Uri): RepExportPreview = withContext(Dispatchers.IO) {
        val root = try {
            syncFileService.readRoot(uri)
        } catch (e: SyncFileException) {
            throw e
        } catch (e: JSONException) {
            throw SyncFileException("الملف تالف أو ليس ملف مزامنة من كادي")
        } catch (e: IOException) {
            throw SyncFileException("تعذّرت قراءة الملف")
        }

        when (SyncFileKind.fromWire(root.stringOrNull("kind"))) {
            SyncFileKind.REP_EXPORT -> Unit
            SyncFileKind.MANAGER_UPDATE ->
                throw SyncFileException("هذا ملف تحديث من المدير — يُستورد على جهاز المندوب وليس هنا")
            SyncFileKind.RECEIPT_ACK ->
                throw SyncFileException("هذا ملف تأكيد استلام — يُستورد على جهاز المندوب وليس هنا")
            null -> throw SyncFileException("نوع الملف غير معروف")
        }

        val from = root.optJSONObject("from") ?: throw SyncFileException("الملف لا يحدد المندوب المُرسِل")
        val senderUid = from.stringOrNull("uid") ?: throw SyncFileException("الملف لا يحدد المندوب المُرسِل")
        val sender = RepSender(
            uid = senderUid,
            username = from.stringOrNull("username"),
            displayName = from.stringOrNull("displayName") ?: from.stringOrNull("username") ?: "مندوب",
            deviceName = from.stringOrNull("deviceName"),
            repNumber = if (from.isNull("repNumber")) null else from.optInt("repNumber"),
        )

        val (customersAll, badCustomers) = readRecords(root.optJSONArray("customers")) { it.toCustomerEntity() }
        val (invoicesAll, badInvoices) = readRecords(root.optJSONArray("invoices")) { obj ->
            val invoice = obj.toInvoiceEntity(NoPaths)
            PendingInvoice(invoice, obj.toInvoiceItems(invoice.id))
        }
        val (receiptsAll, badReceipts) = readRecords(root.optJSONArray("receipts")) { it.toReceiptEntity(NoPaths) }
        val ignoredProducts = root.optJSONArray("products")?.length() ?: 0

        // A rep's file may only carry that rep's own records.
        val customers = customersAll.filter { it.ownerUid == sender.uid }
        val invoices = invoicesAll.filter { it.invoice.ownerUid == sender.uid }
        val receipts = receiptsAll.filter { it.ownerUid == sender.uid }
        val notOwned = (customersAll.size - customers.size) +
            (invoicesAll.size - invoices.size) +
            (receiptsAll.size - receipts.size)

        var newCustomers = 0
        var updatedCustomers = 0
        var duplicateCustomers = 0
        for (remote in customers) {
            val local = customerDao.getById(remote.id)
            if (local == null) {
                newCustomers++
            } else if (remote.updatedAt.isAfter(local.updatedAt)) {
                updatedCustomers++
            } else {
                duplicateCustomers++
            }
        }

        var newInvoices = 0
        var duplicateInvoices = 0
        var differing = 0
        val invoiceRows = ArrayList<PreviewInvoiceRow>(invoices.size)
        for (pending in invoices) {
            val local = invoiceDao.getById(pending.invoice.id)
            if (local == null) {
                newInvoices++
            } else {
                duplicateInvoices++
                if (invoiceDiffers(pending, local)) differing++
            }
            invoiceRows.add(
                PreviewInvoiceRow(
                    invoice = pending.invoice,
                    total = computeInvoiceTotals(pending.invoice, pending.items).grandTotal,
                    isNew = local == null,
                )
            )
        }

        var newReceipts = 0
        var duplicateReceipts = 0
        val receiptRows = ArrayList<PreviewReceiptRow>(receipts.size)
        for (remote in receipts) {
            val local = receiptDao.getById(remote.id)
            if (local == null) {
                newReceipts++
            } else {
                duplicateReceipts++
                if (receiptDiffers(remote, local)) differing++
            }
            receiptRows.add(PreviewReceiptRow(remote, isNew = local == null))
        }

        RepExportPreview(
            fileId = root.stringOrNull("fileId") ?: UUID.randomUUID().toString(),
            createdAt = Instant.ofEpochMilli(root.optLong("createdAt", System.currentTimeMillis())),
            sender = sender,
            senderKnown = userAccountDao.getById(sender.uid) != null,
            customers = customers,
            invoices = invoices,
            receipts = receipts,
            newCustomers = newCustomers,
            updatedCustomers = updatedCustomers,
            duplicateCustomers = duplicateCustomers,
            newInvoices = newInvoices,
            duplicateInvoices = duplicateInvoices,
            newReceipts = newReceipts,
            duplicateReceipts = duplicateReceipts,
            differing = differing,
            ignoredProducts = ignoredProducts,
            notOwned = notOwned,
            invalid = badCustomers + badInvoices + badReceipts,
            invoiceRows = invoiceRows,
            receiptRows = receiptRows,
        )
    }

    /** Business fields only — picture paths and printed/shared flags legitimately differ per device. */
    private suspend fun invoiceDiffers(remote: PendingInvoice, local: InvoiceEntity): Boolean {
        val r = remote.invoice
        if (r.docNumber != local.docNumber || r.kind != local.kind || r.customerId != local.customerId ||
            r.paymentMode != local.paymentMode || r.date.toEpochMilli() != local.date.toEpochMilli()
        ) return true
        val remoteTotal = computeInvoiceTotals(r, remote.items).grandTotal
        val localTotal = computeInvoiceTotals(local, invoiceItemDao.getForInvoice(local.id)).grandTotal
        return abs(remoteTotal - localTotal) > 0.005
    }

    private fun receiptDiffers(remote: ReceiptEntity, local: ReceiptEntity): Boolean =
        remote.docNumber != local.docNumber || remote.customerId != local.customerId ||
            remote.method != local.method || abs(remote.amount - local.amount) > 0.005 ||
            remote.date.toEpochMilli() != local.date.toEpochMilli()

    // ------------------------------------------------------------------ import: approve

    /**
     * Writes what the preview described and answers with the receipt-ack file.
     *  - customers: newest copy wins (same rule as everywhere else);
     *  - invoices / receipts: added when new; one that already exists is never overwritten.
     * Everything valid and owned by the sender goes into the ack — including what was already
     * here — because all of it is now safe on this side, which is what the rep's "pending"
     * flag actually asks.
     */
    suspend fun applyRepExport(preview: RepExportPreview): RepImportResult = withContext(Dispatchers.IO) {
        var appliedCustomers = 0
        var appliedInvoices = 0
        var appliedReceipts = 0

        database.withTransaction {
            for (remote in preview.customers) {
                val local = customerDao.getById(remote.id)
                if (local == null || remote.updatedAt.isAfter(local.updatedAt)) {
                    customerDao.upsert(remote.copy(syncStatus = SyncStatus.SYNCED))
                    appliedCustomers++
                }
            }
            for (pending in preview.invoices) {
                if (invoiceDao.getById(pending.invoice.id) == null) {
                    invoiceDao.upsert(pending.invoice.copy(syncStatus = SyncStatus.SYNCED))
                    invoiceItemDao.upsertAll(pending.items)
                    appliedInvoices++
                }
            }
            for (remote in preview.receipts) {
                if (receiptDao.getById(remote.id) == null) {
                    receiptDao.upsert(remote.copy(syncStatus = SyncStatus.SYNCED))
                    appliedReceipts++
                }
            }
        }

        val now = Instant.now()
        val applied = RecordCounts(customers = appliedCustomers, invoices = appliedInvoices, receipts = appliedReceipts)
        val alreadyPresent = RecordCounts(
            customers = preview.customers.size - appliedCustomers,
            invoices = preview.invoices.size - appliedInvoices,
            receipts = preview.receipts.size - appliedReceipts,
        )

        activityStore.record(
            SyncActivityEntry(
                id = UUID.randomUUID().toString(),
                kind = SyncActivityKind.MANUAL_IMPORT,
                at = now,
                status = if (preview.invalid > 0 || preview.notOwned > 0) SyncActivityStatus.PARTIAL else SyncActivityStatus.SUCCESS,
                title = "استيراد ملف من ${preview.sender.displayName}",
                detail = buildString {
                    if (alreadyPresent.total > 0) append("موجود مسبقًا ${alreadyPresent.total}")
                    if (preview.invalid > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("تعذّرت قراءة ${preview.invalid}")
                    }
                    if (preview.notOwned > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("تُجوهل ${preview.notOwned} لا تخص المندوب")
                    }
                }.ifEmpty { null },
                fileName = null,
                counts = applied,
            )
        )

        val ack = writeReceiptAck(preview, now)
        RepImportResult(applied, alreadyPresent, ack.file, ack.entry)
    }

    private suspend fun writeReceiptAck(preview: RepExportPreview, now: Instant): SyncExportResult {
        val ackId = UUID.randomUUID().toString()
        val root = JSONObject()
        root.put("format", SYNC_FILE_FORMAT)
        root.put("schemaVersion", SYNC_SCHEMA_VERSION)
        root.put("kind", SyncFileKind.RECEIPT_ACK.wire)
        root.put("fileId", ackId)
        root.put("createdAt", now.toEpochMilli())
        root.put("target", JSONObject().put("uid", preview.sender.uid))
        root.put("ackOf", preview.fileId)
        val accepted = JSONObject()
        accepted.put("customers", JSONArray(preview.customers.map { it.id }))
        accepted.put("invoices", JSONArray(preview.invoices.map { it.invoice.id }))
        accepted.put("receipts", JSONArray(preview.receipts.map { it.id }))
        root.put("accepted", accepted)

        val dir = activityStore.outboxDir
        dir.mkdirs()
        val who = safeFileToken(preview.sender.username ?: preview.sender.displayName, "rep")
        val name = "cady_ack_${who}_${fileStamp.format(now)}.json"
        val file = File(dir, name)
        file.writeText(root.toString(), Charsets.UTF_8)

        val entry = SyncActivityEntry(
            id = ackId,
            kind = SyncActivityKind.MANUAL_EXPORT,
            at = now,
            status = SyncActivityStatus.SUCCESS,
            title = "تأكيد استلام لـ${preview.sender.displayName}",
            detail = "يُرسَل للمندوب ليُغلق ما أرسله",
            fileName = name,
            counts = RecordCounts(
                customers = preview.customers.size,
                invoices = preview.invoices.size,
                receipts = preview.receipts.size,
            ),
        )
        activityStore.record(entry)
        return SyncExportResult(file, entry)
    }

    // ------------------------------------------------------------------ export: manager_update

    /**
     * Builds a `manager_update` for [target] (one rep) or, when [target] is null, for every rep
     * in one file (each rep's device keeps only what is his — the rep side already filters).
     * Null when there is nothing to put in it.
     */
    suspend fun exportUpdate(
        manager: UserAccountEntity,
        target: UserAccountEntity?,
        includeCustomers: Boolean,
        includeProducts: Boolean,
        includeSettings: Boolean,
    ): SyncExportResult? = withContext(Dispatchers.IO) {
        val customers = when {
            !includeCustomers -> emptyList()
            target != null -> customerDao.getAllByOwner(target.id)
            else -> customerDao.getAll().filter { it.ownerUid != manager.id }
        }
        val products = if (includeProducts) productDao.getAll() else emptyList()
        val settings = if (includeSettings) companySettingsRepository.settings.first() else null
        if (customers.isEmpty() && products.isEmpty() && settings == null) return@withContext null

        val now = Instant.now()
        val fileId = UUID.randomUUID().toString()
        val root = JSONObject()
        root.put("format", SYNC_FILE_FORMAT)
        root.put("schemaVersion", SYNC_SCHEMA_VERSION)
        root.put("kind", SyncFileKind.MANAGER_UPDATE.wire)
        root.put("fileId", fileId)
        root.put("createdAt", now.toEpochMilli())
        val from = JSONObject()
        from.put("uid", manager.id)
        from.put("username", manager.username)
        from.put("displayName", manager.displayName)
        root.put("from", from)
        root.put("target", JSONObject().put("uid", target?.id ?: JSONObject.NULL))

        val customersJson = JSONArray()
        customers.forEach { customersJson.put(it.toJson()) }
        root.put("customers", customersJson)
        val productsJson = JSONArray()
        products.forEach { productsJson.put(it.toJson(NoPaths)) }
        root.put("products", productsJson)
        if (settings != null) {
            val s = JSONObject()
            s.put("companyName", settings.companyName)
            s.put("companyAddress", settings.companyAddress)
            s.put("companyPhone", settings.companyPhone)
            s.put("invoiceFooterText", settings.invoiceFooterText)
            root.put("settings", s)
        }

        val dir = activityStore.outboxDir
        dir.mkdirs()
        val who = if (target != null) safeFileToken(target.username, "rep") else "all"
        val name = "cady_update_${who}_${fileStamp.format(now)}.json"
        val file = File(dir, name)
        file.writeText(root.toString(), Charsets.UTF_8)

        val entry = SyncActivityEntry(
            id = fileId,
            kind = SyncActivityKind.MANUAL_EXPORT,
            at = now,
            status = SyncActivityStatus.SUCCESS,
            title = "إنشاء تحديث",
            detail = "إلى ${target?.displayName ?: "كل المندوبين"}" + if (settings != null) " · مع بيانات الشركة" else "",
            fileName = name,
            counts = RecordCounts(customers = customers.size, products = products.size),
        )
        activityStore.record(entry)
        SyncExportResult(file, entry)
    }
}
