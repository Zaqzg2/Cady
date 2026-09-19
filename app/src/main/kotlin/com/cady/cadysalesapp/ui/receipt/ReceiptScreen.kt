package com.cady.cadysalesapp.ui.receipt

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
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
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.ui.common.UiState

@Composable
fun ReceiptScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val customers by viewModel.availableCustomers.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    var showCustomerPicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("سند قبض") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = form.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("المبلغ") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.method == ReceiptMethod.CASH,
                    onClick = { viewModel.setMethod(ReceiptMethod.CASH) },
                    label = { Text("نقدًا") },
                )
                FilterChip(
                    selected = form.method == ReceiptMethod.TRANSFER,
                    onClick = { viewModel.setMethod(ReceiptMethod.TRANSFER) },
                    label = { Text("تحويل") },
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("ملاحظات") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (saveState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((saveState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
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
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("اختر عميلًا") },
            text = {
                LazyColumn {
                    items(customers, key = { it.id }) { customer ->
                        Text(
                            customer.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCustomer(customer); showCustomerPicker = false }
                                .padding(vertical = 12.dp),
                        )
                        Divider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCustomerPicker = false }) { Text("إلغاء") } },
        )
    }
}
