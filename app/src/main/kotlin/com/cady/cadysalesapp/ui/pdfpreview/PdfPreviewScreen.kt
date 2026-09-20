package com.cady.cadysalesapp.ui.pdfpreview

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
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
    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.printThermal()
    }

    LaunchedEffect(state) {
        val file = (state as? PdfPreviewState.Ready)?.file ?: return@LaunchedEffect
        val result = renderFirstPage(file)
        pageBitmap = result
        renderError = if (result == null) "تعذّرت معاينة الملف — جرّب المشاركة بدل ذلك" else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معاينة") },
                actions = {
                    IconButton(onClick = {
                        if (viewModel.hasBluetoothPermission()) {
                            viewModel.printThermal()
                        } else if (viewModel.requiredBluetoothPermissions.isNotEmpty()) {
                            bluetoothPermissionLauncher.launch(viewModel.requiredBluetoothPermissions)
                        } else {
                            viewModel.printThermal()
                        }
                    }) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = "طباعة حرارية")
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
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state is PdfPreviewState.Loading -> CircularProgressIndicator()
                state is PdfPreviewState.Error -> Text((state as PdfPreviewState.Error).message, color = MaterialTheme.colorScheme.error)
                renderError != null -> Text(renderError!!, color = MaterialTheme.colorScheme.error)
                pageBitmap != null -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Image(bitmap = pageBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                    }
                }
                else -> CircularProgressIndicator()
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
                                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    androidx.compose.foundation.lazy.items(failed.log) { line ->
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
        }
    }
}

private fun renderFirstPage(file: File): Bitmap? = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                // 2x scale for a sharper on-screen preview than the PDF's raw 72dpi.
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
} catch (e: Exception) {
    null
}

/** Wraps an already-generated PDF file for Android's native print framework —
    onWrite just streams the existing bytes rather than re-rendering, since the
    file is already exactly what should be printed. Works with any registered
    print service (a real printer's driver, "Save as PDF", RawBT, etc.) — the
    same "print via another app" fallback the Flutter app needed a dedicated
    settings toggle for is just how printing works here by default. */
private fun printPdf(context: Context, file: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
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
                .setPageCount(1)
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
