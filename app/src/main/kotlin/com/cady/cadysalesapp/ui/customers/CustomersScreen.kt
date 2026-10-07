package com.cady.cadysalesapp.ui.customers

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.BorderStroke
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.ui.common.AvatarInitial
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadySearchField
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.Pill
import com.cady.cadysalesapp.ui.common.QuickAction
import com.cady.cadysalesapp.ui.common.QuickActionsSheet
import com.cady.cadysalesapp.ui.common.StatTile
import com.cady.cadysalesapp.ui.common.SwipeAction
import com.cady.cadysalesapp.ui.common.SwipeActionRow
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.dial
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.openWhatsApp
import com.cady.cadysalesapp.ui.common.parseNumber
import com.cady.cadysalesapp.ui.common.pressScale
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.manager.RepFilterRow
import com.cady.cadysalesapp.ui.theme.cady

@Composable
fun CustomersScreen(
    onCustomerClick: (String) -> Unit,
    onNewInvoice: (customerId: String, isReturn: Boolean) -> Unit = { _, _ -> },
    onNewReceipt: (customerId: String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: CustomersViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val isManager by viewModel.isManager.collectAsState()
    val reps by viewModel.reps.collectAsState()
    val selectedRepId by viewModel.selectedRepId.collectAsState()
    val ownerNames by viewModel.ownerNames.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomerEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var blockedDelete by remember { mutableStateOf<Pair<CustomerEntity, Int>?>(null) }
    val coordinator = rememberSwipeCoordinator()
    val context = LocalContext.current

    val totalDebt = remember(customers, balances) {
        customers.sumOf { (balances[it.id] ?: 0.0).coerceAtLeast(0.0) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CadyAppBar(title = { Text("العملاء") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("عميل جديد") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                CadySearchField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        viewModel.onSearchQueryChange(it)
                    },
                    placeholder = "بحث بالاسم أو الهاتف",
                )
            }
            if (isManager) {
                item(key = "reps") {
                    RepFilterRow(reps = reps, selectedId = selectedRepId, onSelect = viewModel::selectRep)
                }
            }
            if (customers.isNotEmpty()) {
                item(key = "summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "العملاء",
                            value = customers.size.toString(),
                            icon = Icons.Filled.Group,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "إجمالي المديونية",
                            value = formatMoney(totalDebt),
                            icon = Icons.Filled.Savings,
                            tint = MaterialTheme.cady.debt,
                            valueColor = if (totalDebt > 0.005) MaterialTheme.cady.debt else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Group,
                        title = if (searchText.isNotBlank()) "لا يوجد عميل بهذا الاسم" else "لا يوجد عملاء بعد",
                        message = if (searchText.isNotBlank()) "تأكد من الكتابة أو امسح البحث." else "أضف أول عميل لتبدأ بإصدار الفواتير والسندات له.",
                        actionLabel = if (searchText.isBlank()) "إضافة عميل" else null,
                        onAction = if (searchText.isBlank()) { { showAddDialog = true } } else null,
                    )
                }
            }

            items(customers, key = { it.id }) { customer ->
                CustomerListItem(
                    customer = customer,
                    balance = balances[customer.id] ?: 0.0,
                    ownerName = if (isManager) ownerNames[customer.ownerUid] else null,
                    coordinator = coordinator,
                    onOpen = { onCustomerClick(customer.id) },
                    onNewSale = { onNewInvoice(customer.id, false) },
                    onNewReturn = { onNewInvoice(customer.id, true) },
                    onNewReceipt = { onNewReceipt(customer.id) },
                    onCall = { customer.phone?.takeIf { it.isNotBlank() }?.let { context.dial(it) } },
                    onWhatsApp = { customer.phone?.takeIf { it.isNotBlank() }?.let { context.openWhatsApp(it) } },
                    onTogglePin = { viewModel.togglePin(customer.id) },
                    onEdit = { editing = customer },
                    onToggleActive = { viewModel.setActive(customer.id, !customer.isActive) },
                    onDelete = { pendingDelete = customer },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showAddDialog) {
        CustomerFormDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address, opening ->
                viewModel.createCustomer(name, phone.ifBlank { null }, address.ifBlank { null }, opening)
                showAddDialog = false
            },
        )
    }

    editing?.let { customer ->
        CustomerFormDialog(
            existing = customer,
            onDismiss = { editing = null },
            onSave = { name, phone, address, opening ->
                viewModel.updateCustomer(customer, name, phone.ifBlank { null }, address.ifBlank { null }, opening)
                editing = null
            },
        )
    }

    pendingDelete?.let { customer ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف العميل؟") },
            text = { Text("سيُحذف «${customer.name}» نهائيًا. لا يمكن حذف عميل له فواتير أو سندات.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteCustomer(customer.id) { count -> blockedDelete = customer to count }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }

    // Refused delete: say why, and offer the safe alternative instead of a dead end.
    blockedDelete?.let { (customer, count) ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            title = { Text("تعذّر الحذف") },
            text = {
                Text(
                    "«${customer.name}» لديه $count مستند، وحذفه سيُفقد سجله ورصيده. " +
                        "يمكنك إيقافه بدلًا من ذلك: يختفي من اختيار العميل ويبقى سجله كاملًا."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setActive(customer.id, false); blockedDelete = null }) { Text("إيقاف العميل") }
            },
            dismissButton = { TextButton(onClick = { blockedDelete = null }) { Text("إلغاء") } },
        )
    }
}

/**
 * A customer with every gesture:
 *  - tap → the account statement;
 *  - swipe LEFT → receipt voucher / sale invoice for this customer;  swipe RIGHT → edit / delete;
 *  - long-press → the quick menu (all documents, call, WhatsApp, edit, pin, suspend, delete);
 *  - the star pins to the top.
 */
@Composable
private fun CustomerListItem(
    customer: CustomerEntity,
    balance: Double,
    ownerName: String?,
    coordinator: com.cady.cadysalesapp.ui.common.SwipeCoordinator,
    onOpen: () -> Unit,
    onNewSale: () -> Unit,
    onNewReturn: () -> Unit,
    onNewReceipt: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var sheetOpen by remember { mutableStateOf(false) }
    val hasPhone = !customer.phone.isNullOrBlank()

    SwipeActionRow(
        modifier = modifier,
        rowKey = customer.id,
        coordinator = coordinator,
        rightActions = listOf(
            SwipeAction("سند", Icons.Filled.Receipt, SwipeColors.Blue, onClick = onNewReceipt),
            SwipeAction("فاتورة", Icons.Filled.Payments, SwipeColors.Green, onClick = onNewSale),
        ),
        leftActions = listOf(
            SwipeAction("تعديل", Icons.Filled.Edit, SwipeColors.Slate, onClick = onEdit),
            SwipeAction("حذف", Icons.Filled.Delete, SwipeColors.Red, onClick = onDelete),
        ),
    ) {
        CustomerRowCard(
            customer = customer,
            balance = balance,
            ownerName = ownerName,
            onClick = onOpen,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sheetOpen = true
            },
            onTogglePin = onTogglePin,
        )
    }

    if (sheetOpen) {
        val actions = buildList<QuickAction> {
            add(QuickAction("كشف الحساب", Icons.Filled.Description, onClick = onOpen))
            add(QuickAction("فاتورة بيع", Icons.Filled.Payments, onClick = onNewSale))
            add(QuickAction("فاتورة مرتجع", Icons.Filled.Replay, onClick = onNewReturn))
            add(QuickAction("سند قبض", Icons.Filled.Receipt, onClick = onNewReceipt))
            if (hasPhone) {
                add(QuickAction("اتصال", Icons.Filled.Call, onClick = onCall))
                add(QuickAction("واتساب", Icons.Filled.Chat, onClick = onWhatsApp))
            }
            add(QuickAction("تعديل", Icons.Filled.Edit, onClick = onEdit))
            add(
                QuickAction(
                    label = if (customer.isPinned) "إلغاء التثبيت" else "تثبيت في الأعلى",
                    icon = Icons.Filled.PushPin,
                    onClick = onTogglePin,
                )
            )
            add(
                QuickAction(
                    label = if (customer.isActive) "إيقاف العميل" else "تفعيل العميل",
                    icon = if (customer.isActive) Icons.Filled.Block else Icons.Filled.CheckCircle,
                    onClick = onToggleActive,
                )
            )
            add(QuickAction("حذف", Icons.Filled.Delete, destructive = true, onClick = onDelete))
        }
        QuickActionsSheet(
            title = customer.name,
            subtitle = balanceSentence(balance) + (customer.phone?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""),
            leading = { AvatarInitial(customer.name, size = 44.dp) },
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }
}

private fun balanceSentence(balance: Double): String = when {
    balance > 0.005 -> "عليه ${formatMoney(balance)}"
    balance < -0.005 -> "له ${formatMoney(-balance)}"
    else -> "الحساب مسدّد"
}

@Composable
private fun CustomerRowCard(
    customer: CustomerEntity,
    balance: Double,
    ownerName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarInitial(customer.name, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (customer.isActive) scheme.onSurface else scheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                customer.phone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                if (!customer.isActive || ownerName != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!customer.isActive) Pill("موقوف", scheme.error, icon = Icons.Filled.Block)
                        if (ownerName != null) Pill(ownerName, scheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    balance > 0.005 -> {
                        Text("عليه", style = MaterialTheme.typography.labelSmall, color = extra.debt)
                        Text(
                            formatMoney(balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extra.debt,
                            maxLines = 1,
                        )
                    }
                    balance < -0.005 -> {
                        Text("له", style = MaterialTheme.typography.labelSmall, color = extra.credit)
                        Text(
                            formatMoney(-balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = extra.credit,
                            maxLines = 1,
                        )
                    }
                    else -> Text("مسدّد", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = if (customer.isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (customer.isPinned) "إلغاء التثبيت" else "تثبيت",
                tint = if (customer.isPinned) extra.warning else scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePin)
                    .padding(8.dp)
                    .size(22.dp),
            )
        }
    }
}

/** One form for both "عميل جديد" and "تعديل العميل". */
@Composable
private fun CustomerFormDialog(
    existing: CustomerEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, openingBalance: Double) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var address by remember { mutableStateOf(existing?.address.orEmpty()) }
    var openingBalance by remember { mutableStateOf(existing?.let { formatQuantity(it.openingBalance) } ?: "0") }
    var attempted by remember { mutableStateOf(false) }

    // Empty means zero; anything else must be a real number (Arabic digits are fine).
    val parsedOpening = if (openingBalance.isBlank()) 0.0 else parseNumber(openingBalance)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "عميل جديد" else "تعديل العميل") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    isError = attempted && name.isBlank(),
                    supportingText = if (attempted && name.isBlank()) {
                        { Text("اكتب اسم العميل") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("الهاتف") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("الرصيد الافتتاحي") },
                    singleLine = true,
                    isError = parsedOpening == null,
                    supportingText = if (parsedOpening == null) {
                        { Text("أدخل رقمًا صحيحًا") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (name.isNotBlank() && parsedOpening != null) onSave(name.trim(), phone.trim(), address.trim(), parsedOpening)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
