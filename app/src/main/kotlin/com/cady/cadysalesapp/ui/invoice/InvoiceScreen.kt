package com.cady.cadysalesapp.ui.invoice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.ui.common.LocalFileImage
import com.cady.cadysalesapp.ui.common.SignaturePad
import com.cady.cadysalesapp.ui.common.UiState
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.rememberSignaturePadState
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InvoiceScreen(
    onSaved: (invoiceId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: InvoiceViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val products by viewModel.products.collectAsState()
    val customers by viewModel.availableCustomers.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val companySettings by viewModel.companySettings.collectAsState()

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val signatureState = rememberSignaturePadState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (form.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
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

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.docNumber,
                    onValueChange = viewModel::setDocNumber,
                    label = { Text("رقم الفاتورة") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
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

            Spacer(Modifier.height(12.dp))
            Text("المنتجات", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (products.isEmpty()) {
                Text(
                    "لا توجد منتجات بعد — أضفها من تبويب المنتجات",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProductGrid(
                    products = products,
                    quantities = form.lines.associate { it.productId to it.quantity },
                    onProductClick = viewModel::addLine,
                )
            }

            if (form.lines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("الأصناف المضافة", style = MaterialTheme.typography.titleMedium)
                form.lines.forEach { line ->
                    key(line.key) {
                        val product = products.firstOrNull { it.id == line.productId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProductIconCircle(imagePath = product?.imagePath, name = line.productName, size = 40.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.productName)
                                Text(
                                    "${formatMoney(line.price)} × ${formatQuantity(line.quantity)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = formatQuantity(line.quantity),
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
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.discountPercent,
                    onValueChange = viewModel::setDiscountPercent,
                    label = { Text("خصم %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.discountAmount,
                    onValueChange = viewModel::setDiscountAmount,
                    label = { Text("خصم مبلغ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
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
                    if (!companySettings.repSignaturePath.isNullOrBlank()) {
                        "اتركه فارغًا لاستخدام التوقيع الافتراضي المحفوظ"
                    } else {
                        "التوقيع اختياري"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { signatureState.clear() }) { Text("مسح") }
            }

            Spacer(Modifier.height(12.dp))
            val totals = form.totals
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("الإجمالي")
                Text(totals.grandTotal.toString(), style = MaterialTheme.typography.titleMedium)
            }

            form.balanceAfterPreview?.let { balance ->
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("رصيد العميل بعد هذه العملية", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        balance.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (saveState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((saveState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!signatureState.isEmpty) {
                        val file = File(context.filesDir, "signatures/invoice_${System.currentTimeMillis()}.png")
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
        PickerDialog(
            title = "اختر عميلًا",
            options = customers,
            label = { it.name },
            onDismiss = { showCustomerPicker = false },
            onPick = { viewModel.selectCustomer(it); showCustomerPicker = false },
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

/** The product catalog as a grid of icon tiles (three per row) — tapping a
    tile adds that product to the invoice, or bumps its quantity by one if it
    is already there. A plain chunked Column of Rows rather than a lazy grid:
    the whole screen already scrolls, and a lazy grid nested inside a
    scrolling Column has no bounded height to lay itself out in. */
@Composable
private fun ProductGrid(
    products: List<ProductEntity>,
    quantities: Map<String, Double>,
    onProductClick: (ProductEntity) -> Unit,
) {
    val columns = 3
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        products.chunked(columns).forEach { rowProducts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowProducts.forEach { product ->
                    ProductTile(
                        product = product,
                        quantity = quantities[product.id] ?: 0.0,
                        onClick = { onProductClick(product) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowProducts.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One tile: soft container, round icon holder, name (always two lines tall so
    every tile in the grid is the same height), price. A small badge shows how
    many are already on the invoice, so a tap gets visible feedback even when
    the added-lines list is scrolled out of view. */
@Composable
private fun ProductTile(
    product: ProductEntity,
    quantity: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Opaque on purpose: a translucent fill would let the tile's own shadow
    // show through it.
    val tileColor = scheme.primaryContainer.copy(alpha = 0.45f).compositeOver(scheme.surface)
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = tileColor,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProductIconCircle(imagePath = product.imagePath, name = product.name, size = 56.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatMoney(product.price),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (quantity > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatQuantity(quantity),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
        }
    }
}

/** The round product icon used on the tiles and on the added-lines rows: the
    product's own photo when it has one, otherwise the default box icon. */
@Composable
private fun ProductIconCircle(imagePath: String?, name: String, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        } else {
            LocalFileImage(path = imagePath, contentDescription = name, modifier = Modifier.fillMaxSize())
        }
    }
}
