package com.cady.cadysalesapp.ui.weborders

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadysalesapp.data.weborders.WebOrder
import com.cady.cadysalesapp.data.weborders.WebOrderStatus
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.Pill
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.dial
import com.cady.cadysalesapp.ui.common.friendlyDateTime
import com.cady.cadysalesapp.ui.common.openWhatsApp
import com.cady.cadysalesapp.ui.theme.cady

/**
 * Orders customers placed on the public sales page. The manager sees them live (newest first), calls or
 * messages the customer from the card, and moves each order along: جديد ← قيد التجهيز ← مكتمل (or ملغي).
 * The bell in the top bar switches the new-order alerts on or off.
 */
@Composable
fun WebOrdersScreen(
    onBack: () -> Unit,
    viewModel: WebOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var pendingCancel by remember { mutableStateOf<WebOrder?>(null) }

    // Android 13+ asks before an app may post notifications at all. The screen asks once when it opens (only while
    // the alerts are switched on); a refusal is explained by the banner below instead of being nagged about.
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    // Back from the system's notification settings: look again.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.settled, state.alertsEnabled) {
        if (state.settled && state.alertsEnabled && !notificationsAllowed &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(text)
        viewModel.dismissMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            CadyAppBar(
                title = { Text("طلبات الموقع") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { viewModel.setAlertsEnabled(!state.alertsEnabled) }) {
                        Icon(
                            imageVector = if (state.alertsEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = if (state.alertsEnabled) "إيقاف تنبيهات الطلبات الجديدة" else "تشغيل تنبيهات الطلبات الجديدة",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.alertsEnabled && !notificationsAllowed) {
                item(key = "permission") { PermissionBanner(onOpenSettings = { openNotificationSettings(context) }) }
            }
            item(key = "filters") { FilterRow(state, viewModel::select) }

            val error = state.error
            if (error != null) {
                item(key = "error") { ErrorCard(error) }
            } else if (state.loading && state.orders.isEmpty()) {
                item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Inbox,
                        title = "لا توجد طلبات «${state.filter.label}»",
                        message = if (state.filter == WebOrderStatus.NEW) {
                            "الطلبات التي يرسلها التجّار من صفحة البيع تظهر هنا فور وصولها."
                        } else {
                            null
                        },
                    )
                }
            }

            items(state.visible, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    busy = state.busyId == order.id,
                    onCall = { phone -> context.dial(phone) },
                    onWhatsApp = { phone -> context.openWhatsApp(phone) },
                    onSetStatus = { target ->
                        // Cancelling is the one move that deserves a second thought.
                        if (target == WebOrderStatus.CANCELLED) pendingCancel = order else viewModel.setStatus(order, target)
                    },
                )
            }
        }
    }

    pendingCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("إلغاء الطلب؟") },
            text = { Text("سيُنقل طلب «${order.customerName}» إلى «ملغي»، ويمكنك إعادة فتحه لاحقًا.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(order, WebOrderStatus.CANCELLED)
                    pendingCancel = null
                }) { Text("إلغاء الطلب") }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("تراجع") } },
        )
    }
}

@Composable
private fun FilterRow(state: WebOrdersUiState, onSelect: (WebOrderStatus) -> Unit) {
    val counts = state.counts
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WebOrderStatus.entries.forEach { status ->
            val count = counts[status] ?: 0
            FilterChip(
                selected = state.filter == status,
                onClick = { onSelect(status) },
                label = { Text(if (count > 0) "${status.label} ($count)" else status.label) },
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: WebOrder,
    busy: Boolean,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onSetStatus: (WebOrderStatus) -> Unit,
) {
    var expanded by rememberSaveable(order.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val phone = order.phone
    val subtitle = listOfNotNull(order.createdAt?.let { friendlyDateTime(it) }, phone).joinToString(" • ")
    val items = order.itemsText()

    CadyCard(onClick = { expanded = !expanded }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Pill(order.status.label, statusColor(order.status))
            }

            if (items.isNotEmpty()) {
                Text(
                    items,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Tapping the card opens everything the customer sent.
            if (expanded) {
                order.address?.let { DetailLine("العنوان", it) }
                order.note?.let { DetailLine("ملاحظات", it) }
                order.extras.forEach { (label, value) -> DetailLine(label, value) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (phone != null) {
                    IconButton(onClick = { onCall(phone) }) {
                        Icon(Icons.Filled.Call, "اتصال", tint = SwipeColors.Blue)
                    }
                    IconButton(onClick = { onWhatsApp(phone) }) {
                        Icon(Icons.Filled.Chat, "واتساب", tint = SwipeColors.Teal)
                    }
                }
                Spacer(Modifier.weight(1f))
                nextStep(order.status)?.let { (target, label) ->
                    FilledTonalButton(onClick = { onSetStatus(target) }, enabled = !busy) { Text(label) }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        WebOrderStatus.entries.filter { it != order.status }.forEach { target ->
                            DropdownMenuItem(
                                text = { Text("نقل إلى «${target.label}»") },
                                onClick = {
                                    menuOpen = false
                                    onSetStatus(target)
                                },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    CadyCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("إشعارات التطبيق متوقفة على هذا الجهاز", style = MaterialTheme.typography.titleSmall)
            Text(
                "لن يصلك تنبيه عند وصول طلب جديد حتى تسمح للتطبيق بإرسال الإشعارات.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpenSettings) { Text("فتح إعدادات الإشعارات") }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    CadyCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusColor(status: WebOrderStatus): Color = when (status) {
    WebOrderStatus.NEW -> MaterialTheme.colorScheme.primary
    WebOrderStatus.PROCESSING -> MaterialTheme.cady.warning
    WebOrderStatus.DONE -> MaterialTheme.cady.success
    WebOrderStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The one button that moves an order forward (the ⋮ menu has every other move). */
private fun nextStep(status: WebOrderStatus): Pair<WebOrderStatus, String>? = when (status) {
    WebOrderStatus.NEW -> WebOrderStatus.PROCESSING to "بدء التجهيز"
    WebOrderStatus.PROCESSING -> WebOrderStatus.DONE to "تم التسليم"
    WebOrderStatus.CANCELLED -> WebOrderStatus.NEW to "إعادة فتح"
    WebOrderStatus.DONE -> null
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // A phone without a settings screen for it: nothing more to offer.
    }
}
