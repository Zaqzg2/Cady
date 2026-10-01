package com.cady.cadysalesapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.backup.AutoBackupFrequency
import com.cady.cadysalesapp.data.backup.BackupInfo
import com.cady.cadysalesapp.data.backup.BackupKind
import com.cady.cadysalesapp.data.backup.CsvDataset
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.ui.common.BusyDialog
import com.cady.cadysalesapp.ui.common.MessageDialog
import com.cady.cadysalesapp.ui.common.SectionCard
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.common.formatFileSize
import com.cady.cadysalesapp.ui.common.launchShare
import com.cady.cadysalesapp.ui.common.summaryText
import com.cady.cadysalesapp.ui.theme.SyncColors

@Composable
fun BackupManagementScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // These survive rotation: the picker result can arrive after the screen was recreated.
    var saveTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var csvImportDataset by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.shareEvents.collect { context.launchShare(it) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val name = saveTargetName
        saveTargetName = null
        if (uri != null && name != null) viewModel.saveTo(name, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.requestRestoreFromFile(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val dataset = csvImportDataset?.let { runCatching { CsvDataset.valueOf(it) }.getOrNull() }
        csvImportDataset = null
        if (uri != null && dataset != null) viewModel.previewCsvImport(dataset, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النسخ الاحتياطي") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "نسخة احتياطية الآن", icon = Icons.Filled.Backup) {
                    Text(
                        "تحفظ كل العملاء والمنتجات والفواتير والسندات مع التوقيعات وبيانات الشركة في ملف واحد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.createBackupNow() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("إنشاء نسخة الآن")
                    }
                }
            }

            item {
                SectionCard(title = "النسخ التلقائي", icon = Icons.Filled.Schedule) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تفعيل النسخ التلقائي", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.autoEnabled,
                            onCheckedChange = { viewModel.setAutoBackup(it, state.autoFrequency) },
                        )
                    }
                    if (state.autoEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.autoFrequency == AutoBackupFrequency.DAILY,
                                onClick = { viewModel.setAutoBackup(true, AutoBackupFrequency.DAILY) },
                                label = { Text("يوميًا") },
                            )
                            FilterChip(
                                selected = state.autoFrequency == AutoBackupFrequency.WEEKLY,
                                onClick = { viewModel.setAutoBackup(true, AutoBackupFrequency.WEEKLY) },
                                label = { Text("أسبوعيًا") },
                            )
                        }
                        val lastAuto = state.backups.firstOrNull { it.kind == BackupKind.AUTO && it.isValid }
                        Text(
                            if (lastAuto != null) "آخر نسخة تلقائية: ${formatDateTime(lastAuto.createdAt)}" else "لم تُنشأ نسخة تلقائية بعد.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "يعمل في الخلفية عند توفّر بطارية كافية وقد يتأخر قليلًا. تُحفظ آخر 7 نسخ تلقائية فقط.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "ملفات CSV (إكسل)", icon = Icons.Filled.TableChart) {
                    Text(
                        "صدِّر العملاء أو المنتجات أو الفواتير أو السندات لتفتحها في إكسل، أو استورد قائمة عملاء/منتجات (مع معاينة قبل الحفظ).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.openCsvExportPicker() }, modifier = Modifier.weight(1f)) {
                            Text("تصدير CSV")
                        }
                        OutlinedButton(
                            onClick = { viewModel.openCsvImportPicker() },
                            enabled = state.user != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("استيراد CSV")
                        }
                    }
                }
            }

            item {
                Text(
                    "النسخ المحفوظة (${state.backups.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.backups.isEmpty()) {
                item {
                    Text(
                        if (state.listLoaded) "لا توجد نسخ محفوظة بعد." else "جارٍ التحميل…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.backups, key = { it.name }) { info ->
                    BackupCard(
                        info = info,
                        onShare = { viewModel.share(info) },
                        onSave = {
                            saveTargetName = info.name
                            saveLauncher.launch(info.name)
                        },
                        onRestore = { viewModel.requestRestore(info) },
                        onDelete = { viewModel.requestDelete(info) },
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("استرجاع من ملف خارجي")
                }
            }
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = SyncColors.Pending,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "النسخ المحفوظة هنا تُحذف مع حذف التطبيق أو مسح بياناته. للحماية الكاملة شارك النسخة أو احفظها خارج التطبيق (واتساب، الملفات، درايف…).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ---- dialogs ----

    state.busy?.let { BusyDialog(it) }

    when (val dialog = state.dialog) {
        null -> Unit
        is BackupDialog.Message -> MessageDialog(dialog.title, dialog.text) { viewModel.dismissDialog() }
        is BackupDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text("حذف النسخة؟") },
            text = { Text("ستُحذف هذه النسخة نهائيًا من الجهاز (${formatDateTime(dialog.info.createdAt)}). لا يمكن التراجع.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmDelete() }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("إلغاء") } },
        )
        is BackupDialog.ConfirmRestore -> AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text("استرجاع هذه النسخة؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نسخة ${kindLabel(dialog.preview.kind)} — ${formatDateTime(dialog.preview.createdAt)}")
                    Text(dialog.preview.counts.summaryText(), style = MaterialTheme.typography.bodyMedium)
                    if (dialog.ownerMismatch) {
                        Text(
                            "تنبيه: هذه النسخة تخص حسابًا آخر" +
                                (dialog.preview.ownerName?.let { " ($it)" } ?: "") +
                                ". ستُضاف بياناتها لكنها قد لا تظهر ضمن حسابك الحالي.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SyncColors.ErrorState,
                        )
                    }
                    Text(
                        "الاسترجاع يدمج ولا يمسح: السجلات الموجودة في النسخة تحلّ محل نظيراتها، وما أُنشئ بعد النسخة يبقى كما هو. تؤخذ نسخة أمان من وضعك الحالي قبل البدء.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmRestore() }) { Text("استرجاع") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("إلغاء") } },
        )
        BackupDialog.PickCsvExport -> DatasetPickerDialog(
            title = "تصدير أي بيانات؟",
            datasets = CsvDataset.entries,
            onPick = { viewModel.exportCsv(it) },
            onDismiss = { viewModel.dismissDialog() },
        )
        BackupDialog.PickCsvImport -> {
            val isManager = state.user?.role == UserRole.MANAGER
            DatasetPickerDialog(
                title = "استيراد إلى…",
                datasets = CsvDataset.entries.filter { it.importable && (it != CsvDataset.PRODUCTS || isManager) },
                footer = if (isManager) null else "استيراد المنتجات متاح للمدير فقط.",
                onPick = {
                    csvImportDataset = it.name
                    viewModel.dismissDialog()
                    csvLauncher.launch(arrayOf("*/*"))
                },
                onDismiss = { viewModel.dismissDialog() },
            )
        }
        is BackupDialog.CsvImportReady -> {
            val preview = dialog.preview
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("معاينة الاستيراد — ${preview.dataset.label}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("جديد: ${preview.newCount}")
                        if (preview.updateCount > 0) Text("سيُحدَّث: ${preview.updateCount}")
                        if (preview.duplicateCount > 0) Text("مكرر/بلا تغيير (يُتجاهل): ${preview.duplicateCount}")
                        if (preview.invalidCount > 0) Text("غير صالح (يُتجاهل): ${preview.invalidCount}")
                        if (preview.sampleNames.isNotEmpty()) {
                            Text(
                                "مثال: " + preview.sampleNames.joinToString("، "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!preview.hasChanges) Text("لا يوجد ما يُحفظ من هذا الملف.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmCsvImport() }, enabled = preview.hasChanges) { Text("حفظ") }
                },
                dismissButton = { TextButton(onClick = { viewModel.dismissDialog() }) { Text("إلغاء") } },
            )
        }
    }
}

private fun kindLabel(kind: BackupKind): String = when (kind) {
    BackupKind.MANUAL -> "يدوية"
    BackupKind.AUTO -> "تلقائية"
    BackupKind.PRE_RESTORE -> "قبل الاسترجاع"
}

@Composable
private fun BackupCard(
    info: BackupInfo,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(info.createdAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (info.isValid) kindLabel(info.kind) else "ملف غير صالح",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (info.isValid) MaterialTheme.colorScheme.primary else SyncColors.ErrorState,
                )
            }
            val counts = info.counts
            if (counts != null) {
                Text(counts.summaryText(), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatFileSize(info.sizeBytes) + (info.ownerName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "مشاركة") }
                IconButton(onClick = onSave) { Icon(Icons.Filled.SaveAlt, contentDescription = "حفظ خارج التطبيق") }
                IconButton(onClick = onRestore, enabled = info.isValid) {
                    Icon(Icons.Filled.Restore, contentDescription = "استرجاع")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DatasetPickerDialog(
    title: String,
    datasets: List<CsvDataset>,
    onPick: (CsvDataset) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                datasets.forEach { dataset ->
                    Text(
                        dataset.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(dataset) }
                            .padding(vertical = 14.dp),
                    )
                }
                if (footer != null) {
                    Text(
                        footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
