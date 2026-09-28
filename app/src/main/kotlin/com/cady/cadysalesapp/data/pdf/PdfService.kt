package com.cady.cadysalesapp.data.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceItemEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.data.repository.CompanySettings
import com.cady.cadysalesapp.domain.LedgerRow
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A4 at 72dpi — plain PdfDocument + Canvas, no external library, so there's no
 * extra dependency to version-pin. Raw Canvas.drawText doesn't shape/join
 * Arabic correctly on its own; every piece of Arabic text goes through
 * StaticLayout (drawRtlText below), which does real BiDi + shaping, matching
 * why the Flutter app needed its own RTL workarounds in pdf_service.dart.
 */
@Singleton
class PdfService @Inject constructor() {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f

    // Locale.US pins Latin digits and comma thousands separators ("14,605"),
    // the way the reference documents print them, whatever the device language.
    private val moneyFormat = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    private fun titlePaint() = TextPaint().apply { textSize = 20f; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun labelPaint() = TextPaint().apply { textSize = 11f; color = 0xFF666666.toInt() }
    private fun bodyPaint() = TextPaint().apply { textSize = 13f; color = 0xFF000000.toInt() }
    private fun boldPaint() = TextPaint().apply { textSize = 14f; isFakeBoldText = true; color = 0xFF000000.toInt() }

    /** Draws right-to-left shaped/joined text via StaticLayout — never raw
        Canvas.drawText for Arabic content, which doesn't shape correctly. */
    private fun Canvas.drawRtlText(text: String, right: Float, top: Float, width: Float, paint: TextPaint): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .build()
        save()
        translate(right - width, top)
        layout.draw(this)
        restore()
        return layout.height.toFloat()
    }

    /** The figure as text. A negative one is wrapped in an LTR embedding
        (LRE … PDF) so its minus sign stays glued to the left of the digits
        ("-1,200"): inside an RTL paragraph a bare hyphen is resolved to the
        paragraph direction and would jump to the digits' right. Amounts
        that round to zero print a plain "0", never "-0". */
    private fun signedNumber(value: Double): String {
        val v = if (kotlin.math.abs(value) < 0.5) 0.0 else value
        val text = moneyFormat.format(v)
        return if (v < 0) "\u202A$text\u202C" else text
    }

    private fun money(value: Double) = "${signedNumber(value)} ر.ي"

    fun generateInvoicePdf(invoice: InvoiceEntity, items: List<InvoiceItemEntity>, company: CompanySettings, outputFile: File) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        val right = pageWidth - margin
        val contentWidth = pageWidth - margin * 2
        var y = margin

        if (company.companyName.isNotBlank()) {
            y += canvas.drawRtlText(company.companyName, right, y, contentWidth, boldPaint()) + 6f
        }
        val title = if (invoice.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع"
        y += canvas.drawRtlText(title, right, y, contentWidth, titlePaint()) + 8f
        y += canvas.drawRtlText("رقم: ${invoice.docNumber}", right, y, contentWidth, labelPaint()) + 4f
        y += canvas.drawRtlText(dateFormat.format(invoice.date.atZone(java.time.ZoneId.systemDefault())), right, y, contentWidth, labelPaint()) + 4f
        y += canvas.drawRtlText("العميل: ${invoice.customerName}", right, y, contentWidth, bodyPaint()) + 16f

        // Table header — RTL column order: اسم الصنف | السعر | الكمية | الإجمالي
        // (rightmost column is read first, matching the RTL table trick noted
        // in the Flutter app's pdf_service.dart).
        val col1 = contentWidth * 0.40f // اسم الصنف
        val col2 = contentWidth * 0.20f // السعر
        val col3 = contentWidth * 0.15f // الكمية
        val col4 = contentWidth * 0.25f // الإجمالي

        y += canvas.drawRtlText("الصنف", right, y, col1, boldPaint())
        canvas.drawRtlText("السعر", right - col1, y, col2, boldPaint())
        canvas.drawRtlText("الكمية", right - col1 - col2, y, col3, boldPaint())
        canvas.drawRtlText("الإجمالي", right - col1 - col2 - col3, y, col4, boldPaint())
        y += 20f
        canvas.drawLine(margin, y, right, y, Paint().apply { strokeWidth = 1f; color = 0xFFCCCCCC.toInt() })
        y += 8f

        items.forEach { item ->
            val rowHeight = canvas.drawRtlText(item.productName, right, y, col1, bodyPaint())
            canvas.drawRtlText(money(item.price), right - col1, y, col2, bodyPaint())
            canvas.drawRtlText(item.quantity.toString(), right - col1 - col2, y, col3, bodyPaint())
            canvas.drawRtlText(money(item.price * item.quantity), right - col1 - col2 - col3, y, col4, bodyPaint())
            y += rowHeight.coerceAtLeast(18f) + 6f
        }

        y += 12f
        canvas.drawLine(margin, y, right, y, Paint().apply { strokeWidth = 1f; color = 0xFFCCCCCC.toInt() })
        y += 12f

        val totals = computeInvoiceTotals(invoice, items)
        y += canvas.drawTotalsRow("المجموع الفرعي", totals.subTotal, right, y, contentWidth, bodyPaint())
        if (totals.discountValue > 0) {
            y += canvas.drawTotalsRow("الخصم", totals.discountValue, right, y, contentWidth, bodyPaint())
        }
        y += canvas.drawTotalsRow("الإجمالي", totals.grandTotal, right, y, contentWidth, boldPaint()) + 20f

        if (invoice.repName != null) {
            y += canvas.drawRtlText("المندوب: ${invoice.repName}", right, y, contentWidth, labelPaint()) + 4f
        }
        if (company.invoiceFooterText.isNotBlank()) {
            canvas.drawRtlText(company.invoiceFooterText, right, y + 12f, contentWidth, labelPaint())
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    fun generateReceiptPdf(receipt: ReceiptEntity, company: CompanySettings, outputFile: File) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        val right = pageWidth - margin
        val contentWidth = pageWidth - margin * 2
        var y = margin

        if (company.companyName.isNotBlank()) {
            y += canvas.drawRtlText(company.companyName, right, y, contentWidth, boldPaint()) + 6f
        }
        y += canvas.drawRtlText("سند قبض", right, y, contentWidth, titlePaint()) + 8f
        y += canvas.drawRtlText("رقم: ${receipt.docNumber}", right, y, contentWidth, labelPaint()) + 4f
        y += canvas.drawRtlText(dateFormat.format(receipt.date.atZone(java.time.ZoneId.systemDefault())), right, y, contentWidth, labelPaint()) + 4f
        y += canvas.drawRtlText("العميل: ${receipt.customerName}", right, y, contentWidth, bodyPaint()) + 16f
        y += canvas.drawRtlText("استلمنا من السيد/ة أعلاه مبلغ:", right, y, contentWidth, bodyPaint()) + 8f
        y += canvas.drawRtlText(money(receipt.amount), right, y, contentWidth, titlePaint()) + 20f
        receipt.notes?.let { y += canvas.drawRtlText("ملاحظات: $it", right, y, contentWidth, bodyPaint()) + 8f }
        if (receipt.repName != null) {
            canvas.drawRtlText("المندوب: ${receipt.repName}", right, y, contentWidth, labelPaint())
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    fun generateStatementPdf(customerName: String, rows: List<LedgerRow>, outputFile: File) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        val right = pageWidth - margin
        val contentWidth = pageWidth - margin * 2
        var y = margin

        y += canvas.drawRtlText("كشف حساب", right, y, contentWidth, titlePaint()) + 8f
        y += canvas.drawRtlText(customerName, right, y, contentWidth, bodyPaint()) + 16f

        val col1 = contentWidth * 0.35f // الوصف
        val col2 = contentWidth * 0.20f // مدين
        val col3 = contentWidth * 0.20f // دائن
        val col4 = contentWidth * 0.25f // الرصيد

        y += canvas.drawRtlText("الوصف", right, y, col1, boldPaint())
        canvas.drawRtlText("مدين", right - col1, y, col2, boldPaint())
        canvas.drawRtlText("دائن", right - col1 - col2, y, col3, boldPaint())
        canvas.drawRtlText("الرصيد", right - col1 - col2 - col3, y, col4, boldPaint())
        y += 20f

        rows.forEach { row ->
            val rowHeight = canvas.drawRtlText(row.description, right, y, col1, bodyPaint())
            canvas.drawRtlText(if (row.debit > 0) money(row.debit) else "", right - col1, y, col2, bodyPaint())
            canvas.drawRtlText(if (row.credit > 0) money(row.credit) else "", right - col1 - col2, y, col3, bodyPaint())
            canvas.drawRtlText(money(row.runningBalance), right - col1 - col2 - col3, y, col4, bodyPaint())
            y += rowHeight.coerceAtLeast(18f) + 6f
            if (y > pageHeight - margin) return@forEach // TODO: real multi-page support
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    private fun Canvas.drawTotalsRow(label: String, value: Double, right: Float, y: Float, width: Float, paint: TextPaint): Float {
        val h1 = drawRtlText(label, right, y, width * 0.5f, paint)
        val h2 = drawRtlText(money(value), right - width * 0.5f, y, width * 0.5f, paint)
        return maxOf(h1, h2) + 6f
    }

    // ---- 80mm thermal-receipt layout ----
    // 80mm at 72dpi ≈ 227pt. Unlike the A4 functions above, the page height
    // here is NOT fixed — real receipt paper is a continuous roll, so every
    // page below is sized to exactly fit its own content via a two-pass
    // measure-then-draw: each `layout(canvas)` local function runs once with
    // canvas == null (StaticLayout measurement only, no page exists yet to
    // draw into) to compute the exact height needed, then once for real
    // against a page created at that height. Same content, same code path,
    // so the two passes can never disagree about how tall anything is —
    // and it naturally prints every row on one continuous page instead of
    // needing the A4 statement's page-break handling at all.
    //
    // Visual language shared by the invoice, receipt and statement: a
    // centered logo + company block over a solid rule, a centered title over
    // a dashed rule, label-right / value-left rows, bordered grid tables with
    // a shaded header row, and a dashed cream box for the customer's
    // remaining debt. Fills are pale on purpose — the thermal printer turns
    // anything lighter than its black threshold into white paper — while
    // every rule and border is solid black, so the structure survives the
    // 1-bit print.

    private val thermalPageWidth = 227
    private val thermalMargin = 10f
    private val tableHeaderFill = 0xFFF2ECDD.toInt()
    private val discountRed = 0xFFC62828.toInt()

    private fun thermalTitlePaint(scale: Float) = TextPaint().apply { textSize = 15f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun thermalLabelPaint(scale: Float) = TextPaint().apply { textSize = 10f * scale; color = 0xFF000000.toInt() }
    private fun thermalBodyPaint(scale: Float) = TextPaint().apply { textSize = 11f * scale; color = 0xFF000000.toInt() }
    private fun thermalCompanyPaint(scale: Float) = TextPaint().apply { textSize = 14f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun thermalDebtPaint(scale: Float) = TextPaint().apply { textSize = 11f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun thermalCellPaint(scale: Float) = TextPaint().apply { textSize = 10f * scale; color = 0xFF000000.toInt() }
    private fun thermalCellHeaderPaint(scale: Float) = TextPaint().apply { textSize = 10f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun thermalDiscountPaint(scale: Float) = TextPaint().apply { textSize = 11f * scale; color = discountRed }
    private fun thermalRulePaint() = Paint().apply { strokeWidth = 1f; color = 0xFF000000.toInt() }
    private fun thermalBorderPaint() = Paint().apply { strokeWidth = 1f; color = 0xFF000000.toInt(); style = Paint.Style.STROKE }

    private fun loadBitmapOrNull(path: String?): android.graphics.Bitmap? =
        path?.takeIf { it.isNotBlank() }
            ?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }

    /** A figure without the currency suffix — for table cells, where the
        column header already says what the number is and the narrow columns
        can't spare " ر.ي" on every row. */
    private fun plainNumber(value: Double): String = signedNumber(value)

    /** Whole quantities print as "3", not "3.0". */
    private fun formatQty(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** A copy of [base] shrunk just enough for [text] to fit on ONE line of
        [width] (never below 60% of the original size). Used for the short
        figure columns of the tables so a big amount like 12,345,678 shrinks
        a little instead of breaking across two lines. */
    private fun fitSingleLine(text: String, width: Float, base: TextPaint): TextPaint {
        val paint = TextPaint(base)
        val minSize = base.textSize * 0.6f
        while (paint.measureText(text) > width && paint.textSize > minSize) {
            paint.textSize -= 0.25f
        }
        return paint
    }

    /** Draws — or, with a null canvas, only measures — RTL text. The
        nullable-receiver twin of [drawRtlText], so the 80mm layouts can make
        one call in both of their passes instead of branching on
        `canvas != null` at every line. [alignment] places the text inside
        [width]: NORMAL hugs the right edge (the RTL start), OPPOSITE the
        left edge, CENTER the middle. Returns the laid-out height either way. */
    private fun Canvas?.rtlText(
        text: String,
        right: Float,
        top: Float,
        width: Float,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .build()
        if (this != null) {
            save()
            translate(right - width, top)
            layout.draw(this)
            restore()
        }
        return layout.height.toFloat()
    }

    /** Draws [bitmap] horizontally centered between [left] and [right] at [y],
        capped at [maxHeight] and [maxWidth] with its aspect ratio kept;
        returns the height used (the measurement pass still needs it). */
    private fun Canvas?.drawCenteredBitmap(
        bitmap: android.graphics.Bitmap,
        left: Float,
        right: Float,
        y: Float,
        maxHeight: Float,
        maxWidth: Float,
    ): Float {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        var height = bitmap.height.toFloat().coerceAtMost(maxHeight)
        var width = height * ratio
        if (width > maxWidth) {
            width = maxWidth
            height = width / ratio
        }
        if (this != null) {
            val x = (left + right - width) / 2f
            drawBitmap(bitmap, null, android.graphics.RectF(x, y, x + width, y + height), null)
        }
        return height
    }

    /** Dashes are stepped by hand (dash / gap loop) instead of using
        Paint.pathEffect, so they render identically on every device and
        survive the PDF → raster → thermal pipeline. */
    private fun Canvas?.drawDashedHLine(x1: Float, x2: Float, y: Float, paint: Paint, dash: Float = 3f, gap: Float = 2.5f) {
        if (this == null) return
        var x = x1
        while (x < x2) {
            drawLine(x, y, (x + dash).coerceAtMost(x2), y, paint)
            x += dash + gap
        }
    }

    private fun Canvas?.drawDashedVLine(x: Float, y1: Float, y2: Float, paint: Paint, dash: Float = 3f, gap: Float = 2.5f) {
        if (this == null) return
        var y = y1
        while (y < y2) {
            drawLine(x, y, x, (y + dash).coerceAtMost(y2), paint)
            y += dash + gap
        }
    }

    private fun Canvas?.strokeDashedRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        this.drawDashedHLine(left, right, top, paint)
        this.drawDashedHLine(left, right, bottom, paint)
        this.drawDashedVLine(left, top, bottom, paint)
        this.drawDashedVLine(right, top, bottom, paint)
    }

    private fun Canvas?.fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Int) {
        if (this == null) return
        drawRect(left, top, right, bottom, Paint().apply { color = fill; style = Paint.Style.FILL })
    }

    /** One "label on the right, value on the left" line, the building block of
        the info block and the totals block. The label sits at the RTL start
        (right edge) inside [labelFraction] of [width]; the value is pushed to
        the opposite (left) edge of what remains. Returns the taller of the two. */
    private fun Canvas?.drawLabelValueRow(
        label: String,
        value: String,
        right: Float,
        top: Float,
        width: Float,
        labelStyle: TextPaint,
        valueStyle: TextPaint,
        labelFraction: Float = 0.5f,
    ): Float {
        val labelWidth = width * labelFraction
        val valueWidth = width - labelWidth
        val labelHeight = this.rtlText(label, right, top, labelWidth, labelStyle)
        val valueHeight = this.rtlText(value, right - labelWidth, top, valueWidth, valueStyle, Layout.Alignment.ALIGN_OPPOSITE)
        return maxOf(labelHeight, valueHeight)
    }

    /** A bordered RTL data table: a shaded header row plus one row per entry
        of [rowCells], with a solid grid between every row and column.
        [headers] and [colFractions] are in RTL reading order — index 0 is the
        rightmost, widest column (item name / description), which wraps across
        as many lines as it needs; the remaining columns are short figures and
        are centered. Row heights are measured up front, so the vertical grid
        lines can span the whole table in one stroke and the caller gets the
        exact height back in both layout passes. Shared by the invoice items
        table and the statement ledger, which are the same shape. */
    private fun Canvas?.drawGridTable(
        headers: List<String>,
        colFractions: List<Float>,
        rowCells: List<List<String>>,
        right: Float,
        top: Float,
        contentWidth: Float,
        scale: Float,
    ): Float {
        val canvas: Canvas? = this
        val measure: Canvas? = null
        val columns = headers.size
        val pad = 3f
        val headerStyle = thermalCellHeaderPaint(scale)
        val cellStyle = thermalCellPaint(scale)
        val borderPaint = thermalBorderPaint()
        val rowRulePaint = Paint().apply { strokeWidth = 0.6f; color = 0xFF000000.toInt() }

        val colWidths = colFractions.map { it * contentWidth }
        val colRights = ArrayList<Float>(columns)
        var edge = right
        for (w in colWidths) {
            colRights.add(edge)
            edge -= w
        }
        val left = edge

        fun alignmentFor(col: Int) =
            if (col == 0) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

        // Column 0 (name / description) wraps freely; every other column is a
        // short figure or label and is fitted to a single line instead.
        fun styleFor(col: Int, text: String, base: TextPaint) =
            if (col == 0) base else fitSingleLine(text, colWidths[col] - pad * 2, base)

        val headerHeight = (0 until columns).maxOf { i ->
            measure.rtlText(headers[i], colRights[i] - pad, 0f, colWidths[i] - pad * 2, styleFor(i, headers[i], headerStyle), Layout.Alignment.ALIGN_CENTER)
        } + pad * 2
        val rowHeights = rowCells.map { cells ->
            (0 until columns).maxOf { i ->
                measure.rtlText(cells[i], colRights[i] - pad, 0f, colWidths[i] - pad * 2, styleFor(i, cells[i], cellStyle), alignmentFor(i))
            } + pad * 2
        }
        val bottom = top + headerHeight + rowHeights.sum()

        canvas.fillRect(left, top, right, top + headerHeight, tableHeaderFill)
        canvas?.drawRect(left, top, right, bottom, borderPaint)
        canvas?.drawLine(left, top + headerHeight, right, top + headerHeight, borderPaint)
        for (i in 1 until columns) {
            canvas?.drawLine(colRights[i], top, colRights[i], bottom, borderPaint)
        }
        for (i in 0 until columns) {
            canvas.rtlText(headers[i], colRights[i] - pad, top + pad, colWidths[i] - pad * 2, styleFor(i, headers[i], headerStyle), Layout.Alignment.ALIGN_CENTER)
        }

        var rowTop = top + headerHeight
        for (r in rowCells.indices) {
            val cells = rowCells[r]
            for (i in 0 until columns) {
                canvas.rtlText(cells[i], colRights[i] - pad, rowTop + pad, colWidths[i] - pad * 2, styleFor(i, cells[i], cellStyle), alignmentFor(i))
            }
            rowTop += rowHeights[r]
            if (r < rowCells.lastIndex) {
                canvas?.drawLine(left, rowTop, right, rowTop, rowRulePaint)
            }
        }
        return bottom - top
    }

    /** The dashed cream "remaining debt" highlight — label on the right, figure
        on the left, inside a padded dashed box. Shared by all three documents. */
    private fun Canvas?.drawDebtBox(label: String, value: String, top: Float, scale: Float): Float {
        val measure: Canvas? = null
        val left = thermalMargin
        val right = thermalPageWidth - thermalMargin
        val pad = 7f
        val innerWidth = right - left - pad * 2
        val style = thermalDebtPaint(scale)
        val rowHeight = measure.drawLabelValueRow(label, value, right - pad, 0f, innerWidth, style, style, labelFraction = 0.6f)
        val boxHeight = rowHeight + pad * 2
        this.fillRect(left, top, right, top + boxHeight, tableHeaderFill)
        this.strokeDashedRect(left, top, right, top + boxHeight, thermalRulePaint())
        this.drawLabelValueRow(label, value, right - pad, top + pad, innerWidth, style, style, labelFraction = 0.6f)
        return boxHeight
    }

    /** Shared opening block: centered logo, company name, address and phone,
        then a solid rule. Returns the y where the next block starts. */
    private fun drawHeader80(
        canvas: Canvas?,
        startY: Float,
        company: CompanySettings,
        logo: android.graphics.Bitmap?,
        scale: Float,
        lineGap: Float,
        contentWidth: Float,
    ): Float {
        val right = thermalPageWidth - thermalMargin
        var y = startY
        if (logo != null) {
            y += canvas.drawCenteredBitmap(logo, thermalMargin, right, y, 60f, contentWidth * 0.7f) + 6f
        }
        if (company.companyName.isNotBlank()) {
            y += canvas.rtlText(company.companyName, right, y, contentWidth, thermalCompanyPaint(scale), Layout.Alignment.ALIGN_CENTER) + 3f + lineGap
        }
        if (company.companyAddress.isNotBlank()) {
            y += canvas.rtlText(company.companyAddress, right, y, contentWidth, thermalLabelPaint(scale), Layout.Alignment.ALIGN_CENTER) + 3f + lineGap
        }
        if (company.companyPhone.isNotBlank()) {
            y += canvas.rtlText("هاتف: ${company.companyPhone}", right, y, contentWidth, thermalLabelPaint(scale), Layout.Alignment.ALIGN_CENTER) + 3f + lineGap
        }
        y += 4f
        canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
        return y + 8f
    }

    /** Centered document title over a dashed rule. */
    private fun drawTitle80(canvas: Canvas?, startY: Float, title: String, scale: Float, contentWidth: Float): Float {
        val right = thermalPageWidth - thermalMargin
        var y = startY
        y += canvas.rtlText(title, right, y, contentWidth, thermalTitlePaint(scale), Layout.Alignment.ALIGN_CENTER) + 6f
        canvas.drawDashedHLine(thermalMargin, right, y, thermalRulePaint())
        return y + 8f
    }

    fun generateInvoicePdf80mm(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        company: CompanySettings,
        customerPhone: String?,
        customerAddress: String?,
        outputFile: File,
    ) {
        val contentWidth = thermalPageWidth - thermalMargin * 2
        val right = thermalPageWidth - thermalMargin
        val scale = company.printFontScale.takeIf { it > 0f } ?: 1.0f
        val lineGap = company.printLineSpacingExtra.coerceAtLeast(0f)
        val logo = loadBitmapOrNull(company.companyLogoPath)
        val signature = loadBitmapOrNull(invoice.signaturePath)

        // Kind and payment mode read as one title, e.g. "فاتورة مبيعات آجل".
        val kindWord = if (invoice.kind == InvoiceKind.SALE) "مبيعات" else "مرتجع"
        val modeWord = if (invoice.paymentMode == PaymentMode.CASH) "نقدية" else "آجل"
        val title = "فاتورة $kindWord $modeWord"

        fun layout(canvas: Canvas?): Float {
            var y = drawHeader80(canvas, thermalMargin, company, logo, scale, lineGap, contentWidth)
            y = drawTitle80(canvas, y, title, scale, contentWidth)

            fun infoRow(label: String, value: String) {
                y += canvas.drawLabelValueRow(
                    label, value, right, y, contentWidth,
                    thermalLabelPaint(scale), thermalBodyPaint(scale), labelFraction = 0.36f,
                ) + 4f + lineGap
            }
            infoRow("رقم الفاتورة", invoice.docNumber)
            infoRow("اسم العميل", invoice.customerName)
            infoRow("طريقة الدفع", if (invoice.paymentMode == PaymentMode.CASH) "نقدًا" else "آجل")
            infoRow("التاريخ", dateTimeFormat.format(invoice.date.atZone(java.time.ZoneId.systemDefault())))
            if (!customerPhone.isNullOrBlank()) infoRow("هاتف العميل", customerPhone)
            if (!customerAddress.isNullOrBlank()) infoRow("المنطقة", customerAddress)
            val repLabel = company.repDisplayName.ifBlank { invoice.repName.orEmpty() }
            if (repLabel.isNotBlank()) infoRow("المندوب", repLabel)
            y += 2f
            canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
            y += 8f

            val itemRows = items.map { item ->
                listOf(
                    item.productName,
                    formatQty(item.quantity),
                    plainNumber(item.price),
                    plainNumber(item.price * item.quantity),
                )
            }
            y += canvas.drawGridTable(
                headers = listOf("الصنف", "كمية", "سعر", "الاجمالي"),
                colFractions = listOf(0.42f, 0.14f, 0.20f, 0.24f),
                rowCells = itemRows,
                right = right,
                top = y,
                contentWidth = contentWidth,
                scale = scale,
            ) + 8f

            val totals = computeInvoiceTotals(invoice, items)
            fun totalRow(label: String, value: String, labelStyle: TextPaint, valueStyle: TextPaint, gapAfter: Float = 4f) {
                y += canvas.drawLabelValueRow(label, value, right, y, contentWidth, labelStyle, valueStyle) + gapAfter + lineGap
            }
            totalRow("الإجمالي الفرعي", money(totals.subTotal), thermalBodyPaint(scale), thermalBodyPaint(scale))
            if (totals.discountValue > 0) {
                totalRow("الخصم", "- ${money(totals.discountValue)}", thermalBodyPaint(scale), thermalDiscountPaint(scale))
            }
            y += 2f
            canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
            y += 6f
            totalRow("الاجمالي", money(totals.grandTotal), thermalTitlePaint(scale), thermalTitlePaint(scale), gapAfter = 10f)

            y += canvas.drawDebtBox("إجمالي المديونية المتبقية", money(invoice.balanceAfter), y, scale) + 10f

            if (signature != null) {
                y += canvas.rtlText("توقيع العميل بالاستلام", right, y, contentWidth, thermalLabelPaint(scale), Layout.Alignment.ALIGN_CENTER) + 4f
                y += canvas.drawCenteredBitmap(signature, thermalMargin, right, y, 50f, contentWidth * 0.6f) + 6f
            }

            if (company.invoiceFooterText.isNotBlank()) {
                canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
                y += 6f
                y += canvas.rtlText(company.invoiceFooterText, right, y, contentWidth, thermalBodyPaint(scale), Layout.Alignment.ALIGN_CENTER) + 4f
            }

            return y + thermalMargin
        }

        val totalHeight = layout(null).toInt().coerceAtLeast(200)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(thermalPageWidth, totalHeight, 1).create())
        layout(page.canvas)
        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    fun generateReceiptPdf80mm(receipt: ReceiptEntity, company: CompanySettings, outputFile: File) {
        val contentWidth = thermalPageWidth - thermalMargin * 2
        val right = thermalPageWidth - thermalMargin
        val scale = company.printFontScale.takeIf { it > 0f } ?: 1.0f
        val lineGap = company.printLineSpacingExtra.coerceAtLeast(0f)
        val logo = loadBitmapOrNull(company.companyLogoPath)
        val signature = loadBitmapOrNull(receipt.repSignaturePath)

        fun layout(canvas: Canvas?): Float {
            var y = drawHeader80(canvas, thermalMargin, company, logo, scale, lineGap, contentWidth)
            y = drawTitle80(canvas, y, "سند قبض", scale, contentWidth)

            fun infoRow(label: String, value: String) {
                y += canvas.drawLabelValueRow(
                    label, value, right, y, contentWidth,
                    thermalLabelPaint(scale), thermalBodyPaint(scale), labelFraction = 0.36f,
                ) + 4f + lineGap
            }
            infoRow("رقم السند", receipt.docNumber)
            infoRow("اسم العميل", receipt.customerName)
            infoRow("التاريخ", dateTimeFormat.format(receipt.date.atZone(java.time.ZoneId.systemDefault())))
            val repLabel = company.repDisplayName.ifBlank { receipt.repName.orEmpty() }
            if (repLabel.isNotBlank()) infoRow("المندوب", repLabel)
            y += 2f
            canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
            y += 8f

            y += canvas.rtlText("استلمنا من السيد/ة أعلاه مبلغ:", right, y, contentWidth, thermalBodyPaint(scale), Layout.Alignment.ALIGN_CENTER) + 4f + lineGap
            y += canvas.rtlText(money(receipt.amount), right, y, contentWidth, thermalTitlePaint(scale), Layout.Alignment.ALIGN_CENTER) + 10f

            receipt.notes?.takeIf { it.isNotBlank() }?.let { infoRow("ملاحظات", it) }
            y += 4f

            y += canvas.drawDebtBox("إجمالي المديونية المتبقية", money(receipt.balanceAfter), y, scale) + 10f

            if (signature != null) {
                y += canvas.rtlText("توقيع المندوب", right, y, contentWidth, thermalLabelPaint(scale), Layout.Alignment.ALIGN_CENTER) + 4f
                y += canvas.drawCenteredBitmap(signature, thermalMargin, right, y, 50f, contentWidth * 0.6f) + 6f
            }

            if (company.invoiceFooterText.isNotBlank()) {
                canvas?.drawLine(thermalMargin, y, right, y, thermalRulePaint())
                y += 6f
                y += canvas.rtlText(company.invoiceFooterText, right, y, contentWidth, thermalBodyPaint(scale), Layout.Alignment.ALIGN_CENTER) + 4f
            }

            return y + thermalMargin
        }

        val totalHeight = layout(null).toInt().coerceAtLeast(200)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(thermalPageWidth, totalHeight, 1).create())
        layout(page.canvas)
        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    fun generateStatementPdf80mm(customerName: String, rows: List<LedgerRow>, company: CompanySettings, outputFile: File) {
        val contentWidth = thermalPageWidth - thermalMargin * 2
        val right = thermalPageWidth - thermalMargin
        val scale = company.printFontScale.takeIf { it > 0f } ?: 1.0f
        val lineGap = company.printLineSpacingExtra.coerceAtLeast(0f)
        val logo = loadBitmapOrNull(company.companyLogoPath)

        fun layout(canvas: Canvas?): Float {
            var y = drawHeader80(canvas, thermalMargin, company, logo, scale, lineGap, contentWidth)
            y = drawTitle80(canvas, y, "كشف حساب", scale, contentWidth)

            y += canvas.drawLabelValueRow(
                "اسم العميل", customerName, right, y, contentWidth,
                thermalLabelPaint(scale), thermalBodyPaint(scale), labelFraction = 0.36f,
            ) + 8f + lineGap

            // Blank cell where a row has no debit / no credit — the grid already
            // shows it's empty, and a dash on every row is just noise.
            val ledgerRows = rows.map { row ->
                listOf(
                    row.description,
                    if (row.debit > 0) plainNumber(row.debit) else "",
                    if (row.credit > 0) plainNumber(row.credit) else "",
                    plainNumber(row.runningBalance),
                )
            }
            y += canvas.drawGridTable(
                headers = listOf("الوصف", "مدين", "دائن", "الرصيد"),
                colFractions = listOf(0.31f, 0.23f, 0.23f, 0.23f),
                rowCells = ledgerRows,
                right = right,
                top = y,
                contentWidth = contentWidth,
                scale = scale,
            ) + 10f

            y += canvas.drawDebtBox("إجمالي المديونية المتبقية", money(rows.lastOrNull()?.runningBalance ?: 0.0), y, scale) + 10f

            return y + thermalMargin
        }

        val totalHeight = layout(null).toInt().coerceAtLeast(200)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(thermalPageWidth, totalHeight, 1).create())
        layout(page.canvas)
        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }
}
