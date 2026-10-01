package com.cady.cadysalesapp.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.sync.SyncActivityStatus
import com.cady.cadysalesapp.data.sync.SyncFileKind
import com.cady.cadysalesapp.data.sync.SyncImportOutcome
import com.cady.cadysalesapp.ui.common.BusyDialog
import com.cady.cadysalesapp.ui.common.MessageDialog
import com.cady.cadysalesapp.ui.common.SectionCard
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.common.launchShare
import com.cady.cadysalesapp.ui.common.summaryText
import com.cady.cadysalesapp.ui.theme.SyncColors

private enum class Level { OK, WAITING, PROBLEM, NEUTRAL }

private data class StatusModel(val level: Level, val title: String, val subtitle: String?)

@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onOpenPendingPreview: () -> Unit,
    onOpenLog: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }

    val status = buildStatus(state)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المزامنة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(status, state) }

            val user = state.user
            if (user != null && user.role == UserRole.REP && user.deviceName.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "لم يُحدَّد اسم لهذا الجهاز، لذا سيتعرّف المدير على ملفاتك باسم «${user.displayName}».",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "المزامنة عبر الإنترنت", icon = Icons.Filled.Sync) {
                    Text(
                        "ترفع السجلات المعلّقة إلى الخادم ثم تسحب آخر التحديثات.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.isOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = SyncColors.ErrorState,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "لا يوجد اتصال بالإنترنت حاليًا",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SyncColors.ErrorState,
                            )
                        }
                    }
                    val progress = state.progress
                    if (state.syncing) {
                        if (progress != null && progress.total > 0) {
                            LinearProgressIndicator(
                                progress = { progress.done.toFloat() / progress.total.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "جارٍ رفع ${progress.done} من ${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("جارٍ المزامنة…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !state.syncing && state.isOnline && state.user != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("مزامنة الآن")
                    }
                }
            }

            item {
                SectionCard(title = "المزامنة اليدوية (بلا إنترنت)", icon = Icons.Filled.Description) {
                    Text(
                        "صدِّر السجلات المعلّقة كملف وأرسله للمدير (واتساب أو بلوتوث…)، واستورد منه ملفات التحديث وتأكيد الاستلام.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenPendingPreview,
                        enabled = state.pending.total > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("معاينة المعلّق")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.exportPending() },
                            enabled = state.pending.total > 0 && state.user != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("تصدير ملف")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = state.user != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("استيراد ملف")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("آخر النشاط", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenLog) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("السجل الكامل")
                    }
                }
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        "لا يوجد نشاط بعد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { entry -> ActivityEntryCard(entry) }
            }
        }
    }

    // ---- dialogs ----

    state.busy?.let { BusyDialog(it) }

    if (state.incomingFile != null && state.busy == null && state.dialog == null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissIncoming() },
            title = { Text("وصلك ملف مزامنة") },
            text = { Text("هل تريد استيراد الملف الذي فتحته مع كادي؟ لن يُغيَّر شيء قبل موافقتك.") },
            confirmButton = { TextButton(onClick = { viewModel.acceptIncoming() }) { Text("استيراد") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissIncoming() }) { Text("تجاهل") } },
        )
    }

    when (val dialog = state.dialog) {
        null -> Unit
        is SyncDialog.Message -> MessageDialog(dialog.title, dialog.text) { viewModel.dismissDialog() }
        is SyncDialog.ExportReady -> AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
            title = { Text("الملف جاهز") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dialog.counts.summaryText())
                    Text(
                        "أرسل الملف للمدير. تبقى السجلات معلّقة حتى يصلك منه ملف تأكيد الاستلام.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        dialog.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.launchShare(dialog.share)
                    viewModel.dismissDialog()
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة")
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("لاحقًا") } },
        )
        is SyncDialog.ImportDone -> {
            val (title, body) = describeImport(dialog.outcome)
            MessageDialog(title, body) { viewModel.dismissDialog() }
        }
    }
}

private fun buildStatus(state: SyncUiState): StatusModel {
    val lastRun = state.lastRun
    val lastSuccess = state.lastSuccessAt
    return when {
        state.syncing -> StatusModel(Level.NEUTRAL, "جارٍ المزامنة…", null)
        state.pending.total > 0 -> StatusModel(
            Level.WAITING,
            "بانتظار الرفع: ${state.pending.total} سجل",
            if (lastRun != null && lastRun.status != SyncActivityStatus.SUCCESS && !lastRun.detail.isNullOrBlank()) {
                "آخر محاولة: ${lastRun.detail}"
            } else {
                "اضغط «مزامنة الآن» عند توفّر الإنترنت، أو صدِّرها كملف."
            },
        )
        lastRun != null && lastRun.status != SyncActivityStatus.SUCCESS -> StatusModel(
            Level.PROBLEM,
            "آخر مزامنة لم تكتمل",
            lastRun.detail,
        )
        lastSuccess != null -> StatusModel(
            Level.OK,
            "لا توجد سجلات معلّقة",
            "آخر مزامنة ناجحة: ${formatDateTime(lastSuccess)}",
        )
        else -> StatusModel(Level.NEUTRAL, "لا توجد سجلات معلّقة", "لم تُجرَ مزامنة بعد من هذا الجهاز.")
    }
}

@Composable
private fun StatusCard(status: StatusModel, state: SyncUiState) {
    val (color: Color, icon: ImageVector) = when (status.level) {
        Level.OK -> SyncColors.Synced to Icons.Filled.CloudDone
        Level.WAITING -> SyncColors.Pending to Icons.Filled.CloudUpload
        Level.PROBLEM -> SyncColors.ErrorState to Icons.Filled.Warning
        Level.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Filled.Sync
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status.title, style = MaterialTheme.typography.titleMedium, color = color)
                if (status.subtitle != null) {
                    Text(
                        status.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.pending.total > 0) {
                    Text(state.pending.summaryText(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun describeImport(outcome: SyncImportOutcome): Pair<String, String> = when (outcome) {
    is SyncImportOutcome.Rejected -> "تعذّر الاستيراد" to outcome.message
    is SyncImportOutcome.Applied -> {
        val title = when (outcome.kind) {
            SyncFileKind.MANAGER_UPDATE -> "تم استيراد تحديث المدير"
            SyncFileKind.RECEIPT_ACK -> "تم استيراد تأكيد الاستلام"
            SyncFileKind.REP_EXPORT -> "تم الاستيراد"
        }
        val lines = ArrayList<String>()
        lines.add(
            if (outcome.applied.total > 0) outcome.applied.summaryText() else "لم يتغيّر شيء على جهازك",
        )
        if (outcome.settingsApplied) lines.add("حُدِّثت بيانات الشركة.")
        if (outcome.acknowledgedExport) lines.add("أُغلق التصدير المرتبط بهذا التأكيد.")
        if (outcome.skipped > 0) lines.add("تُجوهل ${outcome.skipped} سجل (أقدم من نسختك أو لمندوب آخر).")
        if (outcome.invalid > 0) lines.add("تعذّرت قراءة ${outcome.invalid} سجل في الملف.")
        title to lines.joinToString("\n")
    }
}
