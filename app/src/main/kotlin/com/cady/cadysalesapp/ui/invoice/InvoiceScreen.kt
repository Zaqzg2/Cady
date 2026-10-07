package com.cady.cadysalesapp.ui.invoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.domain.OfferRules
import com.cady.cadysalesapp.ui.common.AvatarInitial
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.CustomerPickerDialog
import com.cady.cadysalesapp.ui.common.IconTile
import com.cady.cadysalesapp.ui.common.SegmentedChoice
import com.cady.cadysalesapp.ui.common.SwipeAction
import com.cady.cadysalesapp.ui.common.SwipeActionRow
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.ProductIconCircle
import com.cady.cadysalesapp.ui.common.QuantityStepper
import com.cady.cadysalesapp.ui.common.SearchPickerDialog
import com.cady.cadysalesapp.ui.common.SignaturePad
import com.cady.cadysalesapp.ui.common.UiState
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.rememberSignaturePadState
import com.cady.cadysalesapp.ui.theme.cady
import kotlinx.coroutines.launch
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
    // Read so the product list redraws when an offer arrives or expires (offerFor() looks it up).
    @Suppress("UNUSED_VARIABLE")
    val offers by viewModel.offers.collectAsState()

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val signatureState = rememberSignaturePadState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault()) }
    val totals = form.totals
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val swipeCoordinator = rememberSwipeCoordinator()
    val saleColor = MaterialTheme.cady.sale
    val returnColor = MaterialTheme.cady.returns
    val cashColor = MaterialTheme.cady.success
    val creditColor = MaterialTheme.cady.warning

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CadyAppBar(
                title = { Text(if (form.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
        // The total and the save button stay on screen however long the invoice gets.
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
                        Text("الإجمالي", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatMoney(totals.grandTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (form.kind == InvoiceKind.SALE) MaterialTheme.colorScheme.primary else returnColor,
                        )
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SegmentedChoice(
                    options = listOf(InvoiceKind.SALE to "بيع", InvoiceKind.SALE_RETURN to "مرتجع"),
                    selected = form.kind,
                    onSelect = { viewModel.setKind(it) },
                    accentFor = { if (it == InvoiceKind.SALE) saleColor else returnColor },
                    modifier = Modifier.weight(1f),
                )
                SegmentedChoice(
                    options = listOf(PaymentMode.CASH to "نقد", PaymentMode.CREDIT to "آجل"),
                    selected = form.paymentMode,
                    onSelect = { viewModel.setPaymentMode(it) },
                    accentFor = { if (it == PaymentMode.CASH) cashColor else creditColor },
                    modifier = Modifier.weight(1f),
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

            // The whole card is the touch target — no separate "change" button to aim for.
            CustomerCard(customer = form.selectedCustomer, onClick = { showCustomerPicker = true })

            Spacer(Modifier.height(12.dp))
            AddProductCard(
                lineCount = form.lines.size,
                hasProducts = products.isNotEmpty(),
                onClick = { showProductPicker = true },
            )

            if (form.lines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "اسحب الصنف يمينًا أو يسارًا لحذفه",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                form.lines.forEach { line ->
                    key(line.key) {
                        val product = products.firstOrNull { it.id == line.productId }
                        // Removing a line (the swipe button or the bin) can be taken back from the snackbar.
                        val removeLine: () -> Unit = {
                            viewModel.removeLine(line.key)
                            viewModel.dismissError()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "تم حذف «${line.productName}»",
                                    actionLabel = if (product != null) "تراجع" else null,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed && product != null) {
                                    viewModel.setProductQuantity(product, line.quantity)
                                }
                            }
                        }
                        val deleteAction = SwipeAction("حذف", Icons.Filled.Delete, SwipeColors.Red, onClick = removeLine)
                        SwipeActionRow(
                            modifier = Modifier.padding(vertical = 4.dp),
                            rowKey = line.key,
                            coordinator = swipeCoordinator,
                            leftActions = listOf(deleteAction),
                            rightActions = listOf(deleteAction),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            InvoiceLineRow(
                                line = line,
                                imagePath = product?.imagePath,
                                onQuantityChange = { viewModel.updateLineQuantity(line.key, it); viewModel.dismissError() },
                                onRemove = removeLine,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.discountPercent,
                    onValueChange = { viewModel.setDiscountPercent(it); viewModel.dismissError() },
                    label = { Text("خصم %") },
                    singleLine = true,
                    keyboardOptions = DecimalKeyboard,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.discountAmount,
                    onValueChange = { viewModel.setDiscountAmount(it); viewModel.dismissError() },
                    label = { Text("خصم مبلغ") },
                    singleLine = true,
                    keyboardOptions = DecimalKeyboard,
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
            CadyCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (totals.discountValue > 0.0) {
                        SummaryLine("المجموع قبل الخصم", formatMoney(totals.subTotal))
                        SummaryLine("الخصم", "− ${formatMoney(totals.discountValue)}")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SummaryLine("الإجمالي", formatMoney(totals.grandTotal), emphasized = true)
                    form.balanceAfterPreview?.let { balance ->
                        SummaryLine(
                            label = "رصيد العميل بعد هذه العملية",
                            value = formatMoney(balance),
                            valueColor = if (balance > 0) MaterialTheme.cady.debt else MaterialTheme.cady.credit,
                        )
                    }
                }
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
            onPick = { viewModel.selectCustomer(it); viewModel.dismissError(); showCustomerPicker = false },
            onDismiss = { showCustomerPicker = false },
        )
    }
    if (showProductPicker) {
        ProductPickerDialog(
            products = products,
            quantityOf = { product -> form.lines.firstOrNull { it.productId == product.id }?.quantity ?: 0.0 },
            offerFor = viewModel::offerFor,
            lineCount = form.lines.size,
            onQuantityChange = { product, quantity -> viewModel.setProductQuantity(product, quantity); viewModel.dismissError() },
            onDismiss = { showProductPicker = false },
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

// ---------------------------------------------------------------------------------------------

/** The customer of this invoice — the whole card opens the picker. */
@Composable
private fun CustomerCard(customer: CustomerEntity?, onClick: () -> Unit) {
    CadyCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (customer == null) {
                IconTile(
                    icon = Icons.Filled.Person,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    size = 44.dp,
                )
            } else {
                AvatarInitial(customer.name, size = 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (customer == null) {
                    Text("اضغط لاختيار العميل", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(customer.phone, customer.address).joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Add a product": the same one-card pattern as the customer — tap, search, pick. */
@Composable
private fun AddProductCard(lineCount: Int, hasProducts: Boolean, onClick: () -> Unit) {
    CadyCard(onClick = onClick, enabled = hasProducts, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = Icons.Filled.Add,
                tint = Color.White,
                container = if (hasProducts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !hasProducts -> "لا توجد منتجات بعد — أضفها من تبويب المنتجات"
                        lineCount == 0 -> "اضغط لإضافة منتج"
                        else -> "إضافة أو تعديل المنتجات"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasProducts) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lineCount > 0) {
                    Text(
                        "$lineCount ${if (lineCount == 1) "صنف" else "أصناف"} في الفاتورة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One line on the invoice: name and price on top, line total and the − [qty] + control underneath. */
@Composable
private fun InvoiceLineRow(
    line: EditableLine,
    imagePath: String?,
    onQuantityChange: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    // Opaque on purpose: the swipe buttons sit behind it (see SwipeActionRow).
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductIconCircle(imagePath = imagePath, name = line.productName, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.productName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PriceWithOffer(price = line.price, listPrice = line.listPrice)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف الصنف", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMoney(line.price * line.quantity),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                QuantityStepper(quantity = line.quantity, onQuantityChange = onQuantityChange)
            }
        }
    }
}

/** One "label … value" line of the summary card. */
@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasized: Boolean = false,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
        )
    }
}

/** The unit price; when an offer lowered it, the normal price is shown struck through beside it. */
@Composable
private fun PriceWithOffer(price: Double, listPrice: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatMoney(price),
            style = MaterialTheme.typography.bodyMedium,
            color = if (listPrice != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (listPrice != null) FontWeight.Bold else FontWeight.Normal,
        )
        if (listPrice != null && listPrice != price) {
            Spacer(Modifier.width(6.dp))
            Text(
                formatMoney(listPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

/** A small pill: "عرض −10%". */
@Composable
private fun OfferBadge(percent: Double) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "عرض −${OfferRules.percentText(percent)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * The product popup: a filter field on top and every product below it, each with − [qty] +.
 * Tapping a product's name adds one; typing a number in the field sets the exact amount.
 * Everything applies to the invoice immediately — "تم" just closes the popup.
 */
@Composable
private fun ProductPickerDialog(
    products: List<ProductEntity>,
    quantityOf: (ProductEntity) -> Double,
    offerFor: (ProductEntity) -> OfferEntity?,
    lineCount: Int,
    onQuantityChange: (ProductEntity, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    SearchPickerDialog(
        items = products,
        searchText = { it.name },
        onDismiss = onDismiss,
        searchLabel = "بحث عن منتج",
        emptyText = "لا توجد منتجات مطابقة",
        itemKey = { it.id },
        footer = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (lineCount > 0) "تم ($lineCount)" else "إغلاق")
            }
        },
    ) { product ->
        val quantity = quantityOf(product)
        val offer = offerFor(product)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onQuantityChange(product, quantity + 1) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductIconCircle(imagePath = product.imagePath, name = product.name, size = 44.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (offer != null) {
                        PriceWithOffer(price = OfferRules.discountedPrice(product.price, offer.percent), listPrice = product.price)
                        OfferBadge(offer.percent)
                    } else {
                        Text(
                            formatMoney(product.price),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            QuantityStepper(
                quantity = quantity,
                onQuantityChange = { onQuantityChange(product, it) },
                compact = true,
            )
        }
    }
}
