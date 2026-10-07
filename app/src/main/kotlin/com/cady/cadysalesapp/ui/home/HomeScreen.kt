package com.cady.cadysalesapp.ui.home

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.DocDisplay
import com.cady.cadysalesapp.ui.common.DocumentListItem
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.HeroCard
import com.cady.cadysalesapp.ui.common.IconTile
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.animatedMoneyText
import com.cady.cadysalesapp.ui.common.docId
import com.cady.cadysalesapp.ui.common.docType
import com.cady.cadysalesapp.ui.common.formatCompact
import com.cady.cadysalesapp.ui.common.friendlyDateTime
import com.cady.cadysalesapp.ui.common.monthName
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.common.toDisplay
import com.cady.cadysalesapp.ui.common.weekdayName
import com.cady.cadysalesapp.ui.theme.cady
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The swipe hint on the first row is shown once per app launch, not every time Home is opened. */
private var homeSwipeHintShown = false

@Composable
fun HomeScreen(
    onNewSale: () -> Unit,
    onNewReturn: () -> Unit,
    onNewReceipt: () -> Unit,
    onNewCashCustomerSale: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewAllDocuments: () -> Unit,
    onDocumentClick: (docType: String, docId: String) -> Unit = { _, _ -> },
    onEditDocument: (docType: String, docId: String) -> Unit = { _, _ -> },
    bottomBar: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val coordinator = rememberSwipeCoordinator()
    var pendingDelete by remember { mutableStateOf<DocDisplay?>(null) }
    var hintDone by remember { mutableStateOf(homeSwipeHintShown) }
    LaunchedEffect(Unit) {
        delay(3000)
        homeSwipeHintShown = true
        hintDone = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        topBar = {
            CadyAppBar(
                title = { Text("كادي") },
                // navigationIcon (the START slot) lands on the visual right in this app's forced-RTL
                // layout — actions would put it on the left instead, which is wrong per the agreed
                // "top-right settings icon" decision.
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") { TodayHero(state) }

            item(key = "quick") {
                // A plain 2x2 layout instead of a lazy grid: there are always exactly 4 tiles, and each row
                // is sized by its own content, so nothing is ever clipped on a short screen.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile("بيع", "فاتورة جديدة", Icons.Filled.Payments, MaterialTheme.cady.sale, onNewSale, Modifier.weight(1f))
                        QuickActionTile("مرتجع", "إرجاع بضاعة", Icons.Filled.Replay, MaterialTheme.cady.returns, onNewReturn, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionTile("سند قبض", "تحصيل مبلغ", Icons.Filled.Receipt, MaterialTheme.cady.receipt, onNewReceipt, Modifier.weight(1f))
                        QuickActionTile("عميل نقدي", "بيع سريع", Icons.Filled.PersonAdd, SwipeColors.Teal, onNewCashCustomerSale, Modifier.weight(1f))
                    }
                }
            }

            item(key = "recent-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("آخر العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        onClick = onViewAllDocuments,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "عرض الكل",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (state.loaded && state.recentActivity.isEmpty()) {
                item(key = "empty") {
                    CadyCard {
                        EmptyState(
                            icon = Icons.Filled.Receipt,
                            title = "لا توجد عمليات بعد",
                            message = "ابدأ بفاتورة بيع أو سند قبض وستظهر هنا فور حفظها.",
                            actionLabel = "فاتورة بيع جديدة",
                            onAction = onNewSale,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = state.recentActivity,
                    key = { _, item -> item.docType + item.docId },
                ) { index, item ->
                    val row = item.toDisplay(
                        amount = state.amounts[item.docId] ?: 0.0,
                        dateText = friendlyDateTime(item.date),
                        showRep = state.isManager,
                        hideAmount = state.hideAmounts,
                    )
                    DocumentListItem(
                        row = row,
                        coordinator = coordinator,
                        onOpen = { onDocumentClick(row.docType, row.id) },
                        onEdit = { onEditDocument(row.docType, row.id) },
                        onDelete = { pendingDelete = row },
                        onTogglePin = { viewModel.togglePin(row.docType, row.id) },
                        peek = index == 0 && !hintDone,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المستند؟") },
            text = {
                Text("سيُحذف «${row.typeLabel} ${row.docNumber} — ${row.customerName}» نهائيًا ويُعاد احتساب رصيد العميل. لا يمكن التراجع عن ذلك.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(row.docType, row.id); pendingDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }
}

/** Greeting, today's date and today's sales — the first thing seen on opening the app. */
@Composable
private fun TodayHero(state: HomeUiState) {
    val today = remember { LocalDate.now() }
    val dateLabel = remember(today) { "${weekdayName(today)} ${today.dayOfMonth} ${monthName(today.monthValue)}" }
    val name = state.currentUser?.displayName?.trim()?.takeIf { it.isNotEmpty() }
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (name != null) "مرحبًا، $name" else "مرحبًا",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
            if (state.isManager) HeroChip("حساب مدير", null)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.isManager) "مبيعات اليوم (كل المندوبين)" else "مبيعات اليوم",
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.85f),
        )
        Text(
            if (state.hideAmounts) "••••" else animatedMoneyText(state.today.sales),
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroChip("${state.today.salesCount} فاتورة", Icons.Filled.Payments)
            HeroChip("تحصيل " + if (state.hideAmounts) "••••" else formatCompact(state.today.collections), Icons.Filled.Receipt)
            if (state.today.returns > 0.0) {
                HeroChip("مرتجع " + if (state.hideAmounts) "••••" else formatCompact(state.today.returns), Icons.Filled.Undo)
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, icon: ImageVector?) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.width(14.dp).height(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** A big, thumb-sized shortcut: coloured icon on top, name and a one-line hint below. */
@Composable
private fun QuickActionTile(
    label: String,
    hint: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CadyCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon = icon, tint = Color.White, container = accent, size = 44.dp, cornerRadius = 14.dp)
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
