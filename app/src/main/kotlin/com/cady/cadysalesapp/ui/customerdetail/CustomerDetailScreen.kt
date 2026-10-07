package com.cady.cadysalesapp.ui.customerdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.DocDisplay
import com.cady.cadysalesapp.ui.common.DocumentListItem
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.HeroCard
import com.cady.cadysalesapp.ui.common.Pill
import com.cady.cadysalesapp.ui.common.SectionTitle
import com.cady.cadysalesapp.ui.common.SpeedDialFab
import com.cady.cadysalesapp.ui.common.SpeedDialItem
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.animatedMoneyText
import com.cady.cadysalesapp.ui.common.docId
import com.cady.cadysalesapp.ui.common.friendlyDateTime
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.common.toDisplay
import com.cady.cadysalesapp.ui.home.RecentActivityItem

@Composable
fun CustomerDetailScreen(
    onNewInvoice: (kind: String) -> Unit,
    onNewReceipt: () -> Unit,
    onPreviewStatement: () -> Unit,
    onPrintStatement: () -> Unit,
    onDocumentClick: (type: String, id: String) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onMapClick: (address: String) -> Unit,
    onBack: () -> Unit = {},
    onEditDocument: ((type: String, id: String) -> Unit)? = null,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val customer = state.customer
    val coordinator = rememberSwipeCoordinator()
    var pendingDelete by remember { mutableStateOf<DocDisplay?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text(customer?.name.orEmpty(), maxLines = 1) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { viewModel.togglePin() }) {
                        Icon(
                            imageVector = if (customer?.isPinned == true) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "تثبيت",
                            tint = if (customer?.isPinned == true) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // One tap to the document wanted instead of a dropdown hanging off the corner.
            SpeedDialFab(
                items = listOf(
                    SpeedDialItem("سند قبض", Icons.Filled.Receipt, SwipeColors.Blue) { onNewReceipt() },
                    SpeedDialItem("فاتورة مرتجع", Icons.Filled.Replay, SwipeColors.Amber) { onNewInvoice("return") },
                    SpeedDialItem("فاتورة بيع", Icons.Filled.Payments, SwipeColors.Green) { onNewInvoice("sale") },
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                if (customer != null) {
                    BalanceHero(customer = customer, balance = balance)
                }
            }
            item(key = "actions") {
                if (customer != null) {
                    // Everything that can be done about this customer from outside the app, one tap each.
                    CadyCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            val phone = customer.phone?.takeIf { it.isNotBlank() }
                            val address = customer.address?.takeIf { it.isNotBlank() }
                            if (phone != null) {
                                ActionIcon(Icons.Filled.Call, "اتصال", SwipeColors.Green) { onCallClick(phone) }
                                ActionIcon(Icons.Filled.Chat, "واتساب", SwipeColors.Teal) { onWhatsAppClick(phone) }
                            }
                            if (address != null) {
                                ActionIcon(Icons.Filled.Place, "الموقع", SwipeColors.Amber) { onMapClick(address) }
                            }
                            ActionIcon(Icons.Filled.Description, "الكشف", SwipeColors.Blue) { onPreviewStatement() }
                            ActionIcon(Icons.Filled.Print, "طباعة", SwipeColors.Slate) { onPrintStatement() }
                        }
                    }
                }
            }
            item(key = "timeline-title") {
                SectionTitle(
                    text = "السجل",
                    modifier = Modifier.padding(top = 6.dp),
                    trailing = {
                        if (state.timeline.isNotEmpty()) {
                            Text(
                                "${state.timeline.size} مستند",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            if (customer != null && state.timeline.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "لا توجد مستندات لهذا العميل",
                        message = "اضغط + لإصدار أول فاتورة أو سند قبض له.",
                    )
                }
            }
            items(state.timeline, key = { (if (it is RecentActivityItem.Invoice) "i-" else "r-") + it.docId }) { item ->
                val row = item.toDisplay(
                    amount = state.amounts[item.docId] ?: 0.0,
                    dateText = friendlyDateTime(item.date),
                    withBalance = true,
                )
                DocumentListItem(
                    row = row,
                    coordinator = coordinator,
                    onOpen = { onDocumentClick(row.docType, row.id) },
                    onEdit = onEditDocument?.let { edit -> { edit(row.docType, row.id) } },
                    onDelete = { pendingDelete = row },
                    onTogglePin = { viewModel.togglePinDocument(row.docType, row.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المستند؟") },
            text = {
                Text("سيُحذف «${row.typeLabel} ${row.docNumber}» نهائيًا ويُعاد احتساب رصيد العميل. لا يمكن التراجع عن ذلك.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteDocument(row.docType, row.id); pendingDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }
}

/** The customer, and — big — what they owe (or are owed). The number counts up when the page opens. */
@Composable
private fun BalanceHero(customer: CustomerEntity, balance: Double) {
    val letter = customer.name.trim().firstOrNull { it.isLetterOrDigit() }?.toString() ?: "؟"
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val sub = customer.phone?.takeIf { it.isNotBlank() } ?: customer.address?.takeIf { it.isNotBlank() }
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f), maxLines = 1)
                }
            }
            if (!customer.isActive) {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                    Text("موقوف", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                balance > 0.005 -> "مستحق على العميل"
                balance < -0.005 -> "رصيد للعميل"
                else -> "الرصيد الحالي"
            },
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.85f),
        )
        Text(
            animatedMoneyText(kotlin.math.abs(balance)),
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
            Text(
                when {
                    balance > 0.005 -> "عليه دين"
                    balance < -0.005 -> "له رصيد دائن"
                    else -> "الحساب مسدّد"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** A round, coloured icon button with its name underneath. */
@Composable
private fun ActionIcon(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}
