package com.cady.cadysalesapp.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.ProductEntity

@Composable
fun ProductsScreen(
    bottomBar: @Composable () -> Unit = {},
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsState()
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("المنتجات") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = { editingProduct = null; showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة منتج")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(products, key = { it.id }) { product ->
                Card(
                    onClick = { editingProduct = product; showDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${product.price} — ${product.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
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
}

@Composable
private fun ProductEditDialog(
    existing: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, unit: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember { mutableStateOf(existing?.price?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "منتج جديد" else "تعديل المنتج") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("السعر") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("الوحدة (قطعة/كرتون/عبوة)") }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDelete) { Text("حذف المنتج", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, price.toDoubleOrNull() ?: 0.0, unit) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
