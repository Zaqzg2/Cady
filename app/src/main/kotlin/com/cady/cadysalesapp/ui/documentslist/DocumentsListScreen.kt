package com.cady.cadysalesapp.ui.documentslist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadySearchField
import com.cady.cadysalesapp.ui.common.DocumentListItem
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.dayHeaderLabel
import com.cady.cadysalesapp.ui.common.docId
import com.cady.cadysalesapp.ui.common.docType
import com.cady.cadysalesapp.ui.common.formatTime
import com.cady.cadysalesapp.ui.common.friendlyDateTime
import com.cady.cadysalesapp.ui.common.isPinned
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.common.toDisplay
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import com.cady.cadysalesapp.ui.manager.RepFilterRow
import java.time.LocalDate
import java.time.ZoneId

/** What a confirmed delete will remove: (docType, docId) pairs, and how to name them in the question. */
private class DeleteRequest(val refs: List<Pair<String, String>>, val description: String)

private fun RecentActivityItem.selectionKey(): String = docType + ":" + docId

@Composable
fun DocumentsListScreen(
    onDocumentClick: (type: String, id: String) -> Unit,
    onEditDocument: (type: String, id: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DocumentsListViewModel = hiltViewModel(),
) {
    val items by viewModel.filteredItems.collectAsState()
    val amounts by viewModel.amounts.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val isManager by viewModel.isManager.collectAsState()
    val reps by viewModel.reps.collectAsState()
    val selectedRepId by viewModel.selectedRepId.collectAsState()

    val coordinator = rememberSwipeCoordinator()
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedKeys.isNotEmpty()

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val pinned = remember(items) { items.filter { it.isPinned } }
    // The list arrives newest first, so grouping keeps the days in newest-first order.
    val byDay = remember(items) { items.filterNot { it.isPinned }.groupBy { it.date.atZone(zone).toLocalDate() } }
    val selectedItems = remember(items, selectedKeys) { items.filter { it.selectionKey() in selectedKeys } }

    // Rows that disappear (deleted, or filtered out) must not stay selected.
    LaunchedEffect(items) {
        val alive = items.map { it.selectionKey() }.toSet()
        val kept = selectedKeys.filter { it in alive }.toSet()
        if (kept.size != selectedKeys.size) selectedKeys = kept
    }
    BackHandler(enabled = selectionMode) { selectedKeys = emptySet() }

    fun toggle(item: RecentActivityItem) {
        val key = item.selectionKey()
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    val renderRow: @Composable LazyItemScope.(RecentActivityItem, Boolean) -> Unit = { item, fullDate ->
        val row = item.toDisplay(
            amount = amounts[item.docId] ?: 0.0,
            dateText = if (fullDate) friendlyDateTime(item.date) else formatTime(item.date),
            showRep = isManager,
        )
        DocumentListItem(
            row = row,
            coordinator = coordinator,
            selectionMode = selectionMode,
            selected = item.selectionKey() in selectedKeys,
            onOpen = { onDocumentClick(row.docType, row.id) },
            onEdit = { onEditDocument(row.docType, row.id) },
            onDelete = {
                pendingDelete = DeleteRequest(
                    refs = listOf(row.docType to row.id),
                    description = "«${row.typeLabel} ${row.docNumber} — ${row.customerName}»",
                )
            },
            onTogglePin = { viewModel.togglePin(row.docType, row.id) },
            onSelectMode = { selectedKeys = setOf(item.selectionKey()) },
            onToggleSelect = { toggle(item) },
            modifier = Modifier.animateItem(),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
                CadyAppBar(
                    title = { Text("${selectedKeys.size} محدد") },
                    navigationIcon = {
                        IconButton(onClick = { selectedKeys = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "إلغاء التحديد")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedKeys = items.map { it.selectionKey() }.toSet() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "تحديد الكل")
                        }
                        IconButton(
                            onClick = {
                                // All pinned already → unpin them; otherwise pin the whole selection.
                                val unpin = selectedItems.isNotEmpty() && selectedItems.all { it.isPinned }
                                viewModel.setPinnedMany(selectedItems.map { it.docType to it.docId }, pinned = !unpin)
                                selectedKeys = emptySet()
                            },
                        ) { Icon(Icons.Filled.PushPin, contentDescription = "تثبيت المحدد") }
                        IconButton(
                            onClick = {
                                pendingDelete = DeleteRequest(
                                    refs = selectedItems.map { it.docType to it.docId },
                                    description = "${selectedItems.size} مستند",
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف المحدد", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                CadyAppBar(
                    title = { Text("سجل المستندات") },
                    navigationIcon = { BackButton(onBack) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                CadySearchField(value = query, onValueChange = viewModel::setQuery, placeholder = "بحث بالاسم أو الرقم")
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterOption(DocumentFilter.ALL, "الكل", null, filter, viewModel::setFilter)
                    FilterOption(DocumentFilter.SALE, "بيع", Icons.Filled.Payments, filter, viewModel::setFilter)
                    FilterOption(DocumentFilter.RETURN, "مرتجع", Icons.Filled.Replay, filter, viewModel::setFilter)
                    FilterOption(DocumentFilter.RECEIPT, "سند قبض", Icons.Filled.Receipt, filter, viewModel::setFilter)
                }
            }
            if (isManager) {
                item(key = "reps") {
                    RepFilterRow(reps = reps, selectedId = selectedRepId, onSelect = viewModel::selectRep)
                }
            }

            if (items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = if (query.isNotBlank()) "لا توجد نتائج مطابقة" else "لا توجد مستندات بعد",
                        message = if (query.isNotBlank()) "جرّب كلمة أخرى أو امسح البحث." else "الفواتير وسندات القبض التي تحفظها تظهر هنا.",
                    )
                }
            }

            if (pinned.isNotEmpty()) {
                item(key = "h-pinned") { GroupHeader("المثبّتة", Icons.Filled.PushPin) }
                items(pinned, key = { it.selectionKey() }) { item -> renderRow(item, true) }
            }
            byDay.forEach { (day, dayItems) ->
                item(key = "h-$day") { GroupHeader(dayHeaderLabel(day, today)) }
                items(dayItems, key = { it.selectionKey() }) { item -> renderRow(item, false) }
            }
        }
    }

    pendingDelete?.let { request ->
        val many = request.refs.size > 1
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (many) "حذف المستندات المحددة؟" else "حذف المستند؟") },
            text = {
                Text(
                    "سيُحذف ${request.description} نهائيًا ويُعاد احتساب " +
                        (if (many) "أرصدة العملاء" else "رصيد العميل") + ". لا يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMany(request.refs)
                        pendingDelete = null
                        selectedKeys = emptySet()
                    },
                ) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun FilterOption(
    value: DocumentFilter,
    label: String,
    icon: ImageVector?,
    current: DocumentFilter,
    onSelect: (DocumentFilter) -> Unit,
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

/** "اليوم" / "أمس" / "الأحد 4 أكتوبر" followed by a hairline — the divider between days. */
@Composable
private fun GroupHeader(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
