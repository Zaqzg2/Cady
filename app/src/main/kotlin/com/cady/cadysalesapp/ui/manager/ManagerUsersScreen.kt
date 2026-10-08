package com.cady.cadysalesapp.ui.manager

import com.cady.cadysalesapp.ui.common.CadyCard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity
import com.cady.cadysalesapp.ui.common.AvatarInitial
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.EmptyState
import com.cady.cadysalesapp.ui.common.Pill
import com.cady.cadysalesapp.ui.common.QuickAction
import com.cady.cadysalesapp.ui.common.QuickActionsSheet
import com.cady.cadysalesapp.ui.common.SwipeAction
import com.cady.cadysalesapp.ui.common.SwipeActionRow
import com.cady.cadysalesapp.ui.common.SwipeColors
import com.cady.cadysalesapp.ui.common.SwipeCoordinator
import com.cady.cadysalesapp.ui.common.pressScale
import com.cady.cadysalesapp.ui.common.rememberSwipeCoordinator

@Composable
fun ManagerUsersScreen(
    onBack: () -> Unit,
    onRepActivity: (repId: String) -> Unit,
    viewModel: ManagerUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserAccountEntity?>(null) }
    val coordinator = rememberSwipeCoordinator()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CadyAppBar(
                title = { Text("المندوبون") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (state.refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "تحديث") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "إضافة مندوب") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.message?.let { message ->
                item {
                    CadyCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
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
            }
            item {
                Text(
                    "أرقام «اليوم» تُقرأ من السحابة مباشرة، فلا تشمل ما لم يرفعه المندوب بعد من جهازه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.reps.isEmpty() && !state.refreshing) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Visibility,
                        title = "لا يوجد مندوبون بعد",
                        message = "اضغط + لإضافة أول مندوب وتسليمه اسم المستخدم وكلمة المرور.",
                        actionLabel = "إضافة مندوب",
                        onAction = { showAdd = true },
                    )
                }
            }
            items(state.reps, key = { it.account.id }) { row ->
                val rep = row.account
                RepListItem(
                    row = row,
                    coordinator = coordinator,
                    onActivity = { onRepActivity(rep.id) },
                    onEdit = { editing = rep },
                    onSetActive = { viewModel.setActive(rep, it) },
                    modifier = Modifier.animateItem(),
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAdd) {
        AddRepDialog(
            onDismiss = { showAdd = false },
            onSave = { username, password, name, number, device, report ->
                viewModel.addRep(username, password, name, number, device) { error ->
                    if (error == null) showAdd = false
                    report(error)
                }
            },
        )
    }
    editing?.let { rep ->
        EditRepDialog(
            rep = rep,
            onDismiss = { editing = null },
            onSave = { name, number, device, report ->
                viewModel.saveProfile(rep, name, number, device) { error ->
                    if (error == null) editing = null
                    report(error)
                }
            },
        )
    }
}

/**
 * A rep with gestures: tap → his live activity; swipe LEFT → live activity; swipe RIGHT → edit / suspend or
 * re-enable; long-press → the quick menu with the same three things; the switch is suspend / re-enable too.
 */
@Composable
private fun RepListItem(
    row: RepRow,
    coordinator: SwipeCoordinator,
    onActivity: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rep = row.account
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var sheetOpen by remember { mutableStateOf(false) }

    SwipeActionRow(
        modifier = modifier,
        rowKey = rep.id,
        coordinator = coordinator,
        rightActions = listOf(SwipeAction("النشاط", Icons.Filled.Visibility, SwipeColors.Blue, onClick = onActivity)),
        leftActions = listOf(
            SwipeAction("تعديل", Icons.Filled.Edit, SwipeColors.Slate, onClick = onEdit),
            if (rep.isActive) {
                SwipeAction("إيقاف", Icons.Filled.Block, SwipeColors.Red) { onSetActive(false) }
            } else {
                SwipeAction("تفعيل", Icons.Filled.CheckCircle, SwipeColors.Green) { onSetActive(true) }
            },
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().pressScale(interaction),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onActivity,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sheetOpen = true
                        },
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarInitial(rep.displayName, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rep.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(rep.username)
                                rep.repNumber?.let { append(" · مندوب رقم $it") }
                                rep.deviceName?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = rep.isActive, onCheckedChange = onSetActive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (rep.isActive) {
                        Pill("الحساب نشط", com.cady.cadysalesapp.ui.theme.SyncColors.Synced)
                    } else {
                        Pill("موقوف — لا يستطيع الدخول", scheme.error, icon = Icons.Filled.Block)
                    }
                }
                Text(
                    syncRecencyText(rep.lastSyncAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = syncRecencyColor(rep.lastSyncAt),
                )
                Text(
                    row.today?.let { "اليوم (من السحابة): ${it.invoices} فاتورة · ${it.receipts} سند" }
                        ?: "نشاط اليوم: غير متاح (تعذّر الوصول للسحابة)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // The two actions stay visible as buttons (as before) — the gestures are the shortcut, not the only way.
                Row {
                    TextButton(onClick = onEdit) { Text("تعديل") }
                    TextButton(onClick = onActivity) { Text("النشاط المباشر") }
                }
            }
        }
    }

    if (sheetOpen) {
        QuickActionsSheet(
            title = rep.displayName,
            subtitle = rep.username,
            leading = { AvatarInitial(rep.displayName, size = 44.dp) },
            actions = listOf(
                QuickAction("النشاط المباشر", Icons.Filled.Visibility, onClick = onActivity),
                QuickAction("تعديل البيانات", Icons.Filled.Edit, onClick = onEdit),
                if (rep.isActive) {
                    QuickAction("إيقاف الحساب", Icons.Filled.Block, destructive = true) { onSetActive(false) }
                } else {
                    QuickAction("تفعيل الحساب", Icons.Filled.CheckCircle) { onSetActive(true) }
                },
            ),
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun AddRepDialog(
    onDismiss: () -> Unit,
    onSave: (username: String, password: String, displayName: String, repNumber: Int, deviceName: String?, report: (String?) -> Unit) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var repNumber by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("مندوب جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم (أحرف إنجليزية وأرقام)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور (6 أحرف على الأقل)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repNumber,
                    onValueChange = { value -> repNumber = value.filter { it.isDigit() }.take(4) },
                    label = { Text("رقم المندوب") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("اسم الجهاز (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val number = repNumber.toIntOrNull()
                    error = when {
                        displayName.isBlank() -> "اكتب اسم المندوب"
                        username.isBlank() -> "اكتب اسم المستخدم"
                        password.length < 6 -> "كلمة المرور 6 أحرف على الأقل"
                        number == null || number < 1 -> "اكتب رقم المندوب"
                        else -> null
                    }
                    if (error == null && number != null) {
                        saving = true
                        onSave(username, password, displayName, number, deviceName.ifBlank { null }) { reason ->
                            saving = false
                            error = reason
                        }
                    }
                },
            ) { Text(if (saving) "جارٍ الإنشاء…" else "إنشاء") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun EditRepDialog(
    rep: UserAccountEntity,
    onDismiss: () -> Unit,
    onSave: (displayName: String, repNumber: Int?, deviceName: String?, report: (String?) -> Unit) -> Unit,
) {
    var displayName by remember { mutableStateOf(rep.displayName) }
    var repNumber by remember { mutableStateOf(rep.repNumber?.toString().orEmpty()) }
    var deviceName by remember { mutableStateOf(rep.deviceName.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تعديل ${rep.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = repNumber,
                    onValueChange = { value -> repNumber = value.filter { it.isDigit() }.take(4) },
                    label = { Text("رقم المندوب") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("اسم الجهاز") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "اسم المستخدم وكلمة المرور لا يتغيّران من هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (displayName.isBlank()) {
                        error = "اكتب اسم المندوب"
                    } else {
                        saving = true
                        error = null
                        onSave(displayName, repNumber.toIntOrNull(), deviceName.ifBlank { null }) { reason ->
                            saving = false
                            error = reason
                        }
                    }
                },
            ) { Text(if (saving) "جارٍ الحفظ…" else "حفظ") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("إلغاء") } },
    )
}
