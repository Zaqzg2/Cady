package com.cady.cadysalesapp.ui.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.sync.LiveDoc
import com.cady.cadysalesapp.data.sync.LiveDocKind
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.common.formatMoney

@Composable
fun ManagerLiveActivityScreen(
    onBack: () -> Unit,
    onOpenDocument: (docType: String, docId: String) -> Unit,
    viewModel: ManagerLiveActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val repNames = state.reps.associate { it.id to it.displayName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النشاط المباشر") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RepFilterRow(
                reps = state.reps,
                selectedId = state.selectedRepId,
                onSelect = viewModel::selectRep,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveKindFilter.entries.forEach { option ->
                    FilterChip(selected = state.kind == option, onClick = { viewModel.selectKind(option) }, label = { Text(option.label) })
                }
            }

            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            state.message?.let { message ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        TextButton(onClick = viewModel::dismissMessage) { Text("إغلاق") }
                    }
                }
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                item {
                    Text(
                        "مباشر من السحابة — آخر 100 مستند. ما لم يرفعه المندوب بعد من جهازه لا يظهر هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (!state.loading && state.error == null && state.docs.isEmpty()) {
                    item { Text("لا توجد مستندات مطابقة.", style = MaterialTheme.typography.bodyMedium) }
                }
                items(state.docs, key = { "${it.kind}-${it.id}" }) { doc ->
                    LiveRow(
                        doc = doc,
                        repName = doc.repName ?: repNames[doc.repId],
                        onClick = { viewModel.open(doc, onOpenDocument) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LiveRow(doc: LiveDoc, repName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (doc.kind) {
                    LiveDocKind.SALE -> "فاتورة بيع"
                    LiveDocKind.RETURN -> "فاتورة مرتجع"
                    LiveDocKind.RECEIPT -> "سند قبض"
                } + " — ${doc.docNumber}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                doc.customerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(repName, formatDateTime(doc.date)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatMoney(doc.amount), style = MaterialTheme.typography.titleMedium)
    }
}
