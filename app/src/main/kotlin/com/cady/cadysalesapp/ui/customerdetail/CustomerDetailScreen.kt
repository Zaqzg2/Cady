package com.cady.cadysalesapp.ui.customerdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.ui.home.RecentActivityItem

@Composable
fun CustomerDetailScreen(
    onNewInvoice: (kind: String) -> Unit,
    onNewReceipt: () -> Unit,
    onPreviewStatement: () -> Unit,
    onPrintStatement: () -> Unit,
    onDocumentClick: (type: String, id: String) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onMapClick: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val balance by viewModel.balance.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    val customer = state.customer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name.orEmpty()) },
                actions = {
                    IconButton(onClick = { viewModel.togglePin() }) {
                        Icon(
                            imageVector = if (customer?.isPinned == true) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "تثبيت",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة مستند")
            }
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text("فاتورة بيع") }, onClick = { showAddMenu = false; onNewInvoice("sale") })
                DropdownMenuItem(text = { Text("فاتورة مرتجع") }, onClick = { showAddMenu = false; onNewInvoice("return") })
                DropdownMenuItem(text = { Text("سند قبض") }, onClick = { showAddMenu = false; onNewReceipt() })
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header: balance + quick actions, including the statement
            // preview/print buttons — moved up here per the agreed fix, instead
            // of a separate repeated section further down the page.
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("الرصيد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        balance.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        customer?.phone?.let { phone ->
                            HeaderActionIcon(Icons.Filled.Call, "اتصال") { onCallClick(phone) }
                        }
                        customer?.address?.let {
                            HeaderActionIcon(Icons.Filled.Place, "الموقع") { onMapClick() }
                        }
                        HeaderActionIcon(Icons.Filled.Description, "معاينة كشف الحساب") { onPreviewStatement() }
                        HeaderActionIcon(Icons.Filled.Print, "طباعة كشف الحساب") { onPrintStatement() }
                    }
                }
            }

            Text(
                "السجل",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.timeline) { item ->
                    TimelineRow(item, onClick = {
                        when (item) {
                            is RecentActivityItem.Invoice -> onDocumentClick("invoice", item.invoice.id)
                            is RecentActivityItem.Receipt -> onDocumentClick("receipt", item.receipt.id)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun HeaderActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun TimelineRow(item: RecentActivityItem, onClick: () -> Unit) {
    val (title, subtitle, amount) = when (item) {
        is RecentActivityItem.Invoice -> Triple(
            if (item.invoice.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع",
            item.invoice.docNumber,
            item.invoice.balanceAfter,
        )
        is RecentActivityItem.Receipt -> Triple("سند قبض", item.receipt.docNumber, item.receipt.balanceAfter)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("الرصيد بعدها: $amount", style = MaterialTheme.typography.bodyMedium)
    }
}
