package com.cady.cadysalesapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.home.RecentActivityItem
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onNewSale: () -> Unit,
    onNewReturn: () -> Unit,
    onNewReceipt: () -> Unit,
    onNewCashCustomerSale: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewAllDocuments: () -> Unit,
    onDocumentClick: (docType: String, docId: String) -> Unit = { _, _ -> },
    bottomBar: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("كادي") },
                // navigationIcon (the START slot) lands on the visual right in
                // this app's forced-RTL layout — actions would put it on the
                // left instead, which is what actually shipped and is wrong
                // per the agreed "top-right settings icon" decision.
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "الإعدادات",
                        modifier = Modifier.clickable { onSettingsClick() }.padding(12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // A plain 2x2 layout instead of a LazyVerticalGrid: there are always
            // exactly 4 cards, so "lazy" bought nothing but a hardcoded container
            // height that was too short for 2 rows of aspectRatio(1.6f) cards plus
            // spacing on most phone widths — the bottom row was being clipped.
            // Sizing each row by its own content (no fixed height at all) fixes
            // that on every screen size instead of just picking a taller guess.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard("بيع", Icons.Filled.Payments, onNewSale, modifier = Modifier.weight(1f))
                    QuickActionCard("مرتجع", Icons.Filled.Undo, onNewReturn, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard("سند قبض", Icons.Filled.Receipt, onNewReceipt, modifier = Modifier.weight(1f))
                    QuickActionCard("عميل نقدي", Icons.Filled.PersonAdd, onNewCashCustomerSale, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("آخر العمليات", style = MaterialTheme.typography.titleMedium)
                Text(
                    "عرض الكل",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onViewAllDocuments() },
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.recentActivity) { item ->
                    val (docType, docId) = when (item) {
                        is RecentActivityItem.Invoice -> "invoice" to item.invoice.id
                        is RecentActivityItem.Receipt -> "receipt" to item.receipt.id
                    }
                    RecentActivityRow(item, onClick = { onDocumentClick(docType, docId) })
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.aspectRatio(1.6f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Tap opens the document; a left-or-right drag past the threshold opens it
    too (a lightweight "swipe" affordance) — `triggered` guards against firing
    onClick more than once per gesture, since onHorizontalDrag keeps firing
    for every pointer-move event while the finger stays past the threshold. */
@Composable
private fun RecentActivityRow(item: RecentActivityItem, onClick: () -> Unit) {
    val (title, subtitle) = when (item) {
        is RecentActivityItem.Invoice -> item.invoice.customerName to item.invoice.docNumber
        is RecentActivityItem.Receipt -> item.receipt.customerName to item.receipt.docNumber
    }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(item) {
                detectHorizontalDragGestures(
                    onDragStart = { triggered = false },
                    onDragEnd = { offsetX = 0f },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-80f, 80f)
                        if (!triggered && abs(offsetX) > 60f) {
                            triggered = true
                            onClick()
                        }
                    },
                )
            }
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
