package com.cady.cadysalesapp.ui.receipt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.ui.common.SignaturePad
import com.cady.cadysalesapp.ui.common.UiState
import com.cady.cadysalesapp.ui.common.rememberSignaturePadState
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val signatureState = rememberSignaturePadState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("سند قبض") }) }) { padding ->
        // Scrollable: the signature pad below pushes this screen past one
        // phone screen of content, the same way it did on the invoice screen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.docNumber,
                    onValueChange = viewModel::setDocNumber,
                    label = { Text("رقم السند") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                // Same tap-to-pick date field as the invoice screen: a disabled
                // (so it doesn't take keyboard focus) text field with a
                // transparent tap target laid over it.
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = dateFormatter.format(form.date),
                        onValueChange = {},
                        enabled = false,
                        label = { Text("التاريخ") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(16.dp))
            Text("التوقيع", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            SignaturePad(state = signatureState, modifier = Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "اتركه فارغًا لاستخدام توقيع المندوب الافتراضي المحفوظ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { signatureState.clear() }) { Text("مسح") }
            }

            if (saveState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((saveState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (!signatureState.isEmpty) {
                        val file = File(context.filesDir, "signatures/receipt_${System.currentTimeMillis()}.png")
                        if (signatureState.saveTo(file)) {
                            viewModel.setSignaturePath(file.absolutePath)
                        }
                    }
                    viewModel.save(onSaved)
                },
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
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCustomerPicker = false }) { Text("إلغاء") } },
        )
    }
    if (showDatePicker) {
        LaunchedEffect(Unit) {
            val zoned = form.date.atZone(ZoneId.systemDefault())
            android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    viewModel.setDate(
                        LocalDate.of(year, month + 1, day).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    )
                },
                zoned.year, zoned.monthValue - 1, zoned.dayOfMonth,
            ).apply {
                setOnDismissListener { showDatePicker = false }
            }.show()
        }
    }
}
