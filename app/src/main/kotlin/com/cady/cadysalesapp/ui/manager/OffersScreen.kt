package com.cady.cadysalesapp.ui.manager

import com.cady.cadysalesapp.ui.common.CadyCard
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.domain.OfferRules
import com.cady.cadysalesapp.domain.OfferStatus
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.QuickAction
import com.cady.cadysalesapp.ui.common.QuickActionsSheet
import com.cady.cadysalesapp.ui.common.SwipeAction
import com.cady.cadysalesapp.ui.common.SwipeActionRow
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.SwipeCoordinator
import com.cady.cadysalesapp.ui.common.pressScale
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.common.ProductIconCircle
import com.cady.cadysalesapp.ui.common.SearchPickerDialog
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.parseNumber
import com.cady.cadysalesapp.ui.theme.cady
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The manager's offers: "X% off this product, from this day to that day". Each row can be paused with
 * its switch (it then never applies, whatever the date), edited by tapping it, or deleted. Reps
 * receive offers with the next sync and the invoice applies them by itself.
 */
@Composable
fun OffersScreen(
    onBack: () -> Unit,
    viewModel: OffersViewModel = hiltViewModel(),
) {
    val offers by viewModel.offers.collectAsState()
    val products by viewModel.products.collectAsState()

    var editing by remember { mutableStateOf<OfferEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OfferEntity?>(null) }

    val now = remember(offers) { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(zone) }

    val coordinator = rememberSwipeCoordinator()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text("العروض") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("عرض جديد") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (offers.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.LocalOffer,
                        title = "لا توجد عروض بعد",
                        message = "أنشئ أول عرض: نسبة خصم على منتج خلال فترة محددة.",
                        actionLabel = "عرض جديد",
                        onAction = { editing = null; showForm = true },
                    )
                }
            } else {
                item(key = "hint") {
                    Text(
                        "العرض الساري يُطبَّق تلقائيًا على سطر المنتج في الفاتورة، ويظهر اسمه على الفاتورة المطبوعة. " +
                            "اسحب العرض لإيقافه أو حذفه، أو اضغط عليه مطوّلًا لكل الخيارات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            items(offers, key = { it.id }) { offer ->
                OfferListItem(
                    offer = offer,
                    product = products.firstOrNull { it.id == offer.productId },
                    status = OfferRules.statusOf(offer, now),
                    dateText = "من ${dateFormatter.format(offer.startsAt)} إلى ${dateFormatter.format(offer.endsAt)}",
                    coordinator = coordinator,
                    onEdit = { editing = offer; showForm = true },
                    onSetActive = { viewModel.setActive(offer, it) },
                    onDelete = { pendingDelete = offer },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showForm) {
        OfferFormDialog(
            existing = editing,
            products = products,
            onDismiss = { showForm = false },
            onSave = { product, percent, first, last, active ->
                viewModel.save(editing, product, percent, first, last, active)
                showForm = false
            },
        )
    }

    pendingDelete?.let { offer ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف العرض؟") },
            text = { Text("سيُحذف عرض «${offer.productName}». الفواتير التي استفادت منه تبقى كما هي.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(offer); pendingDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }
}

/**
 * One offer with its gestures: tap → edit; swipe LEFT → pause / resume; swipe RIGHT → edit / delete;
 * long-press → the quick menu; the switch is the same pause / resume, one tap away.
 */
@Composable
private fun OfferListItem(
    offer: OfferEntity,
    product: ProductEntity?,
    status: OfferStatus,
    dateText: String,
    coordinator: SwipeCoordinator,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var sheetOpen by remember { mutableStateOf(false) }
    val running = status == OfferStatus.ACTIVE

    SwipeActionRow(
        modifier = modifier,
        rowKey = offer.id,
        coordinator = coordinator,
        rightActions = listOf(
            if (offer.isActive) {
                SwipeAction("إيقاف", Icons.Filled.Pause, SwipeColors.Amber) { onSetActive(false) }
            } else {
                SwipeAction("تفعيل", Icons.Filled.PlayArrow, SwipeColors.Green) { onSetActive(true) }
            },
        ),
        leftActions = listOf(
            SwipeAction("تعديل", Icons.Filled.Edit, SwipeColors.Slate, onClick = onEdit),
            SwipeAction("حذف", Icons.Filled.Delete, SwipeColors.Red, onClick = onDelete),
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().pressScale(interaction),
            shape = RoundedCornerShape(20.dp),
            color = if (running) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, if (running) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onEdit,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sheetOpen = true
                        },
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductIconCircle(imagePath = product?.imagePath, name = offer.productName, size = 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(offer.productName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "خصم ${OfferRules.percentText(offer.percent)}% — $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (product != null) {
                        Text(
                            "${formatMoney(product.price)} ← ${formatMoney(OfferRules.discountedPrice(product.price, offer.percent))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(status)
                }
                Switch(checked = offer.isActive, onCheckedChange = onSetActive)
            }
        }
    }

    if (sheetOpen) {
        val actions = listOf(
            QuickAction("تعديل العرض", Icons.Filled.Edit, onClick = onEdit),
            if (offer.isActive) {
                QuickAction("إيقاف العرض", Icons.Filled.Pause) { onSetActive(false) }
            } else {
                QuickAction("تفعيل العرض", Icons.Filled.PlayArrow) { onSetActive(true) }
            },
            QuickAction("حذف", Icons.Filled.Delete, destructive = true, onClick = onDelete),
        )
        QuickActionsSheet(
            title = offer.productName,
            subtitle = "خصم ${OfferRules.percentText(offer.percent)}% — $dateText",
            leading = { ProductIconCircle(imagePath = product?.imagePath, name = offer.productName, size = 44.dp) },
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun StatusPill(status: OfferStatus) {
    val (label, container) = when (status) {
        OfferStatus.ACTIVE -> "ساري الآن" to MaterialTheme.colorScheme.primary
        OfferStatus.UPCOMING -> "قادم" to MaterialTheme.colorScheme.tertiary
        OfferStatus.EXPIRED -> "منتهٍ" to MaterialTheme.colorScheme.outline
        OfferStatus.PAUSED -> "متوقف" to MaterialTheme.colorScheme.outline
    }
    Surface(shape = RoundedCornerShape(50), color = container, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun OfferFormDialog(
    existing: OfferEntity?,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (product: ProductEntity, percent: Double, firstDay: LocalDate, lastDay: LocalDate, active: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }

    var product by remember { mutableStateOf(existing?.let { e -> products.firstOrNull { it.id == e.productId } }) }
    var percentText by remember { mutableStateOf(existing?.let { formatQuantity(it.percent) }.orEmpty()) }
    var firstDay by remember { mutableStateOf(existing?.startsAt?.atZone(zone)?.toLocalDate() ?: LocalDate.now()) }
    var lastDay by remember { mutableStateOf(existing?.endsAt?.atZone(zone)?.toLocalDate() ?: LocalDate.now().plusDays(7)) }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }
    var showProductPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "عرض جديد" else "تعديل العرض") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // The same tap-the-card pattern as choosing a customer or a product on an invoice.
                CadyCard(onClick = { showProductPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val chosen = product
                        if (chosen != null) {
                            ProductIconCircle(imagePath = chosen.imagePath, name = chosen.name, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chosen.name, style = MaterialTheme.typography.titleMedium)
                                Text(formatMoney(chosen.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(
                                "اضغط لاختيار المنتج",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = percentText,
                    onValueChange = { percentText = it; error = null },
                    label = { Text("نسبة الخصم %") },
                    singleLine = true,
                    keyboardOptions = DecimalKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { pickDate(firstDay) { firstDay = it; error = null } },
                        modifier = Modifier.weight(1f),
                    ) { Text("من ${dateFormatter.format(firstDay)}") }
                    OutlinedButton(
                        onClick = { pickDate(lastDay) { lastDay = it; error = null } },
                        modifier = Modifier.weight(1f),
                    ) { Text("إلى ${dateFormatter.format(lastDay)}") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("العرض مفعّل", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = product
                val percent = parseNumber(percentText)
                when {
                    chosen == null -> error = "اختر المنتج"
                    percent == null || percent <= 0.0 || percent > 100.0 -> error = "نسبة الخصم يجب أن تكون بين 0 و 100"
                    lastDay.isBefore(firstDay) -> error = "تاريخ نهاية العرض قبل تاريخ بدايته"
                    else -> onSave(chosen, percent, firstDay, lastDay, active)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (showProductPicker) {
        SearchPickerDialog(
            items = products,
            searchText = { it.name },
            onDismiss = { showProductPicker = false },
            searchLabel = "بحث عن منتج",
            emptyText = "لا توجد منتجات — أضفها من تبويب المنتجات",
            itemKey = { it.id },
            footer = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showProductPicker = false }) { Text("إلغاء") }
                }
            },
        ) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { product = item; error = null; showProductPicker = false }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductIconCircle(imagePath = item.imagePath, name = item.name, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(formatMoney(item.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

