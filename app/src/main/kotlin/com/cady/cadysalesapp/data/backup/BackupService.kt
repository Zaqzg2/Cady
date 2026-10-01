package com.cady.cadysalesapp.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cady.cadysalesapp.data.files.fileProviderUri
import com.cady.cadysalesapp.data.local.CadyDatabase
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
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.data.repository.CompanySettingsRepository
import com.cady.cadysalesapp.data.repository.PdfLayoutMode
import com.cady.cadysalesapp.data.sync.PathMapper
import com.cady.cadysalesapp.data.sync.PendingInvoice
import com.cady.cadysalesapp.data.sync.RecordCounts
import com.cady.cadysalesapp.data.sync.SYNC_SCHEMA_VERSION
import com.cady.cadysalesapp.data.sync.stringOrNull
import com.cady.cadysalesapp.data.sync.toCustomerEntity
import com.cady.cadysalesapp.data.sync.toInvoiceEntity
import com.cady.cadysalesapp.data.sync.toInvoiceItems
import com.cady.cadysalesapp.data.sync.toJson
import com.cady.cadysalesapp.data.sync.toProductEntity
import com.cady.cadysalesapp.data.sync.toReceiptEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

const val BACKUP_FORMAT = "cady-backup"

/** Bump only if the zip layout itself changes (a newer file then refuses to restore on an older app). */
const val BACKUP_VERSION = 1

/**
 * Full local backups, stored as zips in app-private storage ([backupDir]).
 *
 *   manifest.json            what/when/whose (read on its own to fill the list cheaply)
 *   data/customers.jsonl     one JSON object per line — same codec as the manual sync files
 *   data/products.jsonl
 *   data/invoices.jsonl      each invoice carries its line items
 *   data/receipts.jsonl
 *   data/settings.json       company info + print layout (NOT the PIN, NOT accounts/password hashes)
 *   files/…                  signatures, logo, product pictures — stored relative to filesDir
 *
 * Restore is a *merge*, never a wipe: rows in the backup replace their same-id twins, rows created
 * after the backup stay untouched, and a safety snapshot of the current state is taken first.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CadyDatabase,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val companySettingsRepository: CompanySettingsRepository,
    private val accountRepository: AccountRepository,
) {
    private val createLock = Mutex()
    private val restoreLock = Mutex()
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())

    val backupDir: File get() = File(context.filesDir, "backups")

    private data class Manifest(
        val backupVersion: Int,
        val dbSchemaVersion: Int,
        val createdAt: Instant,
        val kind: BackupKind,
        val counts: RecordCounts,
        val ownerUid: String?,
        val ownerName: String?,
        val appVersion: String?,
    )

    private data class Snapshot(
        val customers: List<CustomerEntity>,
        val products: List<ProductEntity>,
        val invoices: List<InvoiceEntity>,
        val items: List<InvoiceItemEntity>,
        val receipts: List<ReceiptEntity>,
    )

    // ------------------------------------------------------------------ create

    suspend fun createBackup(kind: BackupKind): BackupInfo = createBackupInternal(kind, protect = null)

    private suspend fun createBackupInternal(kind: BackupKind, protect: File?): BackupInfo =
        withContext(Dispatchers.IO) {
            createLock.withLock { doCreateBackup(kind, protect) }
        }

    private suspend fun doCreateBackup(kind: BackupKind, protect: File?): BackupInfo {
        val now = Instant.now()
        val owner = try {
            accountRepository.currentUser.first()
        } catch (e: Exception) {
            null
        }

        // One consistent read of the whole database (all owners — a manager's DB holds every rep's rows).
        val snapshot = database.withTransaction {
            Snapshot(
                customers = customerDao.getAll(),
                products = productDao.getAll(),
                invoices = invoiceDao.getAll(),
                items = invoiceItemDao.getAll(),
                receipts = receiptDao.getAll(),
            )
        }
        val itemsByInvoice = snapshot.items.groupBy { it.invoiceId }
        val settings = companySettingsRepository.settings.first()

        // absolute path on this device -> entry name inside the zip (filled while the JSON is built)
        val media = LinkedHashMap<String, String>()
        val mapPath: PathMapper = { absolute -> registerMedia(absolute, media) }

        val customerLines = snapshot.customers.map { it.toJson().toString() }
        val productLines = snapshot.products.map { it.toJson(mapPath).toString() }
        val invoiceLines = snapshot.invoices.map { it.toJson(itemsByInvoice[it.id].orEmpty(), mapPath).toString() }
        val receiptLines = snapshot.receipts.map { it.toJson(mapPath).toString() }
        val settingsJson = settingsToJson(settings, mapPath)
        val counts = RecordCounts(
            customers = snapshot.customers.size,
            products = snapshot.products.size,
            invoices = snapshot.invoices.size,
            receipts = snapshot.receipts.size,
        )

        val manifest = JSONObject()
        manifest.put("format", BACKUP_FORMAT)
        manifest.put("backupVersion", BACKUP_VERSION)
        manifest.put("dbSchemaVersion", SYNC_SCHEMA_VERSION)
        manifest.put("createdAt", now.toEpochMilli())
        manifest.put("kind", kind.wire)
        manifest.put("appVersion", appVersionName() ?: JSONObject.NULL)
        val ownerJson = JSONObject()
        ownerJson.put("uid", owner?.id ?: JSONObject.NULL)
        ownerJson.put("name", owner?.displayName ?: JSONObject.NULL)
        manifest.put("owner", ownerJson)
        val countsJson = JSONObject()
        countsJson.put("customers", counts.customers)
        countsJson.put("products", counts.products)
        countsJson.put("invoices", counts.invoices)
        countsJson.put("receipts", counts.receipts)
        manifest.put("counts", countsJson)
        manifest.put("files", media.size)

        val dir = backupDir
        dir.mkdirs()
        val baseName = "cady_backup_${kind.wire}_${stamp.format(now)}"
        var target = File(dir, "$baseName.zip")
        var suffix = 2
        while (target.exists()) {
            target = File(dir, "${baseName}_$suffix.zip")
            suffix++
        }
        val tmp = File(dir, target.name + ".tmp")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zip ->
                zip.putBytes(MANIFEST_ENTRY, manifest.toString().toByteArray(Charsets.UTF_8))
                zip.putLines("data/customers.jsonl", customerLines)
                zip.putLines("data/products.jsonl", productLines)
                zip.putLines("data/invoices.jsonl", invoiceLines)
                zip.putLines("data/receipts.jsonl", receiptLines)
                zip.putBytes("data/settings.json", settingsJson.toString().toByteArray(Charsets.UTF_8))
                for ((absolute, entryName) in media) {
                    // Read first, then write: a file vanishing mid-way must not leave a half entry.
                    val bytes = try {
                        File(absolute).readBytes()
                    } catch (e: Exception) {
                        null
                    }
                    if (bytes != null) zip.putBytes(entryName, bytes)
                }
            }
            if (!tmp.renameTo(target)) throw IOException("rename failed")
        } catch (e: Exception) {
            tmp.delete()
            throw BackupException("تعذّر إنشاء النسخة الاحتياطية: ${e.message ?: "خطأ في التخزين"}")
        }

        when (kind) {
            BackupKind.AUTO -> prune(BackupKind.AUTO, AUTO_KEEP, protect)
            BackupKind.PRE_RESTORE -> prune(BackupKind.PRE_RESTORE, PRE_RESTORE_KEEP, protect)
            BackupKind.MANUAL -> Unit
        }
        return readInfo(target)
    }

    private fun prune(kind: BackupKind, keep: Int, protect: File?) {
        val protectedPath = protect?.let { runCatching { it.canonicalPath }.getOrNull() }
        listFilesSync()
            .map { readInfo(it) }
            .filter { it.kind == kind }
            .sortedByDescending { it.createdAt }
            .drop(keep)
            .filter { protectedPath == null || runCatching { it.file.canonicalPath }.getOrNull() != protectedPath }
            .forEach { it.file.delete() }
    }

    // ------------------------------------------------------------------ list / delete / share

    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        listFilesSync().map { readInfo(it) }.sortedByDescending { it.createdAt }
    }

    private fun listFilesSync(): List<File> =
        backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip") }.orEmpty()

    private fun readInfo(file: File): BackupInfo {
        val manifest = try {
            ZipFile(file).use { readManifest(it) }
        } catch (e: Exception) {
            null
        }
        return if (manifest != null) {
            BackupInfo(
                file = file,
                createdAt = manifest.createdAt,
                sizeBytes = file.length(),
                kind = manifest.kind,
                counts = manifest.counts,
                ownerName = manifest.ownerName,
                isValid = true,
            )
        } else {
            BackupInfo(
                file = file,
                createdAt = Instant.ofEpochMilli(file.lastModified()),
                sizeBytes = file.length(),
                kind = BackupKind.MANUAL,
                counts = null,
                ownerName = null,
                isValid = false,
            )
        }
    }

    /** Deletes one saved backup. Refuses anything outside [backupDir]. */
    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        val inside = runCatching { file.canonicalFile.parentFile == backupDir.canonicalFile }.getOrDefault(false)
        inside && file.delete()
    }

    fun shareUri(file: File): Uri = context.fileProviderUri(file)

    /** "Save somewhere else" — copies a backup to a location the person picked (Storage Access Framework). */
    suspend fun copyTo(file: File, destination: Uri) {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: throw BackupException("تعذّر فتح مكان الحفظ")
            output.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
        }
    }

    // ------------------------------------------------------------------ restore

    /** Copies a file the person picked into cache so it can be inspected and restored. */
    suspend fun stageExternal(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "restore")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "incoming.zip")
        val input = context.contentResolver.openInputStream(uri) ?: throw BackupException("تعذّر فتح الملف")
        input.use { source ->
            FileOutputStream(target).use { sink ->
                if (!copyLimited(source, sink, MAX_ZIP_BYTES)) {
                    target.delete()
                    throw BackupException("الملف كبير جدًا")
                }
            }
        }
        target
    }

    fun discardStaged(file: File) {
        runCatching { file.delete() }
    }

    /** Reads only the manifest — used by the confirm dialog, changes nothing. */
    suspend fun previewOf(file: File): BackupPreview = withContext(Dispatchers.IO) {
        val manifest = try {
            ZipFile(file).use { readManifest(it) }
        } catch (e: Exception) {
            null
        }
        if (manifest == null) throw BackupException("الملف ليس نسخة احتياطية صالحة من كادي")
        ensureReadable(manifest)
        BackupPreview(
            createdAt = manifest.createdAt,
            kind = manifest.kind,
            counts = manifest.counts,
            ownerUid = manifest.ownerUid,
            ownerName = manifest.ownerName,
            appVersion = manifest.appVersion,
            sizeBytes = file.length(),
        )
    }

    private fun ensureReadable(manifest: Manifest) {
        if (manifest.backupVersion > BACKUP_VERSION || manifest.dbSchemaVersion > SYNC_SCHEMA_VERSION) {
            throw BackupException("هذه النسخة من إصدار أحدث من التطبيق — حدّث التطبيق أولًا")
        }
    }

    suspend fun restore(file: File): RestoreResult = withContext(Dispatchers.IO) {
        restoreLock.withLock { doRestore(file) }
    }

    private suspend fun doRestore(file: File): RestoreResult {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw BackupException("الملف ليس نسخة احتياطية صالحة")
        }
        return zip.use {
            val manifest = try {
                readManifest(zip)
            } catch (e: Exception) {
                null
            } ?: throw BackupException("الملف ليس نسخة احتياطية من كادي")
            ensureReadable(manifest)

            // Parse everything first: a broken file must fail before a single row is touched.
            var unreadable = 0
            val restorePath: PathMapper = { entryName ->
                entryName?.let { resolveRestoreTarget(it)?.absolutePath }
            }
            val customers = readJsonLines(zip, "data/customers.jsonl", { unreadable++ }) { it.toCustomerEntity() }
            val products = readJsonLines(zip, "data/products.jsonl", { unreadable++ }) { it.toProductEntity(restorePath) }
            val invoices = readJsonLines(zip, "data/invoices.jsonl", { unreadable++ }) { o ->
                val invoice = o.toInvoiceEntity(restorePath)
                PendingInvoice(invoice, o.toInvoiceItems(invoice.id))
            }
            val receipts = readJsonLines(zip, "data/receipts.jsonl", { unreadable++ }) { it.toReceiptEntity(restorePath) }
            val settingsEntry = zip.getEntry("data/settings.json")
            val settingsJson = settingsEntry?.let {
                try {
                    JSONObject(zip.getInputStream(it).use { stream -> stream.readBytes().toString(Charsets.UTF_8) })
                } catch (e: Exception) {
                    null
                }
            }

            // Safety net: snapshot the current state first. If that fails, do not restore.
            try {
                createBackupInternal(BackupKind.PRE_RESTORE, protect = file)
            } catch (e: BackupException) {
                throw BackupException("تعذّر إنشاء نسخة أمان قبل الاسترجاع — لم يتغيّر شيء. ${e.message.orEmpty()}")
            }

            // Media first (harmless if the transaction below then fails: they are just extra files).
            var filesRestored = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.startsWith("files/")) continue
                val destination = resolveRestoreTarget(entry.name) ?: continue
                destination.parentFile?.mkdirs()
                val ok = zip.getInputStream(entry).use { input ->
                    FileOutputStream(destination).use { output -> copyLimited(input, output, MAX_MEDIA_BYTES) }
                }
                if (ok) filesRestored++ else destination.delete()
            }

            database.withTransaction {
                customerDao.upsertAll(customers)
                productDao.upsertAll(products)
                invoiceDao.upsertAll(invoices.map { it.invoice })
                for (pending in invoices) {
                    // Item ids are regenerated on every invoice save, so replace — don't merge — the lines.
                    invoiceItemDao.deleteForInvoice(pending.invoice.id)
                    invoiceItemDao.upsertAll(pending.items)
                }
                receiptDao.upsertAll(receipts)
            }

            var settingsRestored = false
            if (settingsJson != null) {
                restoreSettings(settingsJson, restorePath)
                settingsRestored = true
            }

            RestoreResult(
                restored = RecordCounts(customers.size, products.size, invoices.size, receipts.size),
                unreadableRows = unreadable,
                filesRestored = filesRestored,
                settingsRestored = settingsRestored,
            )
        }
    }

    private suspend fun restoreSettings(o: JSONObject, restorePath: PathMapper) {
        val current = companySettingsRepository.settings.first()
        // Blank text in a backup means "never filled in" — it must not blank out what is set now.
        fun text(key: String, fallback: String): String = o.stringOrNull(key)?.takeIf { it.isNotBlank() } ?: fallback
        companySettingsRepository.updateCompanyInfo(
            name = text("companyName", current.companyName),
            address = text("companyAddress", current.companyAddress),
            phone = text("companyPhone", current.companyPhone),
            footerText = text("invoiceFooterText", current.invoiceFooterText),
        )
        o.stringOrNull("repDisplayName")?.takeIf { it.isNotBlank() }
            ?.let { companySettingsRepository.updateRepDisplayName(it) }
        restorePath(o.stringOrNull("companyLogoPath"))
            ?.let { companySettingsRepository.updateCompanyLogoPath(it) }
        restorePath(o.stringOrNull("repSignaturePath"))
            ?.let { companySettingsRepository.updateRepSignaturePath(it) }
        o.stringOrNull("printLayoutMode")
            ?.let { runCatching { PdfLayoutMode.valueOf(it) }.getOrNull() }
            ?.let { companySettingsRepository.updatePrintLayoutMode(it) }
        if (!o.isNull("printFontScale")) {
            companySettingsRepository.updatePrintFontScale(o.getDouble("printFontScale").toFloat())
        }
        if (!o.isNull("printLineSpacingExtra")) {
            companySettingsRepository.updatePrintLineSpacingExtra(o.getDouble("printLineSpacingExtra").toFloat())
        }
        if (!o.isNull("printBlackThreshold")) {
            companySettingsRepository.updatePrintBlackThreshold(o.getInt("printBlackThreshold"))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun settingsToJson(s: CompanySettings, mapPath: PathMapper): JSONObject {
        val o = JSONObject()
        o.put("companyName", s.companyName)
        o.put("companyAddress", s.companyAddress)
        o.put("companyPhone", s.companyPhone)
        o.put("invoiceFooterText", s.invoiceFooterText)
        o.put("repDisplayName", s.repDisplayName)
        o.put("companyLogoPath", mapPath(s.companyLogoPath) ?: JSONObject.NULL)
        o.put("repSignaturePath", mapPath(s.repSignaturePath) ?: JSONObject.NULL)
        o.put("printLayoutMode", s.printLayoutMode.name)
        o.put("printFontScale", s.printFontScale.toDouble())
        o.put("printLineSpacingExtra", s.printLineSpacingExtra.toDouble())
        o.put("printBlackThreshold", s.printBlackThreshold)
        return o
    }

    private fun readManifest(zip: ZipFile): Manifest? {
        val entry = zip.getEntry(MANIFEST_ENTRY) ?: return null
        val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        val o = JSONObject(text)
        if (o.stringOrNull("format") != BACKUP_FORMAT) return null
        val backupVersion = o.optInt("backupVersion", 0)
        if (backupVersion < 1) return null
        val counts = o.optJSONObject("counts")
        val owner = o.optJSONObject("owner")
        return Manifest(
            backupVersion = backupVersion,
            dbSchemaVersion = o.optInt("dbSchemaVersion", 0),
            createdAt = Instant.ofEpochMilli(o.getLong("createdAt")),
            kind = BackupKind.fromWire(o.stringOrNull("kind")),
            counts = RecordCounts(
                customers = counts?.optInt("customers", 0) ?: 0,
                products = counts?.optInt("products", 0) ?: 0,
                invoices = counts?.optInt("invoices", 0) ?: 0,
                receipts = counts?.optInt("receipts", 0) ?: 0,
            ),
            ownerUid = owner?.stringOrNull("uid"),
            ownerName = owner?.stringOrNull("name"),
            appVersion = o.stringOrNull("appVersion"),
        )
    }

    private inline fun <T> readJsonLines(
        zip: ZipFile,
        name: String,
        onInvalid: () -> Unit,
        parse: (JSONObject) -> T,
    ): List<T> {
        val entry = zip.getEntry(name) ?: return emptyList()
        val out = ArrayList<T>()
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    out.add(parse(JSONObject(line)))
                } catch (e: Exception) {
                    onInvalid()
                }
            }
        }
        return out
    }

    /** Registers a local file for inclusion and returns its zip entry name, or null if it can't be included. */
    private fun registerMedia(absolute: String?, media: MutableMap<String, String>): String? {
        if (absolute.isNullOrBlank()) return null
        media[absolute]?.let { return it }
        val file = File(absolute)
        if (!file.isFile) return null
        val relative = try {
            relativeToFilesDir(file)
        } catch (e: IOException) {
            null
        } ?: return null
        val entryName = "files/$relative"
        media[absolute] = entryName
        return entryName
    }

    private fun relativeToFilesDir(file: File): String? {
        val root = context.filesDir.canonicalFile
        val canonical = file.canonicalFile
        val rootPrefix = root.path + File.separator
        if (!canonical.path.startsWith(rootPrefix)) return null
        val relative = canonical.path.removePrefix(rootPrefix).replace(File.separatorChar, '/')
        // Backups and the sync outbox also live under filesDir — never nest them inside a backup.
        if (relative.startsWith("backups/") || relative.startsWith("sync/")) return null
        return relative
    }

    /**
     * Where a "files/…" zip entry may be written. Anything that could escape filesDir (zip-slip:
     * "..", absolute paths, backslashes) or overwrite the backups/sync folders yields null.
     */
    private fun resolveRestoreTarget(entryName: String): File? {
        if (!entryName.startsWith("files/")) return null
        val relative = entryName.removePrefix("files/")
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("..") || relative.contains('\\')) return null
        val top = relative.substringBefore('/')
        if (top == "backups" || top == "sync") return null
        val root = context.filesDir.canonicalFile
        val target = File(root, relative).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) return null
        return target
    }

    private fun appVersionName(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        null
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > limit) return false
            output.write(buffer, 0, read)
        }
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putLines(name: String, lines: List<String>) {
        putNextEntry(ZipEntry(name))
        for (line in lines) {
            write(line.toByteArray(Charsets.UTF_8))
            write('\n'.code)
        }
        closeEntry()
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val AUTO_KEEP = 7
        const val PRE_RESTORE_KEEP = 3
        const val MAX_ZIP_BYTES = 300L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 25L * 1024 * 1024
    }
}
