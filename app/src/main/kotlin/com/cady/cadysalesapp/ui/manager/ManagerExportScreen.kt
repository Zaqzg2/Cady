package com.cady.cadysalesapp.ui.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.launchShare
import com.cady.cadysalesapp.ui.common.summaryText

@Composable
fun ManagerExportScreen(
    onBack: () -> Unit,
    viewModel: ManagerExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إنشاء تحديث") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "ملف يُرسَل للمندوب (واتساب أو غيره) فيستورده من شاشة المزامنة عنده. لا يحتاج الجهازان إنترنت.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("إلى من؟", style = MaterialTheme.typography.titleSmall)
            RepFilterRow(reps = state.reps, selectedId = state.targetRepId, onSelect = viewModel::selectTarget)
            if (state.targetRepId == null) {
                Text(
                    "ملف واحد لكل المندوبين: كل مندوب يأخذ منه عملاءه هو فقط.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("ماذا يتضمّن؟", style = MaterialTheme.typography.titleSmall)
            OptionRow("العملاء", "بياناتهم كما عندك الآن (الأحدث يغلب)", state.includeCustomers, viewModel::setCustomers)
            OptionRow("المنتجات والأسعار", "قائمة المنتجات كاملة", state.includeProducts, viewModel::setProducts)
            OptionRow("بيانات الشركة", "الاسم والعنوان والهاتف وتذييل الفاتورة", state.includeSettings, viewModel::setSettings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("العروض", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("غير متاحة حاليًا", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = false, onCheckedChange = null, enabled = false)
            }

            state.message?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            val result = state.result
            if (result == null) {
                Button(onClick = viewModel::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "جارٍ التجهيز…" else "إنشاء الملف")
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الملف جاهز", style = MaterialTheme.typography.titleMedium)
                        Text(result.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val parts = listOfNotNull(
                            result.counts.summaryText().takeIf { result.counts.total > 0 },
                            "بيانات الشركة".takeIf { result.includesSettings },
                        )
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = { context.launchShare(result.share) }, modifier = Modifier.fillMaxWidth()) { Text("إرسال الملف") }
                OutlinedButton(onClick = viewModel::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("إنشاء ملف جديد") }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
