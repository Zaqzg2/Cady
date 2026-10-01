package com.cady.cadysalesapp.data.sync

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cady.cadysalesapp.data.files.fileProviderUri
import com.cady.cadysalesapp.data.files.safeFileToken
import com.cady.cadysalesapp.data.local.CadyDatabase
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

const val SYNC_FILE_FORMAT = "cady-sync"

/**
 * The three kinds of manual-exchange file (the no-internet channel). Phase 6 builds the rep
 * side: export [REP_EXPORT], import [MANAGER_UPDATE] and [RECEIPT_ACK]. The manager side of
 * the same three files (import a rep export, build an update, answer with an ack) is Phase 7
 * and must use exactly this envelope — see README "ملفات المزامنة اليدوية".
 */
enum class SyncFileKind(val wire: String) {
    REP_EXPORT("rep_export"),
    MANAGER_UPDATE("manager_update"),
    RECEIPT_ACK("receipt_ack");

    companion object {
        fun fromWire(value: String?): SyncFileKind? = entries.firstOrNull { it.wire == value }
    }
}

class SyncFileException(message: String) : Exception(message)

data class SyncExportResult(val file: File, val entry: SyncActivityEntry)

sealed interface SyncImportOutcome {
    data class Applied(
        val kind: SyncFileKind,
        val applied: RecordCounts,
        /** Valid records deliberately not applied (older than the local copy, or for someone else). */
        val skipped: Int,
        /** Records in the file that could not be read at all. */
        val invalid: Int,
        val settingsApplied: Boolean,
        val acknowledgedExport: Boolean,
    ) : SyncImportOutcome

    data class Rejected(val message: String) : SyncImportOutcome
}

/**
 * Manual sync-file exchange, rep side. Nothing here touches the network.
 *
 * Export never flips anything to SYNCED: a record only stops being "pending" once the manager's
 * receipt-ack file comes back (or Firebase later uploads it). That is what keeps the pending
 * count honest on the manual channel.
 */
@Singleton
class SyncFileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CadyDatabase,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val companySettingsRepository: CompanySettingsRepository,
    private val syncService: SyncService,
    private val activityStore: SyncActivityStore,
) {
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneId.systemDefault())

    // ------------------------------------------------------------------ export

    /** Builds a rep_export file from everything still pending. Null when nothing is pending. */
    suspend fun exportPending(user: UserAccountEntity): SyncExportResult? = withContext(Dispatchers.IO) {
        val snapshot = syncService.pendingSnapshot(user)
        if (snapshot.isEmpty) return@withContext null

        val now = Instant.now()
        val fileId = UUID.randomUUID().toString()

        val root = JSONObject()
        root.put("format", SYNC_FILE_FORMAT)
        root.put("schemaVersion", SYNC_SCHEMA_VERSION)
        root.put("kind", SyncFileKind.REP_EXPORT.wire)
        root.put("fileId", fileId)
        root.put("createdAt", now.toEpochMilli())
        val from = JSONObject()
        from.put("uid", user.id)
        from.put("username", user.username)
        from.put("displayName", user.displayName)
        from.put("deviceName", user.deviceName ?: JSONObject.NULL)
        from.put("repNumber", user.repNumber ?: JSONObject.NULL)
        root.put("from", from)

        val customers = JSONArray()
        snapshot.customers.forEach { customers.put(it.toJson()) }
        root.put("customers", customers)
        val products = JSONArray()
        snapshot.products.forEach { products.put(it.toJson(NoPaths)) }
        root.put("products", products)
        val invoices = JSONArray()
        snapshot.invoices.forEach { invoices.put(it.invoice.toJson(it.items, NoPaths)) }
        root.put("invoices", invoices)
        val receipts = JSONArray()
        snapshot.receipts.forEach { receipts.put(it.toJson(NoPaths)) }
        root.put("receipts", receipts)

        val dir = activityStore.outboxDir
        dir.mkdirs()
        val name = "cady_sync_${safeFileToken(user.username, "rep")}_${fileStamp.format(now)}.json"
        val file = File(dir, name)
        file.writeText(root.toString(), Charsets.UTF_8)

        val entry = SyncActivityEntry(
            id = fileId,
            kind = SyncActivityKind.MANUAL_EXPORT,
            at = now,
            status = SyncActivityStatus.SUCCESS,
            title = "تصدير ملف مزامنة",
            detail = "بانتظار تأكيد استلام المدير",
            fileName = name,
            counts = snapshot.counts,
        )
        activityStore.record(entry)
        SyncExportResult(file, entry)
    }

    fun shareUri(file: File): Uri = context.fileProviderUri(file)

    /** The stored export file behind a log entry, or null if it was deleted/trimmed away. */
    fun outboxFileFor(entry: SyncActivityEntry): File? {
        val name = entry.fileName ?: return null
        val file = File(activityStore.outboxDir, name)
        return if (file.isFile) file else null
    }

    // ------------------------------------------------------------------ import

    suspend fun importFrom(uri: Uri, user: UserAccountEntity): SyncImportOutcome = withContext(Dispatchers.IO) {
        val outcome: SyncImportOutcome = try {
            val root = readRoot(uri)
            when (SyncFileKind.fromWire(root.stringOrNull("kind"))) {
                SyncFileKind.MANAGER_UPDATE -> applyManagerUpdate(root, user)
                SyncFileKind.RECEIPT_ACK -> applyReceiptAck(root, user)
                SyncFileKind.REP_EXPORT ->
                    SyncImportOutcome.Rejected("هذا ملف مُصدَّر من مندوب — يُستقبل على جهاز المدير وليس هنا")
                null -> SyncImportOutcome.Rejected("نوع الملف غير معروف")
            }
        } catch (e: SyncFileException) {
            SyncImportOutcome.Rejected(e.message ?: "ملف غير صالح")
        } catch (e: JSONException) {
            SyncImportOutcome.Rejected("الملف تالف أو ليس ملف مزامنة من كادي")
        } catch (e: IOException) {
            SyncImportOutcome.Rejected("تعذّرت قراءة الملف")
        }
        logImport(outcome)
        outcome
    }

    private fun readRoot(uri: Uri): JSONObject {
        val bytes = readLimited(uri)
        var text = String(bytes, Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) text = text.substring(1)
        val root = JSONObject(text)
        if (root.stringOrNull("format") != SYNC_FILE_FORMAT) {
            throw SyncFileException("هذا ليس ملف مزامنة من كادي")
        }
        val schema = root.optInt("schemaVersion", 0)
        if (schema < 1) throw SyncFileException("إصدار الملف غير معروف")
        if (schema > SYNC_SCHEMA_VERSION) throw SyncFileException("الملف من نسخة أحدث من التطبيق — حدّث التطبيق أولًا")
        return root
    }

    private fun readLimited(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw SyncFileException("تعذّر فتح الملف")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw SyncFileException("الملف كبير جدًا")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

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

    private suspend fun applyManagerUpdate(root: JSONObject, user: UserAccountEntity): SyncImportOutcome {
        val target = root.optJSONObject("target")?.stringOrNull("uid")
        if (target != null && target != user.id) {
            return SyncImportOutcome.Rejected("هذا التحديث موجّه لمندوب آخر")
        }

        val (customers, badCustomers) = readRecords(root.optJSONArray("customers")) { it.toCustomerEntity() }
        val (products, badProducts) = readRecords(root.optJSONArray("products")) { it.toProductEntity(NoPaths) }

        // A rep only ever holds his own customers (same rule the Firestore pull applies).
        val mine = customers.filter { it.ownerUid == user.id }
        var skipped = customers.size - mine.size
        var appliedCustomers = 0

        database.withTransaction {
            for (remote in mine) {
                val local = customerDao.getById(remote.id)
                if (local == null || !remote.updatedAt.isBefore(local.updatedAt)) {
                    // The manager is the source of this copy, so there is nothing to send back.
                    customerDao.upsert(remote.copy(syncStatus = SyncStatus.SYNCED))
                    appliedCustomers++
                } else {
                    skipped++
                }
            }
            if (products.isNotEmpty()) {
                val localById = productDao.getAll().associateBy { it.id }
                productDao.upsertAll(
                    products.map { remote ->
                        // Product pictures are per-device files — never wipe the local one.
                        remote.copy(imagePath = localById[remote.id]?.imagePath, syncStatus = SyncStatus.SYNCED)
                    }
                )
            }
        }

        var settingsApplied = false
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            val current = companySettingsRepository.settings.first()
            companySettingsRepository.updateCompanyInfo(
                name = settings.stringOrNull("companyName") ?: current.companyName,
                address = settings.stringOrNull("companyAddress") ?: current.companyAddress,
                phone = settings.stringOrNull("companyPhone") ?: current.companyPhone,
                footerText = settings.stringOrNull("invoiceFooterText") ?: current.invoiceFooterText,
            )
            settingsApplied = true
        }

        return SyncImportOutcome.Applied(
            kind = SyncFileKind.MANAGER_UPDATE,
            applied = RecordCounts(customers = appliedCustomers, products = products.size),
            skipped = skipped,
            invalid = badCustomers + badProducts,
            settingsApplied = settingsApplied,
            acknowledgedExport = false,
        )
    }

    /**
     * The manager's "received" answer to an earlier export: the listed ids are now safe on the
     * manager's side, so they stop being pending, and the matching log entry shows as confirmed.
     */
    private suspend fun applyReceiptAck(root: JSONObject, user: UserAccountEntity): SyncImportOutcome {
        val target = root.optJSONObject("target")?.stringOrNull("uid")
        if (target != null && target != user.id) {
            return SyncImportOutcome.Rejected("تأكيد الاستلام هذا موجّه لمندوب آخر")
        }
        val accepted = root.optJSONObject("accepted")
        val customerIds = accepted?.optJSONArray("customers").stringList()
        val invoiceIds = accepted?.optJSONArray("invoices").stringList()
        val receiptIds = accepted?.optJSONArray("receipts").stringList()

        database.withTransaction {
            customerIds.chunked(ID_CHUNK).forEach { customerDao.markSyncedByIds(it) }
            invoiceIds.chunked(ID_CHUNK).forEach { invoiceDao.markSyncedByIds(it) }
            receiptIds.chunked(ID_CHUNK).forEach { receiptDao.markSyncedByIds(it) }
        }

        val ackOf = root.stringOrNull("ackOf")
        val acknowledged = ackOf != null && activityStore.markAcknowledged(ackOf)

        return SyncImportOutcome.Applied(
            kind = SyncFileKind.RECEIPT_ACK,
            applied = RecordCounts(customerIds.size, 0, invoiceIds.size, receiptIds.size),
            skipped = 0,
            invalid = 0,
            settingsApplied = false,
            acknowledgedExport = acknowledged,
        )
    }

    private suspend fun logImport(outcome: SyncImportOutcome) {
        val entry = when (outcome) {
            is SyncImportOutcome.Applied -> SyncActivityEntry(
                id = UUID.randomUUID().toString(),
                kind = SyncActivityKind.MANUAL_IMPORT,
                at = Instant.now(),
                status = if (outcome.invalid > 0) SyncActivityStatus.PARTIAL else SyncActivityStatus.SUCCESS,
                title = when (outcome.kind) {
                    SyncFileKind.MANAGER_UPDATE -> "استيراد تحديث من المدير"
                    SyncFileKind.RECEIPT_ACK -> "استيراد تأكيد استلام"
                    SyncFileKind.REP_EXPORT -> "استيراد ملف"
                },
                detail = buildString {
                    if (outcome.settingsApplied) append("مع بيانات الشركة")
                    if (outcome.acknowledgedExport) {
                        if (isNotEmpty()) append(" · ")
                        append("أُغلق تصدير سابق")
                    }
                    if (outcome.skipped > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("تُجوهل ${outcome.skipped}")
                    }
                    if (outcome.invalid > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("تعذّرت قراءة ${outcome.invalid}")
                    }
                }.ifEmpty { null },
                fileName = null,
                counts = outcome.applied,
            )
            is SyncImportOutcome.Rejected -> SyncActivityEntry(
                id = UUID.randomUUID().toString(),
                kind = SyncActivityKind.MANUAL_IMPORT,
                at = Instant.now(),
                status = SyncActivityStatus.FAILED,
                title = "استيراد ملف",
                detail = outcome.message,
                fileName = null,
                counts = RecordCounts(),
            )
        }
        activityStore.record(entry)
    }

    private companion object {
        const val MAX_FILE_BYTES = 30 * 1024 * 1024
        const val ID_CHUNK = 500
    }
}
