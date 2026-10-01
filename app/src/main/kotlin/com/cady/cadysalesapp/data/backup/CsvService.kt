package com.cady.cadysalesapp.data.backup

import android.content.Context
import android.net.Uri
import com.cady.cadysalesapp.data.local.dao.CustomerDao
import com.cady.cadysalesapp.data.local.dao.InvoiceDao
import com.cady.cadysalesapp.data.local.dao.InvoiceItemDao
import com.cady.cadysalesapp.data.local.dao.ProductDao
import com.cady.cadysalesapp.data.local.dao.ReceiptDao
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.ProductRepository
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class CsvDataset(val label: String, val fileToken: String, val importable: Boolean) {
    CUSTOMERS("العملاء", "customers", true),
    PRODUCTS("المنتجات", "products", true),
    INVOICES("الفواتير", "invoices", false),
    INVOICE_ITEMS("بنود الفواتير", "invoice_items", false),
    RECEIPTS("السندات", "receipts", false),
}

data class CustomerDraft(
    val name: String,
    val phone: String?,
    val address: String?,
    val openingBalance: Double,
    val notes: String?,
)

data class ProductDraft(val name: String, val price: Double, val unit: String)

data class ProductUpdate(val existing: ProductEntity, val price: Double, val unit: String)

/** The outcome of reading a CSV *before* anything is saved — the confirm dialog shows it. */
data class CsvImportPreview(
    val dataset: CsvDataset,
    val newCount: Int,
    val updateCount: Int,
    /** Rows that already exist unchanged (or repeat earlier in the same file) — skipped. */
    val duplicateCount: Int,
    /** Rows that could not be used (no name, unreadable number…) — skipped. */
    val invalidCount: Int,
    val sampleNames: List<String>,
    val customerDrafts: List<CustomerDraft> = emptyList(),
    val productDrafts: List<ProductDraft> = emptyList(),
    val productUpdates: List<ProductUpdate> = emptyList(),
) {
    val hasChanges: Boolean get() = newCount + updateCount > 0
}

data class CsvImportResult(val created: Int, val updated: Int)

class CsvException(message: String) : Exception(message)

/**
 * CSV export (five datasets) and import (customers, products). Files are UTF-8 with a BOM so
 * Excel shows Arabic correctly; on import both UTF-8 and the Windows-1256 that Arabic-locale
 * Excel writes are understood, and both "," and ";" delimiters.
 *
 * Import is additive and previewed: customers are never overwritten (a repeat of the same
 * name+phone is skipped), products match by name and only update price/unit.
 */
@Singleton
class CsvService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val receiptDao: ReceiptDao,
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
) {
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneId.systemDefault())
    private val cellDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(ZoneId.systemDefault())

    val exportDir: File get() = File(context.cacheDir, "exports")

    // ------------------------------------------------------------------ export

    suspend fun export(dataset: CsvDataset): File = withContext(Dispatchers.IO) {
        val (header, rows) = when (dataset) {
            CsvDataset.CUSTOMERS -> customersTable()
            CsvDataset.PRODUCTS -> productsTable()
            CsvDataset.INVOICES -> invoicesTable()
            CsvDataset.INVOICE_ITEMS -> invoiceItemsTable()
            CsvDataset.RECEIPTS -> receiptsTable()
        }
        val dir = exportDir
        dir.mkdirs()
        // Exports are throw-away copies — don't let them pile up in cache.
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".csv") }?.forEach { it.delete() }
        val file = File(dir, "cady_${dataset.fileToken}_${fileStamp.format(Instant.now())}.csv")
        val text = buildString {
            append('\uFEFF')
            append(header.joinToString(",") { quote(it) }).append("\r\n")
            for (row in rows) append(row.joinToString(",") { quote(it) }).append("\r\n")
        }
        file.writeText(text, Charsets.UTF_8)
        file
    }

    private suspend fun customersTable(): Pair<List<String>, List<List<String>>> {
        val customers = customerDao.getAll().sortedBy { it.name.lowercase() }
        // Current balance for every customer in one pass (same arithmetic as
        // CustomerRepository.computeBalance: opening + sales - returns - receipts).
        val itemsByInvoice = invoiceItemDao.getAll().groupBy { it.invoiceId }
        val invoiceEffect = HashMap<String, Double>()
        for (invoice in invoiceDao.getAll()) {
            val total = computeInvoiceTotals(invoice, itemsByInvoice[invoice.id].orEmpty()).grandTotal
            val signed = if (invoice.kind == InvoiceKind.SALE) total else -total
            invoiceEffect[invoice.customerId] = (invoiceEffect[invoice.customerId] ?: 0.0) + signed
        }
        val receiptEffect = HashMap<String, Double>()
        for (receipt in receiptDao.getAll()) {
            receiptEffect[receipt.customerId] = (receiptEffect[receipt.customerId] ?: 0.0) + receipt.amount
        }
        val header = listOf("الاسم", "الهاتف", "العنوان", "الرصيد الافتتاحي", "الرصيد الحالي", "نشط", "حد الائتمان", "ملاحظات")
        val rows = customers.map { c ->
            val balance = c.openingBalance + (invoiceEffect[c.id] ?: 0.0) - (receiptEffect[c.id] ?: 0.0)
            listOf(
                safeText(c.name),
                safeText(c.phone, allowPlus = true),
                safeText(c.address),
                num(c.openingBalance),
                num(balance),
                if (c.isActive) "نعم" else "لا",
                c.creditLimit?.let { num(it) }.orEmpty(),
                safeText(c.notes),
            )
        }
        return header to rows
    }

    private suspend fun productsTable(): Pair<List<String>, List<List<String>>> {
        val header = listOf("الاسم", "السعر", "الوحدة")
        val rows = productDao.getAll().sortedBy { it.name.lowercase() }
            .map { listOf(safeText(it.name), num(it.price), safeText(it.unit)) }
        return header to rows
    }

    private suspend fun invoicesTable(): Pair<List<String>, List<List<String>>> {
        val itemsByInvoice = invoiceItemDao.getAll().groupBy { it.invoiceId }
        val header = listOf(
            "رقم الفاتورة", "التاريخ", "النوع", "العميل", "طريقة الدفع",
            "المجموع الفرعي", "الخصم", "الإجمالي", "المندوب", "ملاحظات",
        )
        val rows = invoiceDao.getAll().sortedByDescending { it.date }.map { invoice ->
            val totals = computeInvoiceTotals(invoice, itemsByInvoice[invoice.id].orEmpty())
            listOf(
                safeText(invoice.docNumber),
                cellDate.format(invoice.date),
                if (invoice.kind == InvoiceKind.SALE) "بيع" else "مرتجع",
                safeText(invoice.customerName),
                if (invoice.paymentMode == PaymentMode.CASH) "نقدي" else "آجل",
                num(totals.subTotal),
                num(totals.discountValue),
                num(totals.grandTotal),
                safeText(invoice.repName),
                safeText(invoice.notes),
            )
        }
        return header to rows
    }

    private suspend fun invoiceItemsTable(): Pair<List<String>, List<List<String>>> {
        val invoicesById = invoiceDao.getAll().associateBy { it.id }
        val header = listOf("رقم الفاتورة", "التاريخ", "العميل", "الصنف", "الكمية", "السعر", "الإجمالي")
        val rows = invoiceItemDao.getAll()
            .mapNotNull { item -> invoicesById[item.invoiceId]?.let { invoice -> invoice to item } }
            .sortedByDescending { (invoice, _) -> invoice.date }
            .map { (invoice, item) ->
                listOf(
                    safeText(invoice.docNumber),
                    cellDate.format(invoice.date),
                    safeText(invoice.customerName),
                    safeText(item.productName),
                    num(item.quantity),
                    num(item.price),
                    num(item.price * item.quantity),
                )
            }
        return header to rows
    }

    private suspend fun receiptsTable(): Pair<List<String>, List<List<String>>> {
        val header = listOf("رقم السند", "التاريخ", "العميل", "المبلغ", "الطريقة", "المندوب", "ملاحظات")
        val rows = receiptDao.getAll().sortedByDescending { it.date }.map { receipt ->
            listOf(
                safeText(receipt.docNumber),
                cellDate.format(receipt.date),
                safeText(receipt.customerName),
                num(receipt.amount),
                if (receipt.method == ReceiptMethod.CASH) "نقدي" else "تحويل",
                safeText(receipt.repName),
                safeText(receipt.notes),
            )
        }
        return header to rows
    }

    // ------------------------------------------------------------------ import: preview

    suspend fun previewCustomers(uri: Uri, user: UserAccountEntity): CsvImportPreview = withContext(Dispatchers.IO) {
        val table = readTable(uri)
        val nameCol = table.column(NAME_HEADERS) ?: throw CsvException("لم أجد عمود «الاسم» في الملف")
        val phoneCol = table.column(PHONE_HEADERS)
        val addressCol = table.column(ADDRESS_HEADERS)
        val balanceCol = table.column(BALANCE_HEADERS)
        val notesCol = table.column(NOTES_HEADERS)

        val seen = HashSet<String>()
        customerDao.getAllByOwner(user.id).forEach { seen.add(customerKey(it.name, it.phone)) }

        val drafts = ArrayList<CustomerDraft>()
        var duplicates = 0
        var invalid = 0
        for (row in table.rows) {
            val name = row.cell(nameCol).trim()
            if (name.isEmpty()) {
                invalid++
                continue
            }
            val balanceRaw = balanceCol?.let { row.cell(it) }.orEmpty()
            val balance = if (balanceRaw.isBlank()) 0.0 else parseNumber(balanceRaw, table.decimalComma)
            if (balance == null) {
                invalid++
                continue
            }
            val phone = phoneCol?.let { row.cell(it).trim() }?.ifEmpty { null }
            if (!seen.add(customerKey(name, phone))) {
                duplicates++
                continue
            }
            drafts.add(
                CustomerDraft(
                    name = name,
                    phone = phone,
                    address = addressCol?.let { row.cell(it).trim() }?.ifEmpty { null },
                    openingBalance = balance,
                    notes = notesCol?.let { row.cell(it).trim() }?.ifEmpty { null },
                )
            )
        }
        CsvImportPreview(
            dataset = CsvDataset.CUSTOMERS,
            newCount = drafts.size,
            updateCount = 0,
            duplicateCount = duplicates,
            invalidCount = invalid,
            sampleNames = drafts.take(SAMPLE_SIZE).map { it.name },
            customerDrafts = drafts,
        )
    }

    suspend fun previewProducts(uri: Uri, user: UserAccountEntity): CsvImportPreview = withContext(Dispatchers.IO) {
        // The product catalog is shared and only a manager writes it (firestore.rules).
        if (user.role != UserRole.MANAGER) throw CsvException("استيراد المنتجات متاح للمدير فقط")
        val table = readTable(uri)
        val nameCol = table.column(NAME_HEADERS) ?: throw CsvException("لم أجد عمود «الاسم» في الملف")
        val priceCol = table.column(PRICE_HEADERS) ?: throw CsvException("لم أجد عمود «السعر» في الملف")
        val unitCol = table.column(UNIT_HEADERS)

        val existingByName = HashMap<String, ProductEntity>()
        productDao.getAll().forEach { existingByName[textKey(it.name)] = it }

        val seen = HashSet<String>()
        val creates = ArrayList<ProductDraft>()
        val updates = ArrayList<ProductUpdate>()
        var duplicates = 0
        var invalid = 0
        for (row in table.rows) {
            val name = row.cell(nameCol).trim()
            val price = parseNumber(row.cell(priceCol), table.decimalComma)
            if (name.isEmpty() || price == null || price < 0.0) {
                invalid++
                continue
            }
            val key = textKey(name)
            if (!seen.add(key)) {
                duplicates++
                continue
            }
            val unit = unitCol?.let { row.cell(it).trim() }.orEmpty()
            val existing = existingByName[key]
            if (existing == null) {
                creates.add(ProductDraft(name, price, unit.ifEmpty { DEFAULT_UNIT }))
            } else {
                val newUnit = unit.ifEmpty { existing.unit }
                if (existing.price != price || existing.unit != newUnit) {
                    updates.add(ProductUpdate(existing, price, newUnit))
                } else {
                    duplicates++
                }
            }
        }
        CsvImportPreview(
            dataset = CsvDataset.PRODUCTS,
            newCount = creates.size,
            updateCount = updates.size,
            duplicateCount = duplicates,
            invalidCount = invalid,
            sampleNames = (creates.map { it.name } + updates.map { it.existing.name }).take(SAMPLE_SIZE),
            productDrafts = creates,
            productUpdates = updates,
        )
    }

    // ------------------------------------------------------------------ import: apply

    /** Saves exactly what the preview showed. Each row goes through the normal repository write, so it syncs like any other edit. */
    suspend fun apply(preview: CsvImportPreview, user: UserAccountEntity): CsvImportResult = withContext(Dispatchers.IO) {
        when (preview.dataset) {
            CsvDataset.CUSTOMERS -> {
                for (draft in preview.customerDrafts) {
                    customerRepository.createCustomer(
                        ownerUid = user.id,
                        name = draft.name,
                        phone = draft.phone,
                        address = draft.address,
                        openingBalance = draft.openingBalance,
                        creditLimit = null,
                        notes = draft.notes,
                    )
                }
                CsvImportResult(created = preview.customerDrafts.size, updated = 0)
            }
            CsvDataset.PRODUCTS -> {
                if (user.role != UserRole.MANAGER) throw CsvException("استيراد المنتجات متاح للمدير فقط")
                for (draft in preview.productDrafts) {
                    productRepository.createProduct(draft.name, draft.price, draft.unit, imagePath = null)
                }
                for (update in preview.productUpdates) {
                    productRepository.updateProduct(update.existing.copy(price = update.price, unit = update.unit))
                }
                CsvImportResult(created = preview.productDrafts.size, updated = preview.productUpdates.size)
            }
            else -> throw CsvException("هذا النوع متاح للتصدير فقط")
        }
    }

    // ------------------------------------------------------------------ parsing

    private class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val decimalComma: Boolean,
    ) {
        fun column(aliases: Set<String>): Int? {
            val index = header.indexOfFirst { aliases.contains(normalizeHeader(it)) }
            return if (index >= 0) index else null
        }
    }

    private fun readTable(uri: Uri): Table {
        val input = context.contentResolver.openInputStream(uri) ?: throw CsvException("تعذّر فتح الملف")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_CSV_BYTES) throw CsvException("الملف كبير جدًا")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
        val text = decode(bytes).removePrefix("\uFEFF")
        val delimiter = detectDelimiter(text)
        val all = parseCsv(text, delimiter)
        if (all.size < 2) throw CsvException("الملف فارغ أو لا يحتوي صفوف بيانات")
        return Table(header = all.first(), rows = all.drop(1), decimalComma = delimiter == ';')
    }

    private fun decode(bytes: ByteArray): String {
        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            // Arabic-locale Excel's plain "CSV" is Windows-1256, not UTF-8.
            String(bytes, Charset.forName("windows-1256"))
        }
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val commas = firstLine.count { it == ',' }
        val semicolons = firstLine.count { it == ';' }
        val tabs = firstLine.count { it == '\t' }
        return when {
            tabs > commas && tabs > semicolons -> '\t'
            semicolons > commas -> ';'
            else -> ','
        }
    }

    /** RFC 4180: quoted cells may hold delimiters, quotes ("") and line breaks. Fully blank lines are dropped. */
    private fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else if (c == '"') {
                inQuotes = true
            } else if (c == delimiter) {
                row.add(cell.toString())
                cell.setLength(0)
            } else if (c == '\n') {
                row.add(cell.toString())
                cell.setLength(0)
                rows.add(row)
                row = ArrayList()
            } else if (c != '\r') {
                cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun List<String>.cell(index: Int): String = if (index < size) this[index] else ""

    /** Digits in any script, "٫" as the decimal mark, thousands separators ignored. Null = not a number. */
    private fun parseNumber(raw: String, decimalComma: Boolean): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val sb = StringBuilder()
        for (ch in trimmed) {
            when {
                ch in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ch in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                ch == '٫' -> sb.append('.')
                ch == ',' -> if (decimalComma) sb.append('.')
                ch == '٬' || ch == ' ' || ch == '\u00A0' -> Unit
                else -> sb.append(ch)
            }
        }
        val value = sb.toString().toDoubleOrNull() ?: return null
        return if (value.isFinite()) value else null
    }

    // ------------------------------------------------------------------ cell formatting / keys

    private fun quote(raw: String): String {
        val needsQuotes = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
    }

    /**
     * Spreadsheet formula injection: a text cell that starts with = + - @ is executed as a
     * formula by Excel/Sheets, so it is neutralised with a leading apostrophe. Phone numbers may
     * legitimately start with "+" (allowPlus), but never with "=" or "@".
     */
    private fun safeText(raw: String?, allowPlus: Boolean = false): String {
        val value = raw.orEmpty()
        if (value.isEmpty()) return value
        val risky = when (value[0]) {
            '=', '@', '\t', '\r' -> true
            '+', '-' -> !allowPlus
            else -> false
        }
        return if (risky) "'$value" else value
    }

    private fun num(value: Double): String =
        if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun textKey(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun customerKey(name: String, phone: String?): String =
        textKey(name) + "|" + phone.orEmpty().filter { it.isDigit() }

    private companion object {
        const val MAX_CSV_BYTES = 10 * 1024 * 1024
        const val SAMPLE_SIZE = 5
        const val DEFAULT_UNIT = "قطعة"

        fun normalizeHeader(raw: String): String =
            raw.trim().removePrefix("\uFEFF").lowercase().replace(Regex("[\\s_\\-]+"), "")

        val NAME_HEADERS = setOf("name", "الاسم", "اسم", "اسمالعميل", "العميل", "اسمالمنتج", "المنتج", "الصنف")
        val PHONE_HEADERS = setOf("phone", "mobile", "tel", "الهاتف", "رقمالهاتف", "الجوال", "رقمالجوال", "موبايل")
        val ADDRESS_HEADERS = setOf("address", "العنوان")
        val BALANCE_HEADERS = setOf("openingbalance", "balance", "الرصيدالافتتاحي", "رصيدافتتاحي", "الرصيد")
        val NOTES_HEADERS = setOf("notes", "note", "ملاحظات", "ملاحظة")
        val PRICE_HEADERS = setOf("price", "السعر", "سعر", "سعرالوحدة")
        val UNIT_HEADERS = setOf("unit", "الوحدة", "وحدة")
    }
}
