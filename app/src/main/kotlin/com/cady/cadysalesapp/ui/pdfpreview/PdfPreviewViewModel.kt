package com.cady.cadysalesapp.ui.pdfpreview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadysalesapp.data.pdf.PdfService
import com.cady.cadysalesapp.data.printing.ThermalPrintService
import com.cady.cadysalesapp.data.repository.CustomerRepository
import com.cady.cadysalesapp.data.repository.InvoiceRepository
import com.cady.cadysalesapp.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface PdfPreviewState {
    data object Loading : PdfPreviewState
    data class Ready(val file: File) : PdfPreviewState
    data class Error(val message: String) : PdfPreviewState
}

sealed interface ThermalPrintState {
    data object Idle : ThermalPrintState
    data object Printing : ThermalPrintState
    data object Success : ThermalPrintState
    data class Failed(val message: String, val log: List<String>) : ThermalPrintState
}

@HiltViewModel
class PdfPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pdfService: PdfService,
    private val thermalPrintService: ThermalPrintService,
    private val invoiceRepository: InvoiceRepository,
    private val receiptRepository: ReceiptRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val docType: String = checkNotNull(savedStateHandle["docType"])
    private val docId: String = checkNotNull(savedStateHandle["docId"])

    private val _state = MutableStateFlow<PdfPreviewState>(PdfPreviewState.Loading)
    val state: StateFlow<PdfPreviewState> = _state

    private val _thermalPrintState = MutableStateFlow<ThermalPrintState>(ThermalPrintState.Idle)
    val thermalPrintState: StateFlow<ThermalPrintState> = _thermalPrintState

    val requiredBluetoothPermissions: Array<String> get() = thermalPrintService.requiredPermissions
    fun hasBluetoothPermission(): Boolean = thermalPrintService.hasBluetoothPermission()

    init {
        generate()
    }

    private fun generate() {
        viewModelScope.launch {
            _state.value = PdfPreviewState.Loading
            try {
                val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
                val outputFile = File(pdfDir, "$docType-$docId.pdf")

                when (docType) {
                    "invoice" -> {
                        val invoice = invoiceRepository.getById(docId) ?: error("الفاتورة غير موجودة")
                        val items = invoiceRepository.getItems(docId)
                        pdfService.generateInvoicePdf(invoice, items, outputFile)
                    }
                    "receipt" -> {
                        val receipt = receiptRepository.getById(docId) ?: error("السند غير موجود")
                        pdfService.generateReceiptPdf(receipt, outputFile)
                    }
                    "statement" -> {
                        val customer = customerRepository.getById(docId) ?: error("العميل غير موجود")
                        val rows = customerRepository.getLedger(docId)
                        pdfService.generateStatementPdf(customer.name, rows, outputFile)
                    }
                    else -> error("نوع مستند غير معروف")
                }
                _state.value = PdfPreviewState.Ready(outputFile)
            } catch (e: Exception) {
                _state.value = PdfPreviewState.Error(e.message ?: "تعذّر إنشاء الملف")
            }
        }
    }

    fun printThermal() {
        val file = (_state.value as? PdfPreviewState.Ready)?.file ?: return
        _thermalPrintState.value = ThermalPrintState.Printing
        viewModelScope.launch {
            val ok = thermalPrintService.printPdfUsingSavedPrinter(file)
            _thermalPrintState.value = if (ok) {
                ThermalPrintState.Success
            } else {
                ThermalPrintState.Failed(
                    message = thermalPrintService.lastError ?: "فشلت الطباعة",
                    log = thermalPrintService.lastAttemptLog,
                )
            }
        }
    }

    fun dismissThermalPrintResult() {
        _thermalPrintState.value = ThermalPrintState.Idle
    }
}
