package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.HeroCard
import com.cady.cadysalesapp.ui.common.IconTile
import com.cady.cadysalesapp.ui.common.SwipeColors

private class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
)

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
    onLogoutClick: () -> Unit,
    onBack: () -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }

    val company = listOf(
        SettingsEntry("بيانات الشركة", "الاسم والشعار والعنوان والتوقيع", Icons.Filled.Business, SwipeColors.Blue, onCompanyClick),
        SettingsEntry("الطباعة", "شكل المطبوعات والطابعة المحفوظة", Icons.Filled.Print, SwipeColors.Slate, onPrintingClick),
    )
    val app = listOf(
        SettingsEntry("المظهر", "الوضع الفاتح أو الداكن ولون الهوية", Icons.Filled.Palette, SwipeColors.Amber, onAppearanceClick),
        SettingsEntry("الخصوصية", "قفل التطبيق والبصمة وإخفاء المبالغ", Icons.Filled.Lock, SwipeColors.Red, onPrivacyClick),
    )
    val data = listOf(
        SettingsEntry("حجم البيانات", "ما تشغله قاعدة البيانات على الجهاز", Icons.Filled.Storage, SwipeColors.Teal, onDataClick),
        SettingsEntry("المزامنة", "إرسال البيانات واستقبالها", Icons.Filled.Sync, SwipeColors.Green, onSyncClick),
        SettingsEntry("النسخ الاحتياطي", "نسخ البيانات واستعادتها", Icons.Filled.Backup, SwipeColors.Blue, onBackupClick),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Who is signed in — and therefore which of the two roles' screens are showing.
            item(key = "profile") {
                val letter = accountName?.trim()?.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "؟"
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(letter, style = MaterialTheme.typography.titleLarge) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(accountName ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    if (isManager) "حساب مدير" else "حساب مندوب",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalContentColor.current,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "group-company") { SettingsGroup("الشركة والطباعة", company) }
            item(key = "group-app") { SettingsGroup("التطبيق", app) }
            item(key = "group-data") { SettingsGroup("البيانات", data) }
            item(key = "logout") {
                CadyCard(
                    onClick = { confirmLogout = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "تسجيل الخروج",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
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

/** A titled card of settings rows, each an icon tile + name + one line saying what is inside. */
@Composable
private fun SettingsGroup(title: String, entries: List<SettingsEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        CadyCard {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(icon = entry.icon, tint = Color.White, container = entry.color, size = 40.dp, cornerRadius = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
