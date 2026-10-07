package com.cady.cadysalesapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cady.cadysalesapp.data.local.entity.CustomerEntity

// ---------------------------------------------------------------- search matching

/** Lower-cases and folds the spellings Arabic users treat as the same letter, so a search finds "أحمد" by typing "احمد". */
fun normalizeForSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text.lowercase()) {
        when (ch) {
            in '\u064B'..'\u065F', '\u0670', '\u0640' -> Unit // diacritics and tatweel
            '\u0623', '\u0625', '\u0622' -> out.append('\u0627') // أ إ آ → ا
            '\u0629' -> out.append('\u0647') // ة → ه
            '\u0649' -> out.append('\u064A') // ى → ي
            in '\u0660'..'\u0669' -> out.append('0' + (ch - '\u0660'))
            in '\u06F0'..'\u06F9' -> out.append('0' + (ch - '\u06F0'))
            else -> out.append(ch)
        }
    }
    return out.toString()
}

/** Every word of [query] must appear somewhere in [text] (order does not matter). */
fun matchesSearch(text: String, query: String): Boolean {
    val words = normalizeForSearch(query).split(' ', '\t').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = normalizeForSearch(text)
    return words.all { haystack.contains(it) }
}

// ---------------------------------------------------------------- the picker popup

/**
 * A popup list with a filter field at the very top (no title — the space goes to the list).
 * Used wherever a long list has to be chosen from by touch: customers on invoices and receipts,
 * products on invoices, the product of an offer.
 *
 * [itemContent] draws one row and is responsible for its own tap handling, so the same popup serves
 * "tap to choose and close" (customers) and "stay open and adjust quantities" (products).
 */
@Composable
fun <T> SearchPickerDialog(
    items: List<T>,
    searchText: (T) -> String,
    onDismiss: () -> Unit,
    searchLabel: String = "بحث",
    emptyText: String = "لا توجد نتائج مطابقة",
    itemKey: ((T) -> Any)? = null,
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(items, query) {
        if (query.isBlank()) items else items.filter { matchesSearch(searchText(it), query) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.8f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(searchLabel) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                            }
                        }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (shown.isEmpty()) {
                        Text(
                            emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(shown, key = itemKey) { item ->
                                itemContent(item)
                                HorizontalDivider()
                            }
                        }
                    }
                }
                if (footer != null) {
                    Spacer(Modifier.height(8.dp))
                    footer()
                }
            }
        }
    }
}

/** Choose one customer: tap the row. Customers the manager/rep switched off are not offered. */
@Composable
fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    onPick: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    SearchPickerDialog(
        items = customers.filter { it.isActive },
        searchText = { listOfNotNull(it.name, it.phone, it.address).joinToString(" ") },
        onDismiss = onDismiss,
        searchLabel = "بحث بالاسم أو الهاتف",
        emptyText = "لا يوجد عملاء مطابقون",
        itemKey = { it.id },
        footer = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    ) { customer ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onPick(customer) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarInitial(customer.name, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(customer.phone, customer.address).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- quantity control

/**
 * "−  [ 3 ]  +": the buttons step by one, and the number can also be typed straight into the field
 * (decimals allowed, Arabic digits understood). An empty field means zero.
 */
@Composable
fun QuantityStepper(
    quantity: Double,
    onQuantityChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    /** A narrower field and smaller digits, for rows that also have to fit a name and a price. */
    compact: Boolean = false,
) {
    var text by remember { mutableStateOf(if (quantity > 0) formatQuantity(quantity) else "") }
    // The quantity can change from outside (the − / + buttons, the lines list): keep the field in step,
    // but never rewrite what is being typed while it already means the same number ("2." vs 2).
    LaunchedEffect(quantity) {
        val typed = parseNumber(text) ?: 0.0
        if (typed != quantity) text = if (quantity > 0) formatQuantity(quantity) else ""
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onQuantityChange((quantity - 1).coerceAtLeast(0.0)) },
            enabled = quantity > 0,
        ) { Icon(Icons.Filled.Remove, contentDescription = "إنقاص") }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                onQuantityChange((parseNumber(new) ?: 0.0).coerceAtLeast(0.0))
            },
            singleLine = true,
            keyboardOptions = DecimalKeyboard,
            textStyle = (if (compact) MaterialTheme.typography.bodyMedium else LocalTextStyle.current)
                .copy(textAlign = TextAlign.Center),
            placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            modifier = Modifier.width(if (compact) 68.dp else 84.dp),
        )
        FilledTonalIconButton(onClick = { onQuantityChange(quantity + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "زيادة")
        }
    }
}
