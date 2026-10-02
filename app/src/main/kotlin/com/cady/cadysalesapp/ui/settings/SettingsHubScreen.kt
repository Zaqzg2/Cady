package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHubScreen(
    accountName: String?,
    isManager: Boolean,
    onCompanyClick: () -> Unit,
    onPrintingClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onDataClick: () -> Unit,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit,
    onManagerClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }

    val entries = buildList<Pair<String, () -> Unit>> {
        if (isManager) add("لوحة المدير" to onManagerClick)
        add("بيانات الشركة" to onCompanyClick)
        add("الطباعة" to onPrintingClick)
        add("المظهر" to onAppearanceClick)
        add("الخصوصية" to onPrivacyClick)
        add("حجم البيانات" to onDataClick)
        add("المزامنة" to onSyncClick)
        add("النسخ الاحتياطي" to onBackupClick)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("الإعدادات") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            // Who is signed in — and therefore which of the two roles' screens are showing.
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(accountName ?: "—", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isManager) "حساب مدير" else "حساب مندوب",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(entries) { (label, onClick) ->
                Card(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
            item {
                Card(
                    onClick = { confirmLogout = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(
                        "تسجيل الخروج",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("تسجيل الخروج") },
            text = { Text("ستعود لشاشة الدخول، ولن تُحذف أي بيانات من هذا الجهاز.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogoutClick() }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("إلغاء") } },
        )
    }
}
