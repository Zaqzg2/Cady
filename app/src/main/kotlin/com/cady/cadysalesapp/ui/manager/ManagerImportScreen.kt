package com.cady.cadysalesapp.ui.manager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.sync.RepExportPreview
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.launchShare
import com.cady.cadysalesapp.ui.common.summaryText

/** How many rows of each kind the preview lists before saying "and N more". */
private const val PREVIEW_ROWS = 25

@Composable
fun ManagerImportScreen(
    onBack: () -> Unit,
    viewModel: ManagerImportViewModel = hiltViewModel(),
) {
    val stage by viewModel.stage.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pick(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استيراد من مندوب") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
    ) { padding ->
        when (val current = stage) {
            is ImportStage.Idle -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("استلام ملف مزامنة من مندوب", style = MaterialTheme.typography.titleMedium)
                Text(
                    "اختر الملف الذي أرسله لك المندوب (واتساب أو غيره). سيُفحص ويُعرض لك ما فيه أولًا، ولا يُستورد شيء قبل أن تعتمد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("اختيار ملف") }
            }

            is ImportStage.Busy -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(current.message)
                }
            }

            is ImportStage.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(current.message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("اختيار ملف آخر") }
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("رجوع") }
            }

            is ImportStage.Preview -> PreviewContent(
                preview = current.preview,
                onApprove = viewModel::approve,
                onCancel = viewModel::reset,
                modifier = Modifier.padding(padding),
            )

            is ImportStage.Done -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("تمّ الاستيراد من ${current.preview.sender.displayName}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (current.result.applied.total > 0) "أُضيف: ${current.result.applied.summaryText()}" else "لم يكن في الملف جديد.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (current.result.alreadyPresent.total > 0) {
                            Text(
                                "موجود مسبقًا: ${current.result.alreadyPresent.summaryText()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "الخطوة الأخيرة: أرسل ملف تأكيد الاستلام للمندوب. من دونه تبقى سجلاته «معلّقة» عنده.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { context.launchShare(current.share) }, modifier = Modifier.fillMaxWidth()) {
                    Text("إرسال تأكيد الاستلام للمندوب")
                }
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("تم") }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    preview: RepExportPreview,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("الملف من: ${preview.sender.displayName}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            preview.sender.username,
                            preview.sender.repNumber?.let { "مندوب رقم $it" },
                            preview.sender.deviceName,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "أُنشئ: ${formatDateTime(preview.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!preview.senderKnown) {
            item {
                WarningCard("هذا المندوب غير موجود في قائمة المندوبين على جهازك. حدّث القائمة من شاشة «المندوبون» إن كان حسابه جديدًا — ويمكنك المتابعة.")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ما في الملف", style = MaterialTheme.typography.titleSmall)
                    SummaryLine(
                        "العملاء", preview.customers.size,
                        "جديد ${preview.newCustomers} · محدَّث ${preview.updatedCustomers} · موجود ${preview.duplicateCustomers}",
                    )
                    SummaryLine("الفواتير", preview.invoices.size, "جديد ${preview.newInvoices} · موجود ${preview.duplicateInvoices}")
                    SummaryLine("السندات", preview.receipts.size, "جديد ${preview.newReceipts} · موجود ${preview.duplicateReceipts}")
                }
            }
        }

        if (preview.differing > 0) {
            item { WarningCard("${preview.differing} مستند موجود عندك بمحتوى مختلف عمّا في الملف — يُبقى ما عندك ولا يُستبدل.") }
        }
        if (preview.ignoredProducts > 0) {
            item { WarningCard("${preview.ignoredProducts} منتج في الملف لم يُؤخذ — قائمة المنتجات يكتبها المدير فقط.") }
        }
        if (preview.notOwned > 0) {
            item { WarningCard("${preview.notOwned} سجل لا يخص هذا المندوب — تُجوهل ولن يدخل.") }
        }
        if (preview.invalid > 0) {
            item { WarningCard("${preview.invalid} سجل تعذّرت قراءته — يبقى معلّقًا عند المندوب.") }
        }

        val newInvoiceRows = preview.invoiceRows.filter { it.isNew }
        if (newInvoiceRows.isNotEmpty()) {
            item { Text("الفواتير الجديدة", style = MaterialTheme.typography.titleSmall) }
            items(newInvoiceRows.take(PREVIEW_ROWS)) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${row.invoice.docNumber} — ${row.invoice.customerName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatMoney(row.total), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (newInvoiceRows.size > PREVIEW_ROWS) {
                item { MoreLine(newInvoiceRows.size - PREVIEW_ROWS) }
            }
        }
        val newReceiptRows = preview.receiptRows.filter { it.isNew }
        if (newReceiptRows.isNotEmpty()) {
            item { Text("السندات الجديدة", style = MaterialTheme.typography.titleSmall) }
            items(newReceiptRows.take(PREVIEW_ROWS)) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${row.receipt.docNumber} — ${row.receipt.customerName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatMoney(row.receipt.amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (newReceiptRows.size > PREVIEW_ROWS) {
                item { MoreLine(newReceiptRows.size - PREVIEW_ROWS) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                    Text(if (preview.hasAnythingNew) "اعتماد واستيراد" else "لا جديد — إنشاء تأكيد الاستلام فقط")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, total: Int, detail: String) {
    Column {
        Text("$label: $total", style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MoreLine(count: Int) {
    Text("و$count أخرى…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
