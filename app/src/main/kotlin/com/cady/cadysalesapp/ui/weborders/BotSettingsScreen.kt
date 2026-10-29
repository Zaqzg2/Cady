package com.cady.cadysalesapp.ui.weborders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.SectionTitle
import java.util.Locale

/**
 * The manager's rules for the AI seller on the public sales page: on/off, how long a quote stays valid, and the
 * ITEMS it may sell — each a product or a scent with its own carton price, the most it may take off, the size of
 * each concession and an optional short description. One round of haggling takes one step off every item of the
 * order, each down to its own lowest price. The seller applies these in code (the lowest prices are never sent
 * to the language model) and re-reads them about once a minute.
 */
@Composable
fun BotSettingsScreen(
    onBack: () -> Unit,
    viewModel: BotSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var linkingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.dismissMessage()
    }

    val (draft, problem) = checkDraft(state.enabled, state.hoursText, state.items)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CadyAppBar(
                title = { Text("البائع الآلي") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            CadyCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("تشغيل البائع الآلي", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "عند الإيقاف تعود الصفحة إلى أجوبة القائمة الجاهزة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            OutlinedTextField(
                value = state.hoursText,
                onValueChange = viewModel::setHours,
                label = { Text("صلاحية العرض (ساعات)") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("الأصناف (بالريال)")
            Text(
                "كل صنف — منتج أو رائحة — له سعره وخصمه وخطوته. المساومة الواحدة تنزل خطوة من كل أصناف الطلب، وكل صنف حتى أقل سعر له.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.items.forEach { item ->
                key(item.id) {
                    BotItemCard(
                        item = item,
                        onChange = { change -> viewModel.updateItem(item.id, change) },
                        onRemove = { viewModel.removeItem(item.id) },
                        onLink = { linkingId = item.id },
                        onUnlink = { viewModel.updateItem(item.id) { it.copy(productId = "") } },
                    )
                }
            }

            OutlinedButton(
                onClick = { showAdd = true },
                enabled = state.seeded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة صنف")
            }

            if (state.seeded && problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = state.seeded && draft != null && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.saving) "جارٍ الحفظ…" else "حفظ") }
        }
    }

    val linkedProductIds = state.items.mapNotNull { it.productId.ifBlank { null } }.toSet()
    if (showAdd) {
        ProductPickerDialog(
            title = "إضافة صنف",
            products = products,
            linkedProductIds = linkedProductIds,
            allowNamed = true,
            onDismiss = { showAdd = false },
            onPickProduct = { product ->
                viewModel.addItem(product.name, product)
                showAdd = false
            },
            onAddNamed = { name ->
                viewModel.addItem(name, null)
                showAdd = false
            },
        )
    }
    linkingId?.let { id ->
        ProductPickerDialog(
            title = "ربط الصنف بمنتج",
            products = products,
            linkedProductIds = linkedProductIds,
            allowNamed = false,
            onDismiss = { linkingId = null },
            onPickProduct = { product ->
                viewModel.updateItem(id) { it.copy(productId = product.id) }
                linkingId = null
            },
            onAddNamed = {},
        )
    }
}

/** One item: its name, its three price numbers, an optional description, an on/off switch and its own ladder. */
@Composable
private fun BotItemCard(
    item: BotItemDraft,
    onChange: ((BotItemDraft) -> BotItemDraft) -> Unit,
    onRemove: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    val parsed = item.toBotItemOrNull()
    val problem = item.problem()
    CadyCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { text -> onChange { it.copy(name = text) } },
                    label = { Text("اسم الصنف (منتج أو رائحة)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف الصنف")
                }
            }
            OutlinedTextField(
                value = item.priceText,
                onValueChange = { text -> onChange { it.copy(priceText = text) } },
                label = { Text("سعر الكرتونة") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = item.maxText,
                onValueChange = { text -> onChange { it.copy(maxText = text) } },
                label = { Text("أقصى خصم للكرتونة (0 يوقف التفاوض)") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = item.stepText,
                onValueChange = { text -> onChange { it.copy(stepText = text) } },
                label = { Text("حجم خطوة التنازل") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = item.note,
                onValueChange = { text -> onChange { it.copy(note = text) } },
                label = { Text("وصف قصير للبائع (اختياري)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            // Linked to an app product, an ordered line becomes an invoice line exactly — no guessing from names.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.productId.isBlank()) "غير مرتبط بمنتج في التطبيق" else "مرتبط بمنتج في التطبيق",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (item.productId.isBlank()) {
                    TextButton(onClick = onLink) { Text("ربط بمنتج") }
                } else {
                    TextButton(onClick = onUnlink) { Text("إلغاء الربط") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("متاح للبيع", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = item.active, onCheckedChange = { value -> onChange { it.copy(active = value) } })
            }
            if (parsed != null) {
                Text(
                    "سلّم التفاوض: " + parsed.ladder.joinToString(" ثم ") { money(it) } + " (آخر سعر)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "أقل سعر يصله البائع ${money(parsed.floor)} ريال، ولا يُرسل لأي نموذج ذكاء ولا يظهر في الصفحة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Pick one of the app's products (the item is then linked to it) — and, when [allowNamed], add an item by just
 * typing its name instead.
 */
@Composable
private fun ProductPickerDialog(
    title: String,
    products: List<ProductEntity>,
    linkedProductIds: Set<String>,
    allowNamed: Boolean,
    onDismiss: () -> Unit,
    onPickProduct: (ProductEntity) -> Unit,
    onAddNamed: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(products, query, linkedProductIds) {
        val text = query.trim()
        products.filter { it.id !in linkedProductIds && (text.isEmpty() || it.name.contains(text, ignoreCase = true)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (allowNamed) "ابحث في المنتجات أو اكتب اسمًا" else "ابحث في المنتجات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (matches.isEmpty()) {
                        Text(
                            if (allowNamed) "لا منتج مطابق. اكتب الاسم واضغط «إضافة بهذا الاسم»." else "لا منتج مطابق.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    matches.forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickProduct(product) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                money(product.price.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (allowNamed) {
                TextButton(onClick = { onAddNamed(query) }, enabled = query.isNotBlank()) { Text("إضافة بهذا الاسم") }
            } else {
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        },
        dismissButton = if (allowNamed) {
            { TextButton(onClick = onDismiss) { Text("إلغاء") } }
        } else {
            null
        },
    )
}

private fun money(value: Long): String = String.format(Locale.US, "%,d", value)
