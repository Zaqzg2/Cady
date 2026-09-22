package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsDataScreen(viewModel: SettingsDataViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("حجم البيانات") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val current = stats
            if (current == null) {
                CircularProgressIndicator()
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("حجم قاعدة البيانات", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(current.databaseSizeBytes), style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(16.dp))

                StatRow("العملاء", current.customersCount)
                StatRow("المنتجات", current.productsCount)
                StatRow("الفواتير", current.invoicesCount)
                StatRow("السندات", current.receiptsCount)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(count.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes بايت"
    bytes < 1024 * 1024 -> "${(bytes / 1024.0 * 10).roundToInt() / 10.0} كيلوبايت"
    else -> "${(bytes / (1024.0 * 1024.0) * 10).roundToInt() / 10.0} ميجابايت"
}
