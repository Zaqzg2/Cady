package com.cady.cadysalesapp.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.launchShare

@Composable
fun SyncOutboxInboxScreen(
    onBack: () -> Unit,
    /** The manager's "سجل المزامنة" is this same screen under another title, without the rep's wait-for-confirmation line. */
    title: String = "الصادر والوارد",
    showAckStatus: Boolean = true,
    viewModel: SyncOutboxInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { context.launchShare(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LogFilter.entries.forEach { option ->
                            FilterChip(
                                selected = state.filter == option,
                                onClick = { viewModel.select(option) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                if (state.rows.isEmpty()) {
                    item {
                        Text(
                            if (state.totalCount == 0) "لا يوجد نشاط مسجّل بعد." else "لا توجد عناصر ضمن هذا التصنيف.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else {
                    items(state.rows, key = { it.entry.id }) { row ->
                        ActivityEntryCard(
                            entry = row.entry,
                            onResend = if (row.canResend) ({ viewModel.resend(row.entry) }) else null,
                            showAckStatus = showAckStatus,
                        )
                    }
                    item {
                        Text(
                            "يحتفظ التطبيق بآخر ${com.cady.cadysalesapp.data.sync.SyncActivityStore.MAX_ENTRIES} عملية فقط.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
