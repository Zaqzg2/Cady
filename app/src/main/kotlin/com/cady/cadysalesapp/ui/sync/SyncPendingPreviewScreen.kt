package com.cady.cadysalesapp.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.MessageDialog
import com.cady.cadysalesapp.ui.common.formatDate
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.launchShare
import com.cady.cadysalesapp.ui.theme.SyncColors

@Composable
fun SyncPendingPreviewScreen(
    onBack: () -> Unit,
    viewModel: SyncPendingPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { context.launchShare(it) }
    }

    val snapshot = state.snapshot

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معاينة المعلّق") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
        bottomBar = {
            if (snapshot != null && !snapshot.isEmpty) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.export() },
                        enabled = !state.exporting,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (state.exporting) "جارٍ التجهيز…" else "تصدير هذه السجلات كملف")
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            snapshot == null || snapshot.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = SyncColors.Synced, modifier = Modifier.size(48.dp))
                    Text("لا توجد سجلات معلّقة", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (snapshot.customers.isNotEmpty()) {
                    item { SectionHeader("العملاء", snapshot.customers.size) }
                    items(snapshot.customers, key = { "c-" + it.id }) { customer ->
                        PreviewRow(
                            title = customer.name,
                            subtitle = customer.phone.orEmpty().ifBlank { "بلا هاتف" },
                            trailing = formatMoney(customer.openingBalance),
                        )
                    }
                }
                if (snapshot.products.isNotEmpty()) {
                    item { SectionHeader("المنتجات", snapshot.products.size) }
                    items(snapshot.products, key = { "p-" + it.id }) { product ->
                        PreviewRow(
                            title = product.name,
                            subtitle = product.unit.ifBlank { "—" },
                            trailing = formatMoney(product.price),
                        )
                    }
                }
                if (snapshot.invoices.isNotEmpty()) {
                    item { SectionHeader("الفواتير", snapshot.invoices.size) }
                    items(snapshot.invoices, key = { "i-" + it.invoice.id }) { pending ->
                        val invoice = pending.invoice
                        val total = computeInvoiceTotals(invoice, pending.items).grandTotal
                        val kind = if (invoice.kind == InvoiceKind.SALE) "بيع" else "مرتجع"
                        PreviewRow(
                            title = "${invoice.docNumber} — ${invoice.customerName}",
                            subtitle = "$kind · ${formatDate(invoice.date)} · ${pending.items.size} صنف",
                            trailing = formatMoney(total),
                        )
                    }
                }
                if (snapshot.receipts.isNotEmpty()) {
                    item { SectionHeader("السندات", snapshot.receipts.size) }
                    items(snapshot.receipts, key = { "r-" + it.id }) { receipt ->
                        PreviewRow(
                            title = "${receipt.docNumber} — ${receipt.customerName}",
                            subtitle = formatDate(receipt.date),
                            trailing = formatMoney(receipt.amount),
                        )
                    }
                }
            }
        }
    }

    state.message?.let { MessageDialog("تنبيه", it) { viewModel.clearMessage() } }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        "$title ($count)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PreviewRow(title: String, subtitle: String, trailing: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
