package com.cady.cadysalesapp.ui.pdfpreview

import com.cady.cadysalesapp.ui.common.CadyAppBar
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.UiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun PdfPreviewScreen(
    onBack: () -> Unit,
    viewModel: PdfPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val thermalPrintState by viewModel.thermalPrintState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val context = LocalContext.current
    val printers by viewModel.printers.collectAsState()
    val defaultPrinterMac by viewModel.defaultPrinterMac.collectAsState()
    var printerMenuOpen by remember { mutableStateOf(false) }
    // The printer the person chose, kept across the Bluetooth permission prompt.
    var pendingPrinterMac by remember { mutableStateOf<String?>(null) }

    // Every page of the PDF is shown (an A4 invoice or statement can run to several). Pages are drawn one
    // at a time, only while they are on screen, so a long document never fills the phone's memory.
    val readyFile = (state as? PdfPreviewState.Ready)?.file
    val pdfPages = remember(readyFile) { readyFile?.let { PdfPages.open(it) } }
    DisposableEffect(pdfPages) { onDispose { pdfPages?.close() } }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.printThermal(pendingPrinterMac)
    }

    fun startThermalPrint(printerMac: String?) {
        pendingPrinterMac = printerMac
        if (viewModel.hasBluetoothPermission() || viewModel.requiredBluetoothPermissions.isEmpty()) {
            viewModel.printThermal(printerMac)
        } else {
            bluetoothPermissionLauncher.launch(viewModel.requiredBluetoothPermissions)
        }
    }

    // Storage Access Framework: the user picks the destination folder/name
    // themselves, so no WRITE_EXTERNAL_STORAGE-style permission is needed.
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.downloadTo(uri) }

    Scaffold(
        topBar = {
            CadyAppBar(
                title = { Text("معاينة") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
                actions = {
                    // One saved printer prints straight away; with several, the person picks which one.
                    Box {
                        IconButton(onClick = {
                            if (printers.size > 1) printerMenuOpen = true else startThermalPrint(null)
                        }) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = "طباعة حرارية")
                        }
                        DropdownMenu(expanded = printerMenuOpen, onDismissRequest = { printerMenuOpen = false }) {
                            printers.forEach { printer ->
                                DropdownMenuItem(
                                    text = {
                                        Text(printer.name + if (printer.mac.equals(defaultPrinterMac, ignoreCase = true)) " (الافتراضية)" else "")
                                    },
                                    onClick = { printerMenuOpen = false; startThermalPrint(printer.mac) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        val file = (state as? PdfPreviewState.Ready)?.file ?: return@IconButton
                        printPdf(context, file)
                    }) {
                        Icon(Icons.Filled.Print, contentDescription = "طباعة")
                    }
                    IconButton(onClick = {
                        val file = (state as? PdfPreviewState.Ready)?.file ?: return@IconButton
                        val uri = FileProvider.getUriForFile(context, "com.cady.cadysalesapp.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "مشاركة")
                    }
                    IconButton(onClick = {
                        val file = (state as? PdfPreviewState.Ready)?.file ?: return@IconButton
                        downloadLauncher.launch(file.name)
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "تحميل")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state is PdfPreviewState.Loading -> CircularProgressIndicator()
                state is PdfPreviewState.Error -> Text((state as PdfPreviewState.Error).message, color = MaterialTheme.colorScheme.error)
                pdfPages == null -> Text("تعذّرت معاينة الملف — جرّب المشاركة بدل ذلك", color = MaterialTheme.colorScheme.error)
                else -> PdfPagesList(pdfPages)
            }

            if (thermalPrintState is ThermalPrintState.Printing) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("جارٍ الطباعة…") },
                    text = { CircularProgressIndicator() },
                    confirmButton = {},
                )
            }
            if (thermalPrintState is ThermalPrintState.Success) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissThermalPrintResult,
                    title = { Text("تمت الطباعة ✓") },
                    text = {},
                    confirmButton = { TextButton(onClick = viewModel::dismissThermalPrintResult) { Text("حسنًا") } },
                )
            }
            val failed = thermalPrintState as? ThermalPrintState.Failed
            if (failed != null) {
                var showDetails by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = viewModel::dismissThermalPrintResult,
                    title = { Text("تعذّرت الطباعة") },
                    text = {
                        Column {
                            Text(failed.message)
                            if (showDetails) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(failed.log) { line ->
                                        Text(line, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else {
                                TextButton(onClick = { showDetails = true }) { Text("التفاصيل") }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = viewModel::dismissThermalPrintResult) { Text("حسنًا") } },
                )
            }
            if (downloadState is UiState.Loading) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("جارٍ الحفظ…") },
                    text = { CircularProgressIndicator() },
                    confirmButton = {},
                )
            }
            if (downloadState is UiState.Success) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissDownloadResult,
                    title = { Text("تم الحفظ ✓") },
                    text = {},
                    confirmButton = { TextButton(onClick = viewModel::dismissDownloadResult) { Text("حسنًا") } },
                )
            }
            val downloadError = downloadState as? UiState.Error
            if (downloadError != null) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissDownloadResult,
                    title = { Text("تعذّر الحفظ") },
                    text = { Text(downloadError.message) },
                    confirmButton = { TextButton(onClick = viewModel::dismissDownloadResult) { Text("حسنًا") } },
                )
            }
        }
    }
}

/** The pages of one open PDF, drawn on demand (PdfRenderer can only have one page open at a time, hence the lock). */
private class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount
    private val lock = Mutex()

    suspend fun render(index: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    // About one phone-screen width of pixels (sharper than the PDF's raw 72dpi), but never so
                    // many that one page needs more than roughly 36 MB.
                    val sharp = (1080f / page.width).coerceIn(1.5f, 3f)
                    val memoryCap = sqrt(9_000_000f / (page.width.toFloat() * page.height.toFloat()))
                    val scale = min(sharp, memoryCap)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    // PdfRenderer draws no paper: without a white fill the text is black on whatever is
                    // behind it — unreadable in dark mode.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun close() {
        try { renderer.close() } catch (_: Exception) {}
        try { descriptor.close() } catch (_: Exception) {}
    }

    companion object {
        fun open(file: File): PdfPages? = try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                PdfPages(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                descriptor.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun PdfPagesList(pages: PdfPages) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(count = pages.pageCount) { index ->
            val bitmap by produceState<Bitmap?>(initialValue = null, pages, index) { value = pages.render(index) }
            val current = bitmap
            if (current == null) {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Image(bitmap = current.asImageBitmap(), contentDescription = "صفحة ${index + 1}", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wraps an already-generated PDF file for Android's native print framework —
    onWrite just streams the existing bytes rather than re-rendering, since the
    file is already exactly what should be printed. Works with any registered
    print service (a real printer's driver, "Save as PDF", RawBT, etc.) — the
    same "print via another app" fallback the Flutter app needed a dedicated
    settings toggle for is just how printing works here by default. */
private fun printPdf(context: Context, file: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    // The print dialog needs the true number of pages — a multi-page A4 document reported as one page
    // would be cut to its first page.
    val pageCount = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    } catch (e: Exception) {
        PrintDocumentInfo.PAGE_COUNT_UNKNOWN
    }
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    printManager.print(file.name, adapter, PrintAttributes.Builder().build())
}
