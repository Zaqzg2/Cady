package com.cady.cadysalesapp.ui.manager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ManagerEntry(val title: String, val subtitle: String, val onClick: () -> Unit)

/**
 * The manager's front page, inside the same app and the same bottom bar as the
 * rep's screens — it only appears (as a fifth tab) for a manager account.
 * It is the hub the manager's tools hang off; each entry opens its own screen.
 */
@Composable
fun ManagerDashboardScreen(
    displayName: String,
    onUsersClick: () -> Unit,
    onLiveActivityClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onSyncLogClick: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val entries = listOf(
        ManagerEntry("المندوبون", "إضافة المندوبين وإدارة حساباتهم", onUsersClick),
        ManagerEntry("النشاط المباشر", "فواتير وسندات المندوبين فور وصولها", onLiveActivityClick),
        ManagerEntry("استيراد من مندوب", "استلام ملف مزامنة من مندوب ومراجعته", onImportClick),
        ManagerEntry("إنشاء تحديث", "إرسال المنتجات والعملاء والإعدادات للمندوبين", onExportClick),
        ManagerEntry("سجل المزامنة", "كل عمليات الاستيراد والتصدير في مكان واحد", onSyncLogClick),
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("لوحة المدير") }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مرحبًا $displayName", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "أنت مسجّل بحساب مدير — هذه الصفحة وأدواتها لا تظهر للمندوبين",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(entries) { entry ->
                Card(
                    onClick = entry.onClick,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
