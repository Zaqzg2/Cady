package com.cady.cadysalesapp.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.SyncStatus
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import com.cady.cadysalesapp.ui.theme.cady

/** The three kinds of document — each has its own colour and icon everywhere in the app. */
enum class DocKind { SALE, RETURN, RECEIPT }

/** Everything one document row needs to draw itself, already formatted. */
@Immutable
data class DocDisplay(
    val id: String,
    /** "invoice" or "receipt" — the same strings the navigation routes use. */
    val docType: String,
    val kind: DocKind,
    val typeLabel: String,
    val customerName: String,
    val docNumber: String,
    val dateText: String,
    val amount: Double,
    val repName: String? = null,
    val isPinned: Boolean = false,
    val isPending: Boolean = false,
    /** "نقد" / "آجل" on a sale; null on everything else. */
    val payment: String? = null,
    /** The customer's balance right after this document (shown in a customer's own timeline). */
    val balanceAfter: Double? = null,
    /** The "hide amounts in recent operations" privacy setting: the amount is drawn as dots. */
    val amountHidden: Boolean = false,
)

val RecentActivityItem.docId: String
    get() = when (this) {
        is RecentActivityItem.Invoice -> invoice.id
        is RecentActivityItem.Receipt -> receipt.id
    }

val RecentActivityItem.docType: String
    get() = when (this) {
        is RecentActivityItem.Invoice -> "invoice"
        is RecentActivityItem.Receipt -> "receipt"
    }

val RecentActivityItem.isPinned: Boolean
    get() = when (this) {
        is RecentActivityItem.Invoice -> invoice.isPinned
        is RecentActivityItem.Receipt -> receipt.isPinned
    }

val RecentActivityItem.ownerUid: String
    get() = when (this) {
        is RecentActivityItem.Invoice -> invoice.ownerUid
        is RecentActivityItem.Receipt -> receipt.ownerUid
    }

fun RecentActivityItem.toDisplay(
    amount: Double,
    dateText: String,
    showRep: Boolean = false,
    withBalance: Boolean = false,
    hideAmount: Boolean = false,
): DocDisplay = when (this) {
    is RecentActivityItem.Invoice -> DocDisplay(
        id = invoice.id,
        docType = "invoice",
        kind = if (invoice.kind == InvoiceKind.SALE) DocKind.SALE else DocKind.RETURN,
        typeLabel = if (invoice.kind == InvoiceKind.SALE) "فاتورة بيع" else "فاتورة مرتجع",
        customerName = invoice.customerName,
        docNumber = invoice.docNumber,
        dateText = dateText,
        amount = amount,
        repName = if (showRep) invoice.repName?.takeIf { it.isNotBlank() } else null,
        isPinned = invoice.isPinned,
        isPending = invoice.syncStatus == SyncStatus.PENDING,
        payment = if (invoice.kind == InvoiceKind.SALE) {
            if (invoice.paymentMode == PaymentMode.CASH) "نقد" else "آجل"
        } else {
            null
        },
        balanceAfter = if (withBalance) invoice.balanceAfter else null,
        amountHidden = hideAmount,
    )
    is RecentActivityItem.Receipt -> DocDisplay(
        id = receipt.id,
        docType = "receipt",
        kind = DocKind.RECEIPT,
        typeLabel = "سند قبض",
        customerName = receipt.customerName,
        docNumber = receipt.docNumber,
        dateText = dateText,
        amount = amount,
        repName = if (showRep) receipt.repName?.takeIf { it.isNotBlank() } else null,
        isPinned = receipt.isPinned,
        isPending = receipt.syncStatus == SyncStatus.PENDING,
        payment = null,
        balanceAfter = if (withBalance) receipt.balanceAfter else null,
        amountHidden = hideAmount,
    )
}

val DocKind.icon: ImageVector
    get() = when (this) {
        DocKind.SALE -> Icons.Filled.Payments
        DocKind.RETURN -> Icons.Filled.Replay
        DocKind.RECEIPT -> Icons.Filled.Receipt
    }

@Composable
@ReadOnlyComposable
fun DocKind.accent(): Color = when (this) {
    DocKind.SALE -> MaterialTheme.cady.sale
    DocKind.RETURN -> MaterialTheme.cady.returns
    DocKind.RECEIPT -> MaterialTheme.cady.receipt
}

@Composable
@ReadOnlyComposable
fun DocKind.accentContainer(): Color = when (this) {
    DocKind.SALE -> MaterialTheme.cady.saleContainer
    DocKind.RETURN -> MaterialTheme.cady.returnsContainer
    DocKind.RECEIPT -> MaterialTheme.cady.receiptContainer
}

/**
 * One document as a card: a coloured type tile (a check circle while selecting), the customer, "type · number",
 * when, small pills (rep, cash/credit, waiting-to-sync), and the amount — signed and coloured by kind.
 * Tap and long-press are the caller's; swipe buttons wrap it ([DocumentListItem] does both).
 */
@Composable
fun DocumentRowCard(
    row: DocDisplay,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = row.kind.accent()
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        label = "docRowContainer",
    )
    Surface(
        modifier = modifier.fillMaxWidth().pressScale(interaction),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = selectionMode, label = "docRowLeading") { inSelection ->
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    if (inSelection) {
                        Icon(
                            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) scheme.primary else scheme.outline,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        IconTile(icon = row.kind.icon, tint = accent, container = row.kind.accentContainer(), size = 46.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.isPinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.PushPin, contentDescription = "مثبّت", tint = scheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    "${row.typeLabel} · ${row.docNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                if (row.repName != null || row.payment != null || row.isPending) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (row.payment != null) {
                            Pill(row.payment, if (row.payment == "نقد") MaterialTheme.cady.success else MaterialTheme.cady.warning)
                        }
                        if (row.repName != null) Pill(row.repName, scheme.primary)
                        if (row.isPending) {
                            Pill("بانتظار المزامنة", MaterialTheme.cady.warning, icon = Icons.Filled.CloudUpload)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val sign = when (row.kind) {
                    DocKind.SALE -> ""
                    DocKind.RETURN -> "−"
                    DocKind.RECEIPT -> "+"
                }
                Text(
                    if (row.amountHidden) "••••" else sign + formatMoney(row.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (row.kind == DocKind.SALE) scheme.onSurface else accent,
                    maxLines = 1,
                )
                if (row.balanceAfter != null && !row.amountHidden) {
                    Text(
                        "الرصيد ${formatMoney(row.balanceAfter)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A document row with all of its gestures:
 *  - tap → [onOpen] (or toggles the selection while selecting);
 *  - swipe LEFT → print / pin;  swipe RIGHT → edit / delete;
 *  - long-press → the quick menu (open, edit, pin, select several, delete) — or toggles while selecting.
 * Any callback left null simply removes that button / menu line.
 */
@Composable
fun DocumentListItem(
    row: DocDisplay,
    coordinator: SwipeCoordinator,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    peek: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onSelectMode: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    var sheetOpen by remember { mutableStateOf(false) }

    val rightActions = buildList<SwipeAction> {
        add(SwipeAction("طباعة", Icons.Filled.Print, SwipeColors.Blue, onClick = onOpen))
        if (onTogglePin != null) {
            add(
                SwipeAction(
                    label = if (row.isPinned) "فك التثبيت" else "تثبيت",
                    icon = Icons.Filled.PushPin,
                    container = SwipeColors.Amber,
                    onClick = onTogglePin,
                )
            )
        }
    }
    val leftActions = buildList<SwipeAction> {
        if (onEdit != null) add(SwipeAction("تعديل", Icons.Filled.Edit, SwipeColors.Slate, onClick = onEdit))
        if (onDelete != null) add(SwipeAction("حذف", Icons.Filled.Delete, SwipeColors.Red, onClick = onDelete))
    }

    SwipeActionRow(
        modifier = modifier,
        rowKey = row.docType + row.id,
        coordinator = coordinator,
        leftActions = leftActions,
        rightActions = rightActions,
        enabled = !selectionMode,
        peek = peek,
    ) {
        DocumentRowCard(
            row = row,
            selectionMode = selectionMode,
            selected = selected,
            onClick = { if (selectionMode) onToggleSelect?.invoke() else onOpen() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (selectionMode) onToggleSelect?.invoke() else sheetOpen = true
            },
        )
    }

    if (sheetOpen) {
        val actions = buildList<QuickAction> {
            add(QuickAction("معاينة وطباعة", Icons.Filled.Print, onClick = onOpen))
            if (onEdit != null) add(QuickAction("تعديل", Icons.Filled.Edit, onClick = onEdit))
            if (onTogglePin != null) {
                add(
                    QuickAction(
                        label = if (row.isPinned) "إلغاء التثبيت" else "تثبيت في الأعلى",
                        icon = Icons.Filled.PushPin,
                        onClick = onTogglePin,
                    )
                )
            }
            if (onSelectMode != null) add(QuickAction("تحديد متعدد", Icons.Filled.CheckCircle, onClick = onSelectMode))
            if (onDelete != null) add(QuickAction("حذف", Icons.Filled.Delete, destructive = true, onClick = onDelete))
        }
        QuickActionsSheet(
            title = row.customerName,
            subtitle = "${row.typeLabel} · ${row.docNumber}" + if (row.amountHidden) "" else " · ${formatMoney(row.amount)}",
            leading = {
                IconTile(icon = row.kind.icon, tint = row.kind.accent(), container = row.kind.accentContainer(), size = 44.dp)
            },
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }
}
