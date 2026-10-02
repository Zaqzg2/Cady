package com.cady.cadysalesapp.data.printing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cady.cadysalesapp.data.repository.PrintSpeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Higher-level printing logic ported from print_service_io.dart, sitting on
 * top of BluetoothPrinterBridge's raw socket layer. Reliability layers carried
 * over from that file's own reasoning (see its header comment for the real
 * hardware failures each one fixes):
 * 1. Paced writes — a full invoice image can be tens of KB, and sending it in
 *    one shot overflows cheap printers' buffers. The pacing is now a choice
 *    ([PrintSpeed]) because too slow is a failure too: a printer that is fed
 *    slower than it prints stops and starts, and leaves pale streaks.
 * 2. A verified write, not just a connection-status check — write for real,
 *    and if it fails despite "being connected", force a full reconnect and
 *    retry once before giving up.
 * 3. A step-by-step diagnostic log with millisecond timestamps, so a failure
 *    on a specific device is actually diagnosable instead of a single opaque
 *    "printing failed" message.
 * 4. Raster bands are cut only inside blank paper ([RasterBands]), so the
 *    unavoidable pause between two raster commands never lands on ink.
 * 5. The Bluetooth connection is opened while the picture is still being
 *    prepared (and ahead of time, from the preview screen — see [warmUp]), so
 *    the paper starts moving right after the tap instead of after the connect.
 */
@Singleton
class ThermalPrintService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: BluetoothPrinterBridge,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        /** Darker than the old 175: thin Arabic strokes survive the 1-bit conversion. */
        const val DEFAULT_BLACK_THRESHOLD = 195

        /** 80mm paper prints 72mm of it: 72mm × 8 dots/mm at 203dpi. */
        private const val PRINT_WIDTH_DOTS = 576
    }

    private val printerMacKey = stringPreferencesKey("thermal_printer_mac")

    val savedPrinterMac: Flow<String?> = dataStore.data.map { it[printerMacKey] }

    suspend fun savePrinterMac(mac: String) {
        dataStore.edit { it[printerMacKey] = mac }
    }

    private val log = mutableListOf<String>()
    var lastError: String? = null; private set

    /** A snapshot — the connection may be logging from another coroutine while this is read. */
    val lastAttemptLog: List<String> get() = synchronized(log) { log.toList() }
    private var attemptStartMs = 0L

    /** Only one coroutine may open, close or re-open the socket at a time. */
    private val connectionMutex = Mutex()

    private fun resetLog() {
        synchronized(log) { log.clear() }
        lastError = null
        attemptStartMs = System.currentTimeMillis()
    }

    private fun logStep(message: String) {
        synchronized(log) { log += "+${System.currentTimeMillis() - attemptStartMs}ms  $message" }
    }

    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyArray() // pre-Android 12: BLUETOOTH/BLUETOOTH_ADMIN are install-time, no runtime prompt needed
        }

    fun hasBluetoothPermission(): Boolean =
        requiredPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    suspend fun pairedDevices(): List<BluetoothPrinterBridge.PairedDevice> = bridge.pairedDevices()

    /**
     * Opens the saved printer's connection ahead of the tap on "print" (called when
     * the receipt preview opens), so the tap doesn't pay for the Bluetooth connect.
     * Never throws and never reports: if it fails, the real print simply tries again
     * and reports properly.
     */
    suspend fun warmUp() {
        try {
            if (!hasBluetoothPermission()) return
            val mac = savedPrinterMac.first()
            if (mac.isNullOrEmpty()) return
            withContext(Dispatchers.IO) { ensureConnected(mac) }
        } catch (e: Exception) {
            // Silent on purpose — see above.
        }
    }

    /** Is the socket live — and if not, and there is a saved address, open it. */
    private suspend fun ensureConnected(printerMac: String?): Boolean = connectionMutex.withLock {
        var connected = bridge.isConnected()
        logStep("isConnected before attempt: $connected")
        if (!connected && !printerMac.isNullOrEmpty()) {
            logStep("Connecting to $printerMac...")
            connected = try {
                bridge.connect(printerMac)
            } catch (e: Exception) {
                logStep("connect() threw: ${e.message}")
                false
            }
            logStep("connect() result: $connected")
            if (connected) delay(300) // brief settle time some cheap printers need after a fresh connect
        }
        connected
    }

    /** Writes in paced chunks (see [PrintSpeed]) and logs the real throughput, so two
        speeds can be compared from the diagnostic log and not only by eye. */
    private suspend fun writeChunked(bytes: ByteArray, speed: PrintSpeed): Boolean {
        val chunkSize = speed.chunkSize
        val startedAt = System.currentTimeMillis()
        if (bytes.size <= chunkSize) {
            val ok = bridge.writeBytes(bytes, 0, bytes.size)
            logStep("Wrote ${bytes.size} bytes (single chunk): ${if (ok) "ok" else "failed"}")
            return ok
        }
        var offset = 0
        var chunkIndex = 0
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
        while (offset < bytes.size) {
            val length = minOf(chunkSize, bytes.size - offset)
            chunkIndex++
            val ok = try {
                bridge.writeBytes(bytes, offset, length)
            } catch (e: Exception) {
                logStep("Exception writing chunk $chunkIndex/$totalChunks: ${e.message}")
                return false
            }
            if (!ok) {
                logStep("Chunk $chunkIndex/$totalChunks failed at byte $offset")
                return false
            }
            offset += length
            if (speed.delayMs > 0) delay(speed.delayMs)
        }
        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1)
        logStep(
            "Wrote ${bytes.size} bytes in $totalChunks chunks (${speed.name}): all ok ✓ — " +
                "$elapsedMs ms, about ${bytes.size / elapsedMs} KB/s"
        )
        return true
    }

    /** Connect-if-needed, write, and on failure force a full reconnect + retry
        once — matches _writeVerified exactly. */
    private suspend fun writeVerified(bytes: ByteArray, printerMac: String?, speed: PrintSpeed): Boolean {
        val connected = ensureConnected(printerMac)
        if (!connected) {
            logStep("Final failure: no live connection and no saved printer address to retry")
            lastError = "لا يوجد اتصال بالطابعة"
            return false
        }

        var ok = writeChunked(bytes, speed)
        if (!ok && !printerMac.isNullOrEmpty()) {
            logStep("Write failed despite an apparently \"connected\" state — the socket was silently dead. Forcing a full reconnect as a last attempt...")
            val reconnected = connectionMutex.withLock {
                try { bridge.disconnect() } catch (e: Exception) { logStep("Exception during disconnect(): ${e.message}") }
                val result = try {
                    bridge.connect(printerMac)
                } catch (e: Exception) {
                    logStep("connect() threw: ${e.message}")
                    false
                }
                logStep("Reconnect result: $result")
                if (result) delay(300)
                result
            }
            if (reconnected) ok = writeChunked(bytes, speed)
        }
        if (!ok) lastError = "فشلت الكتابة على المقبس رغم محاولات إعادة الاتصال"
        logStep(if (ok) "✅ Final write succeeded" else "❌ Final write failed")
        return ok
    }

    /** ESC @ — silent init, prints/feeds nothing, used only to prove a real write succeeds. */
    private val pingBytes = byteArrayOf(0x1B, 0x40)

    suspend fun verifyConnection(printerMac: String?): Boolean {
        resetLog()
        logStep("— starting real connection verification —")
        return writeVerified(pingBytes, printerMac, PrintSpeed.SAFE)
    }

    /** The highest threshold ever applied, whatever the saved setting says.
        The page background is forced to pure white (255) and the app's pale
        fills (table header, debt box) sit around 236, so a threshold at or
        above that turns blank paper — or the whole receipt — solid black.
        The settings slider used to run all the way to 255, where every pixel
        counts as "dark"; a printout that is one solid black block is exactly
        what that produces. */
    private val maxEffectiveThreshold = 230

    /** A real receipt is mostly white paper (text and rules are a small
        fraction of the area). If more than this share of the picture came
        out black, something upstream is wrong — better to say so than to
        burn a metre of paper and the battery on a black page. */
    private val maxPlausibleBlackRatio = 0.6

    /** The picture reduced to one bit per dot, row by row, plus how much ink each row has. */
    private class PackedRaster(
        val widthPx: Int,
        val rowBytes: Int,
        val height: Int,
        val bits: ByteArray,
        val inkPerRow: IntArray,
        val blackCount: Long,
    ) {
        val blackRatio: Double
            get() {
                val total = widthPx.toLong() * height.toLong()
                return if (total == 0L) 0.0 else blackCount.toDouble() / total
            }
    }

    /**
     * Fixed-threshold black/white (no dithering) — matches _buildRasterCommand's
     * reasoning: dithering is great for photos but makes anti-aliased text edges
     * look faded at 203dpi, whereas a hard threshold makes text bolder and clearer,
     * which is what an invoice/receipt actually needs.
     *
     * Read in strips of 64 rows rather than all at once: a long customer statement
     * is thousands of rows, and a whole-picture pixel array of that size is what
     * runs a low-memory phone out of heap.
     */
    private fun thresholdToBits(bitmap: Bitmap, threshold: Int): PackedRaster {
        val width = bitmap.width
        val height = bitmap.height
        val rowBytes = (width + 7) / 8
        val bits = ByteArray(rowBytes * height)
        val inkPerRow = IntArray(height)
        val stripRows = 64
        val strip = IntArray(width * stripRows)
        var black = 0L

        var firstRow = 0
        while (firstRow < height) {
            val rows = minOf(stripRows, height - firstRow)
            bitmap.getPixels(strip, 0, width, 0, firstRow, width, rows)
            for (r in 0 until rows) {
                val y = firstRow + r
                val pixelBase = r * width
                val byteBase = y * rowBytes
                var rowInk = 0
                for (x in 0 until width) {
                    val px = strip[pixelBase + x]
                    // Integer form of 0.299 R + 0.587 G + 0.114 B (weights sum to 256).
                    val gray = (77 * Color.red(px) + 150 * Color.green(px) + 29 * Color.blue(px)) shr 8
                    if (gray <= threshold) {
                        val i = byteBase + (x shr 3)
                        bits[i] = (bits[i].toInt() or (0x80 shr (x and 7))).toByte()
                        rowInk++
                    }
                }
                inkPerRow[y] = rowInk
                black += rowInk
            }
            firstRow += rows
        }
        return PackedRaster(width, rowBytes, height, bits, inkPerRow, black)
    }

    /** ESC @, then the picture as back-to-back GS v 0 bands cut at [cuts], a short
        feed, and a partial cut. */
    private fun buildPayload(raster: PackedRaster, cuts: List<Int>): ByteArray {
        val out = ByteArrayOutputStream(raster.bits.size + 64)
        out.write(byteArrayOf(0x1B, 0x40))
        var start = 0
        for (end in cuts) {
            val bandHeight = end - start
            out.write(
                byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    (raster.rowBytes and 0xFF).toByte(), ((raster.rowBytes shr 8) and 0xFF).toByte(),
                    (bandHeight and 0xFF).toByte(), ((bandHeight shr 8) and 0xFF).toByte(),
                )
            )
            out.write(raster.bits, start * raster.rowBytes, bandHeight * raster.rowBytes)
            start = end
        }
        out.write(byteArrayOf(0x1B, 0x64, 0x02))
        out.write(byteArrayOf(0x1D, 0x56, 0x01))
        return out.toByteArray()
    }

    /**
     * Renders the PDF's first page at 80mm/203dpi (≈576px wide, same target
     * as the Flutter version), flattens onto an explicit white background
     * (defends against a transparent decode reading as black).
     */
    private fun rasterizePdf(pdfFile: File): Bitmap =
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val scale = PRINT_WIDTH_DOTS.toFloat() / page.width
                    val targetHeightPx = (page.height * scale).toInt()
                    val raw = Bitmap.createBitmap(PRINT_WIDTH_DOTS, targetHeightPx, Bitmap.Config.ARGB_8888)
                    Canvas(raw).drawColor(Color.WHITE)
                    // Same render mode as the on-screen preview, which is the one
                    // known to come out right on the real device (the preview looks
                    // fine while the print did not, and FOR_PRINT was the untested
                    // difference).
                    page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    // Then flatten onto a fresh white bitmap with real alpha
                    // blending. The threshold reads only the RGB channels, so any
                    // pixel the renderer left transparent (RGB 0,0,0) would count
                    // as black; the preview never shows that because a transparent
                    // pixel just displays the screen behind it.
                    val flat = Bitmap.createBitmap(PRINT_WIDTH_DOTS, targetHeightPx, Bitmap.Config.ARGB_8888)
                    Canvas(flat).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(raw, 0f, 0f, null)
                    }
                    raw.recycle()
                    flat
                }
            }
        }

    /** Threshold → bands cut in blank paper → ESC/POS payload → send. The connection
        is already being opened in [connection] and is awaited only once the payload is ready. */
    private suspend fun sendBitmap(
        bitmap: Bitmap,
        printerMac: String?,
        blackThreshold: Int,
        speed: PrintSpeed,
        connection: Deferred<Boolean>,
    ): Boolean {
        val threshold = blackThreshold.coerceIn(0, maxEffectiveThreshold)
        logStep("Black threshold: saved=$blackThreshold, used=$threshold; speed=${speed.name}")

        val built = try {
            val packed = thresholdToBits(bitmap, threshold)
            val bandCuts = RasterBands.cuts(packed.inkPerRow)
            Triple(packed, bandCuts, buildPayload(packed, bandCuts))
        } catch (e: Exception) {
            logStep("❌ Failed to build ESC/POS command: ${e.message}")
            lastError = "فشل تجهيز بيانات الطباعة"
            return false
        }
        val raster = built.first
        val cuts = built.second
        val payload = built.third
        logStep("Black pixels: ${"%.1f".format(raster.blackRatio * 100)}% of the picture")
        logStep("Final ESC/POS payload size: ${payload.size} bytes (${cuts.size} raster band(s), cut at blank rows: $cuts)")

        if (raster.blackRatio > maxPlausibleBlackRatio) {
            logStep("❌ Refusing to print: ${"%.0f".format(raster.blackRatio * 100)}% of the picture is black — a receipt is mostly white")
            lastError = "الصورة الناتجة سوداء تقريبًا بالكامل — أوقفت الطباعة حتى لا تُهدر الورق"
            return false
        }

        if (!connection.await()) {
            logStep("Final failure: could not open the printer connection")
            lastError = "لا يوجد اتصال بالطابعة"
            return false
        }
        return writeVerified(payload, printerMac, speed)
    }

    /**
     * Rasterizes the PDF and prints it as a raster image rather than ESC/POS
     * text — thermal-printer codepages don't shape Arabic correctly, so the PDF
     * (already laid out correctly via StaticLayout) is what actually gets sent,
     * just as a picture.
     */
    suspend fun printPdf(
        pdfFile: File,
        printerMac: String?,
        blackThreshold: Int = DEFAULT_BLACK_THRESHOLD,
        speed: PrintSpeed = PrintSpeed.BALANCED,
    ): Boolean = withContext(Dispatchers.IO) {
        resetLog()
        logStep("— starting print (PDF: ${pdfFile.length()} bytes) —")
        coroutineScope {
            // The Bluetooth connect (often the slowest step) runs while the picture is prepared.
            val connection = async { ensureConnected(printerMac) }
            val bitmap = try {
                rasterizePdf(pdfFile)
            } catch (e: Exception) {
                logStep("❌ Failed to rasterize PDF: ${e.message}")
                lastError = "فشل تحويل المستند إلى صورة قابلة للطباعة"
                return@coroutineScope false
            }
            logStep("Rasterized PDF to ${bitmap.width}×${bitmap.height}")
            try {
                sendBitmap(bitmap, printerMac, blackThreshold, speed, connection)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Convenience wrapper for the common case (the settings screen's saved
        printer) so callers don't need to read savedPrinterMac themselves first. */
    suspend fun printPdfUsingSavedPrinter(
        pdfFile: File,
        blackThreshold: Int = DEFAULT_BLACK_THRESHOLD,
        speed: PrintSpeed = PrintSpeed.BALANCED,
    ): Boolean {
        val mac = savedPrinterMac.first()
        if (mac.isNullOrEmpty()) {
            resetLog()
            logStep("No saved printer MAC address")
            lastError = "لم يتم اختيار طابعة بعد — افتح إعدادات الطباعة"
            return false
        }
        return printPdf(pdfFile, mac, blackThreshold, speed)
    }

    /**
     * A built-in test page, about 9cm long: rules of five thicknesses, a solid black bar,
     * text, and a dense area of fine lines (the hardest thing for a printer that is being
     * fed too slowly — it shows as stops, pale streaks or missing lines). It goes through
     * exactly the same path as a real receipt, so printing it at each [PrintSpeed] shows
     * in a minute which one suits this printer.
     */
    suspend fun printTestPage(
        printerMac: String?,
        blackThreshold: Int,
        speed: PrintSpeed,
    ): Boolean = withContext(Dispatchers.IO) {
        resetLog()
        logStep("— starting test page —")
        coroutineScope {
            val connection = async { ensureConnected(printerMac) }
            val bitmap = renderTestPage()
            try {
                sendBitmap(bitmap, printerMac, blackThreshold, speed, connection)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun renderTestPage(): Bitmap {
        val width = PRINT_WIDTH_DOTS
        val height = 760
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val right = width - 24f
        var y = 14f

        text.textAlign = Paint.Align.CENTER
        text.textSize = 30f
        text.isFakeBoldText = true
        canvas.drawText("صفحة اختبار الطباعة", width / 2f, y + 30f, text)
        y += 52f
        text.isFakeBoldText = false
        text.textSize = 20f
        canvas.drawText("اختبار السرعة والوضوح", width / 2f, y + 20f, text)
        y += 40f

        // Rules 1, 2, 3, 4 and 6 dots thick — which of them survive, and how dark they come out.
        for (thickness in intArrayOf(1, 2, 3, 4, 6)) {
            canvas.drawRect(24f, y, right, y + thickness, ink)
            y += thickness + 10f
        }

        // A full-width black bar: the heaviest load on the print head.
        canvas.drawRect(0f, y, width.toFloat(), y + 36f, ink)
        y += 36f + 12f

        text.textAlign = Paint.Align.RIGHT
        text.textSize = 22f
        canvas.drawText("غسيل صحون بالرمان 5 لتر", right, y + 22f, text)
        y += 36f
        text.textSize = 26f
        canvas.drawText("الإجمالي 1,700 ر.ي", right, y + 26f, text)
        y += 40f
        text.textSize = 18f
        canvas.drawText("0123456789  1,234,567", right, y + 18f, text)
        y += 34f

        // Dense fine lines — a 2-dot line every 8 dots — to the end of the page.
        while (y < height - 20f) {
            canvas.drawRect(24f, y, right, y + 2f, ink)
            y += 8f
        }
        return bitmap
    }
}
