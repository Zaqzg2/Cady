package com.cady.cadysalesapp.ui.manager

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.HeroCard
import com.cady.cadysalesapp.ui.common.IconTile
import com.cady.cadysalesapp.ui.common.SectionTitle
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.formatDateTime

private class ManagerEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
    /** A number on the tile (orders waiting, …); 0 shows nothing. */
    val badge: Int = 0,
)

/**
 * The manager's front page, inside the same app and the same bottom bar as the
 * rep's screens — it only appears (as a fifth tab) for a manager account.
 * It is the hub the manager's tools hang off; each tile opens its own screen.
 */
@Composable
fun ManagerDashboardScreen(
    displayName: String,
    onUsersClick: () -> Unit,
    onSyncHubClick: () -> Unit,
    onLiveActivityClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onSyncLogClick: () -> Unit,
    onOffersClick: () -> Unit,
    onWebOrdersClick: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: ManagerDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Android 13+: the new-website-order alert is a notification, which needs the user's permission. Asked here, on the
    // manager's front page, so the alerts work from the first sign-in — not only after the orders screen is opened.
    // (A refusal is not nagged about: the system stops showing its dialog after two, and the orders screen explains.)
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.alertsEnabled) {
        if (state.alertsEnabled == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val entries = listOf(
        ManagerEntry(
            "طلبات الموقع", "طلبات التجّار من صفحة البيع — راجعها وغيّر حالتها",
            Icons.Filled.Public, SwipeColors.Red, onWebOrdersClick, badge = state.newWebOrders,
        ),
        ManagerEntry("المندوبون", "إضافة المندوبين وإدارة حساباتهم ونشاط كل منهم", Icons.Filled.Group, SwipeColors.Blue, onUsersClick),
        ManagerEntry("مركز المزامنة", "حالة كل مندوب وإحصاءات الاستيراد والتصدير", Icons.Filled.Sync, SwipeColors.Teal, onSyncHubClick),
        ManagerEntry("النشاط المباشر", "فواتير وسندات المندوبين فور وصولها", Icons.Filled.Visibility, SwipeColors.Green, onLiveActivityClick),
        ManagerEntry("العروض", "خصم نسبة على منتج خلال فترة محددة", Icons.Filled.LocalOffer, SwipeColors.Amber, onOffersClick),
        ManagerEntry("استيراد من مندوب", "استلام ملف مزامنة من مندوب ومراجعته", Icons.Filled.Download, SwipeColors.Slate, onImportClick),
        ManagerEntry("إنشاء تحديث", "إرسال المنتجات والعملاء والإعدادات للمندوبين", Icons.Filled.Upload, SwipeColors.Red, onExportClick),
        ManagerEntry("سجل المزامنة", "كل عمليات الاستيراد والتصدير في مكان واحد", Icons.Filled.History, SwipeColors.Slate, onSyncLogClick),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CadyAppBar(title = { Text("لوحة المدير") }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                HeroCard {
                    Text("مرحبًا $displayName", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        HeroStat("المندوبون", state.repCount.toString())
                        HeroStat("النشطون", state.activeReps.toString())
                    }
                    Spacer(Modifier.height(12.dp))
                    val last = state.lastActivity
                    Text(
                        if (last == null) "لا يوجد نشاط مزامنة مسجّل بعد" else "آخر نشاط: ${last.title} — ${formatDateTime(last.at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.85f),
                    )
                }
            }
            item(key = "title") { SectionTitle("أدوات المدير") }
            // Two tiles per row (equal height inside a row); a lone last tile takes the full width.
            val rows = entries.chunked(2)
            rows.forEachIndexed { index, pair ->
                item(key = "row-$index") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { entry ->
                            ToolTile(entry, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ToolTile(entry: ManagerEntry, modifier: Modifier = Modifier) {
    CadyCard(onClick = entry.onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                IconTile(icon = entry.icon, tint = Color.White, container = entry.color, size = 44.dp)
                Spacer(Modifier.weight(1f))
                if (entry.badge > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) { Text(entry.badge.toString()) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
