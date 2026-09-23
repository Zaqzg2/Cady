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

    private val moneyFormat = NumberFormat.getNumberInstance(Locale("ar")).apply { maximumFractionDigits = 0 }
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")

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

    private fun money(value: Double) = "${moneyFormat.format(value)} ر.ي"

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

    private val thermalPageWidth = 227
    private val thermalMargin = 10f

    private fun thermalTitlePaint(scale: Float) = TextPaint().apply { textSize = 15f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }
    private fun thermalLabelPaint(scale: Float) = TextPaint().apply { textSize = 10f * scale; color = 0xFF000000.toInt() }
    private fun thermalBodyPaint(scale: Float) = TextPaint().apply { textSize = 11f * scale; color = 0xFF000000.toInt() }
    private fun thermalBoldPaint(scale: Float) = TextPaint().apply { textSize = 12f * scale; isFakeBoldText = true; color = 0xFF000000.toInt() }

    /** Same StaticLayout the real drawRtlText uses, minus the actual canvas
        draw call — a pure measurement so the pre-pass can size the page
        without a page/canvas existing yet. */
    private fun measureTextHeight(text: String, width: Float, paint: TextPaint): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .build()
        return layout.height.toFloat()
    }

    private fun loadBitmapOrNull(path: String?): android.graphics.Bitmap? =
        path?.takeIf { it.isNotBlank() }
            ?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }

    /** Draws [bitmap] right-aligned at [y], capped at [maxHeight], preserving
        aspect ratio; returns the height actually used (0 if canvas is null —
        measurement pass still needs the real height to advance y by). */
    private fun Canvas?.drawRightAlignedBitmap(bitmap: android.graphics.Bitmap, right: Float, y: Float, maxHeight: Float): Float {
        val height = (bitmap.height.toFloat()).coerceAtMost(maxHeight)
        val width = height * bitmap.width / bitmap.height
        if (this != null) {
            drawBitmap(
                bitmap, null,
                android.graphics.RectF(right - width, y, right, y + height),
                null,
            )
        }
        return height
    }

    fun generateInvoicePdf80mm(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        company: CompanySettings,
        customerPhone: String?,
        outputFile: File,
    ) {
        val contentWidth = thermalPageWidth - thermalMargin * 2
        val scale = company.printFontScale.takeIf { it > 0f } ?: 1.0f
        val lineGap = company.printLineSpacingExtra.coerceAtLeast(0f)
        val logo = loadBitmapOrNull(company.companyLogoPath)
        val signature = loadBitmapOrNull(invoice.signaturePath)

        fun layout(canvas: Canvas?): Float {
            var y = thermalMargin
            fun text(value: String, paint: TextPaint, gapAfter: Float = 4f) {
                val h = if (canvas != null) {
                    canvas.drawRtlText(value, thermalPageWidth - thermalMargin, y, contentWidth, paint)
                } else {
                    measureTextHeight(value, contentWidth, paint)
                }
                y += h + gapAfter + lineGap
            }

            if (logo != null) {
                y += canvas.drawRightAlignedBitmap(logo, thermalPageWidth - thermalMargin, y, 70f) + 6f
            }
            if (company.companyName.isNotBlank()) text(company.companyName, thermalBoldPaint(scale))
            if (company.companyAddress.isNotBlank()) text(company.companyAddress, thermalLabelPaint(scale))
            if (company.companyPhone.isNotBlank()) text("هاتف: ${company.companyPhone}", thermalLabelPaint(scale))
            y += 4f

            val title = if (invoice.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع"
            text(title, thermalTitlePaint(scale))
            text("رقم: ${invoice.docNumber}", thermalLabelPaint(scale))
            text(dateFormat.format(invoice.date.atZone(java.time.ZoneId.systemDefault())), thermalLabelPaint(scale))
            text(if (invoice.paymentMode == PaymentMode.CASH) "نقدًا" else "آجل", thermalLabelPaint(scale))
            text("العميل: ${invoice.customerName}", thermalBodyPaint(scale))
            if (!customerPhone.isNullOrBlank()) text("هاتف العميل: $customerPhone", thermalLabelPaint(scale))
            y += 4f

            items.forEach { item ->
                text(item.productName, thermalBodyPaint(scale), gapAfter = 1f)
                text("  ${item.quantity} × ${money(item.price)} = ${money(item.price * item.quantity)}", thermalLabelPaint(scale))
            }

            y += 4f
            if (canvas != null) {
                canvas.drawLine(thermalMargin, y, thermalPageWidth - thermalMargin, y, Paint().apply { strokeWidth = 1f; color = 0xFF000000.toInt() })
            }
            y += 8f

            val totals = computeInvoiceTotals(invoice, items)
            text("المجموع الفرعي: ${money(totals.subTotal)}", thermalBodyPaint(scale))
            if (totals.discountValue > 0) text("الخصم: ${money(totals.discountValue)}", thermalBodyPaint(scale))
            text("الإجمالي: ${money(totals.grandTotal)}", thermalBoldPaint(scale), gapAfter = 8f)
            text("الرصيد بعد هذه العملية: ${money(invoice.balanceAfter)}", thermalBoldPaint(scale), gapAfter = 8f)

            val repLabel = company.repDisplayName.ifBlank { invoice.repName.orEmpty() }
            if (repLabel.isNotBlank()) text("المندوب: $repLabel", thermalLabelPaint(scale))

            if (signature != null) {
                y += canvas.drawRightAlignedBitmap(signature, thermalPageWidth - thermalMargin, y, 50f) + 4f
            }

            if (company.invoiceFooterText.isNotBlank()) {
                y += 4f
                text(company.invoiceFooterText, thermalLabelPaint(scale))
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
        val scale = company.printFontScale.takeIf { it > 0f } ?: 1.0f
        val lineGap = company.printLineSpacingExtra.coerceAtLeast(0f)
        val logo = loadBitmapOrNull(company.companyLogoPath)
        val signature = loadBitmapOrNull(receipt.repSignaturePath)

        fun layout(canvas: Canvas?): Float {
            var y = thermalMargin
            fun text(value: String, paint: TextPaint, gapAfter: Float = 4f) {
                val h = if (canvas != null) {
                    canvas.drawRtlText(value, thermalPageWidth - thermalMargin, y, contentWidth, paint)
                } else {
                    measureTextHeight(value, contentWidth, paint)
                }
                y += h + gapAfter + lineGap
            }

            if (logo != null) {
                y += canvas.drawRightAlignedBitmap(logo, thermalPageWidth - thermalMargin, y, 70f) + 6f
            }
            if (company.companyName.isNotBlank()) text(company.companyName, thermalBoldPaint(scale))
            if (company.companyAddress.isNotBlank()) text(company.companyAddress, thermalLabelPaint(scale))
            if (company.companyPhone.isNotBlank()) text("هاتف: ${company.companyPhone}", thermalLabelPaint(scale))
            y += 4f

            text("سند قبض", thermalTitlePaint(scale))
            text("رقم: ${receipt.docNumber}", thermalLabelPaint(scale))
            text(dateFormat.format(receipt.date.atZone(java.time.ZoneId.systemDefault())), thermalLabelPaint(scale))
            text("العميل: ${receipt.customerName}", thermalBodyPaint(scale))
            y += 4f
            text("استلمنا من السيد/ة أعلاه مبلغ:", thermalBodyPaint(scale))
            text(money(receipt.amount), thermalTitlePaint(scale), gapAfter = 8f)
            text("الرصيد بعد هذه العملية: ${money(receipt.balanceAfter)}", thermalBoldPaint(scale), gapAfter = 8f)
            receipt.notes?.takeIf { it.isNotBlank() }?.let { text("ملاحظات: $it", thermalBodyPaint(scale)) }

            val repLabel = company.repDisplayName.ifBlank { receipt.repName.orEmpty() }
            if (repLabel.isNotBlank()) text("المندوب: $repLabel", thermalLabelPaint(scale))

            if (signature != null) {
                y += canvas.drawRightAlignedBitmap(signature, thermalPageWidth - thermalMargin, y, 50f) + 4f
            }

            if (company.invoiceFooterText.isNotBlank()) {
                y += 4f
                text(company.invoiceFooterText, thermalLabelPaint(scale))
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

    fun generateStatementPdf80mm(customerName: String, rows: List<LedgerRow>, outputFile: File) {
        val contentWidth = thermalPageWidth - thermalMargin * 2

        fun layout(canvas: Canvas?): Float {
            var y = thermalMargin
            fun text(value: String, paint: TextPaint, gapAfter: Float = 4f): Float {
                val h = if (canvas != null) {
                    canvas.drawRtlText(value, thermalPageWidth - thermalMargin, y, contentWidth, paint)
                } else {
                    measureTextHeight(value, contentWidth, paint)
                }
                y += h + gapAfter
                return h
            }

            text("كشف حساب", thermalTitlePaint(1f), gapAfter = 8f)
            text(customerName, thermalBodyPaint(1f), gapAfter = 8f)
            if (canvas != null) {
                canvas.drawLine(thermalMargin, y, thermalPageWidth - thermalMargin, y, Paint().apply { strokeWidth = 1f; color = 0xFF000000.toInt() })
            }
            y += 8f

            rows.forEach { row ->
                text(row.description, thermalBodyPaint(1f), gapAfter = 1f)
                val movement = when {
                    row.debit > 0 -> "مدين ${money(row.debit)}"
                    row.credit > 0 -> "دائن ${money(row.credit)}"
                    else -> ""
                }
                val secondLine = if (movement.isBlank()) {
                    "الرصيد: ${money(row.runningBalance)}"
                } else {
                    "$movement — الرصيد: ${money(row.runningBalance)}"
                }
                text(secondLine, thermalLabelPaint(1f), gapAfter = 6f)
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
}
