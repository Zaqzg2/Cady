package com.cady.cadysalesapp.ui.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.OfferEntity
import com.cady.cadysalesapp.data.local.entity.ProductEntity
import com.cady.cadysalesapp.domain.OfferRules
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadySearchField
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.Pill
import com.cady.cadysalesapp.ui.common.ProductIconCircle
import com.cady.cadysalesapp.ui.common.QuickAction
import com.cady.cadysalesapp.ui.common.QuickActionsSheet
import com.cady.cadysalesapp.ui.common.SwipeAction
import com.cady.cadysalesapp.ui.common.SwipeActionRow
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.matchesSearch
import com.cady.cadysalesapp.ui.common.parseNumber
import com.cady.cadysalesapp.ui.common.pressScale
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator
import com.cady.cadysalesapp.ui.theme.cady
import java.time.Instant

/**
 * The price list is the manager's to edit — reps only read it (the cloud rules
 * already refuse a rep's write, so an edit button for them could only fail).
 * [canEdit] is true for a manager account; [onOffersClick] (also manager only) opens the offers screen.
 *
 * For a manager each product also answers to gestures: tap → edit, swipe RIGHT → edit / delete,
 * long-press → the quick menu (edit, offers, delete).
 */
@Composable
fun ProductsScreen(
    canEdit: Boolean = false,
    onOffersClick: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsState()
    val offers by viewModel.offers.collectAsState()
    var query by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProductEntity?>(null) }
    val coordinator = rememberSwipeCoordinator()

    val now = remember(offers) { Instant.now() }
    val shown = remember(products, query) { products.filter { matchesSearch(it.name, query) } }
    val runningOffers = remember(products, offers, now) { products.count { OfferRules.bestFor(it.id, offers, now) != null } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text("المنتجات") },
                actions = {
                    if (onOffersClick != null) {
                        IconButton(onClick = onOffersClick) {
                            Icon(Icons.Filled.LocalOffer, contentDescription = "العروض")
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { editingProduct = null; showDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("منتج جديد") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (products.isNotEmpty()) {
                item(key = "search") {
                    CadySearchField(value = query, onValueChange = { query = it }, placeholder = "بحث عن منتج")
                }
                item(key = "summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${shown.size} منتج",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (runningOffers > 0) {
                            Pill("$runningOffers عرض ساري", MaterialTheme.cady.warning, icon = Icons.Filled.LocalOffer)
                        }
                    }
                }
            }
            if (products.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Inventory2,
                        title = "لا توجد منتجات بعد",
                        message = if (canEdit) "أضف أول منتج لتبدأ بإصدار الفواتير." else "سيضيف المدير المنتجات وتظهر هنا.",
                        actionLabel = if (canEdit) "إضافة منتج" else null,
                        onAction = if (canEdit) { { editingProduct = null; showDialog = true } } else null,
                    )
                }
            } else if (shown.isEmpty()) {
                item(key = "no-match") {
                    EmptyState(icon = Icons.Filled.Inventory2, title = "لا يوجد منتج بهذا الاسم")
                }
            }
            items(shown, key = { it.id }) { product ->
                ProductListItem(
                    product = product,
                    offer = OfferRules.bestFor(product.id, offers, now),
                    canEdit = canEdit,
                    coordinator = coordinator,
                    onEdit = { editingProduct = product; showDialog = true },
                    onDelete = { pendingDelete = product },
                    onOffers = onOffersClick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showDialog && canEdit) {
        ProductEditDialog(
            existing = editingProduct,
            onDismiss = { showDialog = false },
            onSave = { name, price, unit ->
                viewModel.saveProduct(editingProduct, name, price, unit)
                showDialog = false
            },
            onDelete = editingProduct?.let { product ->
                { viewModel.deleteProduct(product); showDialog = false }
            },
        )
    }

    pendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المنتج؟") },
            text = { Text("سيُحذف «${product.name}» مع أي عروض عليه. الفواتير القديمة تبقى كما هي.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProduct(product); pendingDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    offer: OfferEntity?,
    canEdit: Boolean,
    coordinator: com.cady.cadysalesapp.ui.common.SwipeCoordinator,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOffers: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var sheetOpen by remember { mutableStateOf(false) }

    SwipeActionRow(
        modifier = modifier,
        rowKey = product.id,
        coordinator = coordinator,
        enabled = canEdit,
        leftActions = if (canEdit) {
            listOf(
                SwipeAction("تعديل", Icons.Filled.Edit, SwipeColors.Slate, onClick = onEdit),
                SwipeAction("حذف", Icons.Filled.Delete, SwipeColors.Red, onClick = onDelete),
            )
        } else {
            emptyList()
        },
    ) {
        ProductRowCard(
            product = product,
            offer = offer,
            onClick = if (canEdit) onEdit else null,
            onLongClick = if (canEdit) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sheetOpen = true
                }
            } else {
                null
            },
        )
    }

    if (sheetOpen) {
        val actions = buildList<QuickAction> {
            add(QuickAction("تعديل المنتج", Icons.Filled.Edit, onClick = onEdit))
            if (onOffers != null) add(QuickAction("العروض", Icons.Filled.LocalOffer, onClick = onOffers))
            add(QuickAction("حذف", Icons.Filled.Delete, destructive = true, onClick = onDelete))
        }
        QuickActionsSheet(
            title = product.name,
            subtitle = "${formatMoney(product.price)} — ${product.unit}",
            leading = { ProductIconCircle(imagePath = product.imagePath, name = product.name, size = 44.dp) },
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun ProductRowCard(
    product: ProductEntity,
    offer: OfferEntity?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = clickModifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductIconCircle(imagePath = product.imagePath, name = product.name, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (offer == null) {
                    Text(
                        formatMoney(product.price),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMoney(OfferRules.discountedPrice(product.price, offer.percent)),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatMoney(product.price),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                if (product.unit.isNotBlank()) {
                    Pill(product.unit, scheme.onSurfaceVariant, container = scheme.surfaceContainerHigh)
                }
            }
            if (offer != null) {
                Surface(shape = RoundedCornerShape(50), color = scheme.tertiaryContainer) {
                    Text(
                        "عرض −${OfferRules.percentText(offer.percent)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductEditDialog(
    existing: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, unit: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember { mutableStateOf(existing?.price?.let { formatQuantity(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedPrice = parseNumber(price)
    val canSave = name.isNotBlank() && parsedPrice != null && parsedPrice >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "منتج جديد" else "تعديل المنتج") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("السعر") },
                    singleLine = true,
                    keyboardOptions = DecimalKeyboard,
                    isError = price.isNotBlank() && parsedPrice == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("الوحدة (قطعة/كرتون/عبوة)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { confirmDelete = true }) { Text("حذف المنتج", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), parsedPrice ?: 0.0, unit.trim()) }, enabled = canSave) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف المنتج؟") },
            text = { Text("سيُحذف «${existing?.name.orEmpty()}» مع أي عروض عليه. الفواتير القديمة تبقى كما هي.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}
