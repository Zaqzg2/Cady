package com.cady.cadysalesapp.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.formatDateTime

@Composable
fun ManagerSyncHubScreen(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLiveActivity: () -> Unit,
    onLog: () -> Unit,
    onUsers: () -> Unit,
    onDeviceSync: () -> Unit,
    viewModel: ManagerSyncHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مركز المزامنة") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard("المندوبون النشطون", "${state.activeReps} / ${state.reps.size}", Modifier.weight(1f))
                        StatCard("عمليات اليوم", "${state.operationsToday}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard("أخطاء (7 أيام)", "${state.problems}", Modifier.weight(1f))
                        StatCard("منتجات معلّقة", "${state.pendingProducts}", Modifier.weight(1f))
                    }
                }
            }

            item { Text("الإجراءات", style = MaterialTheme.typography.titleMedium) }
            item { ActionCard("استيراد من مندوب", "ملف مزامنة وصلك من مندوب — مع معاينة قبل الاعتماد", onImport) }
            item { ActionCard("إنشاء تحديث", "منتجات وعملاء وإعدادات تُرسَل للمندوبين", onExport) }
            item { ActionCard("النشاط المباشر", "ما رفعه المندوبون للسحابة الآن", onLiveActivity) }
            item { ActionCard("سجل المزامنة", "كل عمليات الاستيراد والتصدير", onLog) }
            item { ActionCard("المندوبون", "الحسابات والتفعيل ونشاط اليوم", onUsers) }
            item { ActionCard("مزامنة هذا الجهاز", "رفع/سحب بيانات جهازك مع Firebase", onDeviceSync) }

            item { Text("حالة المندوبين", style = MaterialTheme.typography.titleMedium) }
            if (state.reps.isEmpty()) {
                item {
                    Text(
                        "لا يوجد مندوبون بعد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.reps, key = { it.id }) { rep ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(14.dp).clip(CircleShape).background(syncRecencyColor(rep.lastSyncAt)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rep.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                syncRecencyText(rep.lastSyncAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!rep.isActive) {
                            Text("موقوف", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Text(
                    "الأخضر: مزامنة خلال 24 ساعة · الأصفر: خلال 3 أيام · الأحمر: أقدم أو لم تتم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.lastActivity?.let { last ->
                item {
                    Text(
                        "آخر نشاط: ${last.title} — ${formatDateTime(last.at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
