package com.cady.cadysalesapp.data.printing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Higher-level printing logic ported from print_service_io.dart, sitting on
 * top of BluetoothPrinterBridge's raw socket layer. Three reliability layers
 * carried over verbatim from that file's own reasoning (see its header
 * comment for the real hardware failures each one fixes):
 * 1. Chunked writes (1KB + 12ms delay) — a full invoice image can be tens of
 *    KB, and sending it in one shot overflows cheap printers' buffers.
 * 2. A verified write, not just a connection-status check — write for real,
 *    and if it fails despite "being connected", force a full reconnect and
 *    retry once before giving up.
 * 3. A step-by-step diagnostic log with millisecond timestamps, so a failure
 *    on a specific device is actually diagnosable instead of a single opaque
 *    "printing failed" message.
 */
@Singleton
class ThermalPrintService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: BluetoothPrinterBridge,
    private val dataStore: DataStore<Preferences>,
) {
    private val printerMacKey = stringPreferencesKey("thermal_printer_mac")

    val savedPrinterMac: Flow<String?> = dataStore.data.map { it[printerMacKey] }

    suspend fun savePrinterMac(mac: String) {
        dataStore.edit { it[printerMacKey] = mac }
    }

    private val log = mutableListOf<String>()
    var lastError: String? = null; private set
    val lastAttemptLog: List<String> get() = log
    private var attemptStartMs = 0L

    private fun resetLog() {
        log.clear()
        lastError = null
        attemptStartMs = System.currentTimeMillis()
    }

    private fun logStep(message: String) {
        log += "+${System.currentTimeMillis() - attemptStartMs}ms  $message"
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

    /** 1024-byte chunks with a 12ms gap — matches _writeChunked exactly. */
    private suspend fun writeChunked(bytes: ByteArray): Boolean {
        val chunkSize = 1024
        if (bytes.size <= chunkSize) {
            val ok = bridge.writeBytes(bytes)
            logStep("Wrote ${bytes.size} bytes (single chunk): ${if (ok) "ok" else "failed"}")
            return ok
        }
        var offset = 0
        var chunkIndex = 0
        val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            chunkIndex++
            val ok = try {
                bridge.writeBytes(chunk)
            } catch (e: Exception) {
                logStep("Exception writing chunk $chunkIndex/$totalChunks: ${e.message}")
                return false
            }
            if (!ok) {
                logStep("Chunk $chunkIndex/$totalChunks failed at byte $offset")
                return false
            }
            offset = end
            delay(12)
        }
        logStep("Wrote ${bytes.size} bytes in $totalChunks chunks: all ok ✓")
        return true
    }

    /** Connect-if-needed, write, and on failure force a full reconnect + retry
        once — matches _writeVerified exactly. */
    private suspend fun writeVerified(bytes: ByteArray, printerMac: String?): Boolean {
        var connected = bridge.isConnected()
        logStep("isConnected before attempt: $connected")
        if (!connected && !printerMac.isNullOrEmpty()) {
            logStep("Connecting to $printerMac...")
            connected = bridge.connect(printerMac)
            logStep("connect() result: $connected")
            if (connected) delay(300) // brief settle time some cheap printers need after a fresh connect
        }
        if (!connected) {
            logStep("Final failure: no live connection and no saved printer address to retry")
            lastError = "لا يوجد اتصال بالطابعة"
            return false
        }

        var ok = writeChunked(bytes)
        if (!ok && !printerMac.isNullOrEmpty()) {
            logStep("Write failed despite an apparently \"connected\" state — the socket was silently dead. Forcing a full reconnect as a last attempt...")
            try { bridge.disconnect() } catch (e: Exception) { logStep("Exception during disconnect(): ${e.message}") }
            val reconnected = bridge.connect(printerMac)
            logStep("Reconnect result: $reconnected")
            if (reconnected) {
                delay(300)
                ok = writeChunked(bytes)
            }
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
        return writeVerified(pingBytes, printerMac)
    }

    /**
     * GS v 0 raster command from a Bitmap, fixed-threshold black/white (no
     * dithering) — matches _buildRasterCommand exactly, including the
     * reasoning: dithering is great for photos but makes anti-aliased text
     * edges look faded at 203dpi, whereas a hard threshold makes text bolder
     * and clearer, which is what an invoice/receipt actually needs.
     */
    private fun buildRasterCommand(bitmap: Bitmap, threshold: Int): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val rowBytes = (width + 7) / 8
        val packed = ByteArray(rowBytes * height)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = pixels[y * width + x]
                val gray = 0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)
                if (gray <= threshold) {
                    val i = y * rowBytes + (x shr 3)
                    packed[i] = (packed[i].toInt() or (0x80 shr (x and 7))).toByte()
                }
            }
        }

        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (rowBytes and 0xFF).toByte(), ((rowBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte(),
        )
        return header + packed
    }

    /**
     * Renders the PDF's first page at 80mm/203dpi (≈576px wide, same target
     * as the Flutter version), flattens onto an explicit white background
     * (defends against a transparent decode reading as black), and prints it
     * as a raster image rather than ESC/POS text — thermal-printer codepages
     * don't shape Arabic correctly, so the PDF (already laid out correctly
     * via StaticLayout) is what actually gets sent, just as a picture.
     */
    suspend fun printPdf(pdfFile: File, printerMac: String?, blackThreshold: Int = 175): Boolean = withContext(Dispatchers.IO) {
        resetLog()
        logStep("— starting print (PDF: ${pdfFile.length()} bytes) —")

        val targetWidthPx = 576
        val bitmap = try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val targetHeightPx = (page.height * scale).toInt()
                        val raw = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                        // Explicit white background before rendering — same defense as
                        // the Flutter version's img.fill(...) flatten step.
                        Canvas(raw).drawColor(Color.WHITE)
                        page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        raw
                    }
                }
            }
        } catch (e: Exception) {
            logStep("❌ Failed to rasterize PDF: ${e.message}")
            lastError = "فشل تحويل المستند إلى صورة قابلة للطباعة"
            return@withContext false
        }
        logStep("Rasterized PDF to ${bitmap.width}×${bitmap.height}")

        val payload = try {
            val raster = buildRasterCommand(bitmap, blackThreshold)
            val built = byteArrayOf(0x1B, 0x40) + raster + byteArrayOf(0x1B, 0x64, 0x02) + byteArrayOf(0x1D, 0x56, 0x01)
            logStep("Final ESC/POS payload size: ${built.size} bytes")
            built
        } catch (e: Exception) {
            logStep("❌ Failed to build ESC/POS command: ${e.message}")
            lastError = "فشل تجهيز بيانات الطباعة"
            return@withContext false
        }

        writeVerified(payload, printerMac)
    }

    /** Convenience wrapper for the common case (the settings screen's saved
        printer) so callers don't need to read savedPrinterMac themselves first. */
    suspend fun printPdfUsingSavedPrinter(pdfFile: File, blackThreshold: Int = 175): Boolean {
        val mac = savedPrinterMac.first()
        if (mac.isNullOrEmpty()) {
            resetLog()
            logStep("No saved printer MAC address")
            lastError = "لم يتم اختيار طابعة بعد — افتح إعدادات الطباعة"
            return false
        }
        return printPdf(pdfFile, mac, blackThreshold)
    }
}
