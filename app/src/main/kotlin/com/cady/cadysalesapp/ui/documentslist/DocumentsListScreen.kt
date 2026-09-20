package com.cady.cadysalesapp.ui.documentslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.ui.home.RecentActivityItem

private data class DocRowDisplay(val title: String, val subtitle: String, val docType: String, val docId: String)

@Composable
fun DocumentsListScreen(
    onDocumentClick: (type: String, id: String) -> Unit,
    viewModel: DocumentsListViewModel = hiltViewModel(),
) {
    val items by viewModel.filteredItems.collectAsState()
    val filter by viewModel.filter.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("سجل المستندات") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    listOf(
                        DocumentFilter.ALL to "الكل",
                        DocumentFilter.SALE to "بيع",
                        DocumentFilter.RETURN to "مرتجع",
                        DocumentFilter.RECEIPT to "سند قبض",
                    )
                ) { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { viewModel.setFilter(value) }, label = { Text(label) })
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(items) { item ->
                    val row = when (item) {
                        is RecentActivityItem.Invoice -> DocRowDisplay(
                            title = if (item.invoice.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع",
                            subtitle = "${item.invoice.docNumber} — ${item.invoice.customerName}",
                            docType = "invoice",
                            docId = item.invoice.id,
                        )
                        is RecentActivityItem.Receipt -> DocRowDisplay(
                            title = "سند قبض",
                            subtitle = "${item.receipt.docNumber} — ${item.receipt.customerName}",
                            docType = "receipt",
                            docId = item.receipt.id,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDocumentClick(row.docType, row.docId) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge)
                            Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
