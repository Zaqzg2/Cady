package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHubScreen(
    onCompanyClick: () -> Unit,
    onPrintingClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onDataClick: () -> Unit,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit,
) {
    val items = listOf(
        "بيانات الشركة" to onCompanyClick,
        "الطباعة" to onPrintingClick,
        "المظهر" to onAppearanceClick,
        "الخصوصية" to onPrivacyClick,
        "حجم البيانات" to onDataClick,
        "المزامنة" to onSyncClick,
        "النسخ الاحتياطي" to onBackupClick,
    )

    Scaffold(topBar = { TopAppBar(title = { Text("الإعدادات") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(items) { (label, onClick) ->
                Card(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
