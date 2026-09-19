package com.cady.cadysalesapp.ui.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.ui.common.UiState

@Composable
fun InvoiceScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: InvoiceViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val products by viewModel.products.collectAsState()
    val customers by viewModel.availableCustomers.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (form.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.kind == InvoiceKind.SALE,
                    onClick = { viewModel.setKind(InvoiceKind.SALE) },
                    label = { Text("بيع") },
                )
                FilterChip(
                    selected = form.kind == InvoiceKind.SALE_RETURN,
                    onClick = { viewModel.setKind(InvoiceKind.SALE_RETURN) },
                    label = { Text("مرتجع") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = form.paymentMode == PaymentMode.CASH,
                    onClick = { viewModel.setPaymentMode(PaymentMode.CASH) },
                    label = { Text("نقد") },
                )
                FilterChip(
                    selected = form.paymentMode == PaymentMode.CREDIT,
                    onClick = { viewModel.setPaymentMode(PaymentMode.CREDIT) },
                    label = { Text("آجل") },
                )
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(form.selectedCustomer?.name ?: "اختر عميلًا")
                    TextButton(onClick = { showCustomerPicker = true }) { Text("تغيير") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("الأصناف", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showProductPicker = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة صنف")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                items(form.lines, key = { it.key }) { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName)
                            Text(
                                "${line.price} × ${line.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = line.quantity.toString(),
                            onValueChange = { viewModel.updateLineQuantity(line.key, it.toDoubleOrNull() ?: line.quantity) },
                            modifier = Modifier.width(70.dp),
                            singleLine = true,
                        )
                        IconButton(onClick = { viewModel.removeLine(line.key) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.discountPercent,
                onValueChange = viewModel::setDiscountPercent,
                label = { Text("خصم %") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.discountAmount,
                onValueChange = viewModel::setDiscountAmount,
                label = { Text("خصم مبلغ") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("ملاحظات") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            val totals = form.totals
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("الإجمالي")
                Text(totals.grandTotal.toString(), style = MaterialTheme.typography.titleMedium)
            }

            if (saveState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((saveState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = saveState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (saveState is UiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("حفظ")
                }
            }
        }
    }

    if (showCustomerPicker) {
        PickerDialog(
            title = "اختر عميلًا",
            options = customers,
            label = { it.name },
            onDismiss = { showCustomerPicker = false },
            onPick = { viewModel.selectCustomer(it); showCustomerPicker = false },
        )
    }
    if (showProductPicker) {
        PickerDialog(
            title = "اختر صنفًا",
            options = products,
            label = { "${it.name} — ${it.price}" },
            onDismiss = { showProductPicker = false },
            onPick = { viewModel.addLine(it); showProductPicker = false },
        )
    }
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { option ->
                    Text(
                        label(option),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
