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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cady.cadysalesapp.ui.common.CustomerPickerDialog
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.formatMoney
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.ReceiptMethod
import com.cady.cadysalesapp.ui.common.SignaturePad
import com.cady.cadysalesapp.ui.common.UiState
import com.cady.cadysalesapp.ui.common.AvatarInitial
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.IconTile
import com.cady.cadysalesapp.ui.common.SegmentedChoice
import com.cady.cadysalesapp.ui.common.rememberSignaturePadState
import com.cady.cadysalesapp.ui.theme.cady
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
    val balanceBefore by viewModel.balanceBefore.collectAsState()
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val signatureState = rememberSignaturePadState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()) }

    val receiptColor = MaterialTheme.cady.receipt
    val cashColor = MaterialTheme.cady.success

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text("سند قبض") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
        // The amount and the save button stay on screen however far the form is scrolled.
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("المبلغ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            form.amount.ifBlank { "0" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = receiptColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(min = 150.dp).height(52.dp),
                    ) {
                        if (saveState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("حفظ")
                        }
                    }
                }
            }
        },
    ) { padding ->
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
            // The whole card is the touch target — no separate "change" button to aim for.
            CadyCard(onClick = { showCustomerPicker = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val chosen = form.selectedCustomer
                    if (chosen == null) {
                        IconTile(
                            icon = Icons.Filled.Person,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            size = 44.dp,
                        )
                    } else {
                        AvatarInitial(chosen.name, size = 44.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val customer = form.selectedCustomer
                        if (customer == null) {
                            Text("اضغط لاختيار العميل", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(customer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val details = listOfNotNull(customer.phone, customer.address).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = form.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("المبلغ") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            // What the customer will owe after this receipt (before it: the figure shown here minus the amount).
            val before = balanceBefore
            if (before != null) {
                val entered = com.cady.cadysalesapp.ui.common.parseNumber(form.amount) ?: 0.0
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("رصيد العميل بعد هذا السند", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatMoney(before - entered),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (before - entered > 0) MaterialTheme.cady.debt else MaterialTheme.cady.credit,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SegmentedChoice(
                options = listOf(ReceiptMethod.CASH to "نقدًا", ReceiptMethod.TRANSFER to "تحويل"),
                selected = form.method,
                onSelect = { viewModel.setMethod(it) },
                accentFor = { if (it == ReceiptMethod.CASH) cashColor else receiptColor },
            )

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
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onPick = { viewModel.selectCustomer(it); showCustomerPicker = false },
            onDismiss = { showCustomerPicker = false },
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
