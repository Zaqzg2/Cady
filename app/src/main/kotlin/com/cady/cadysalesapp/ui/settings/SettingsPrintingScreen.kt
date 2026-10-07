package com.cady.cadysalesapp.ui.settings

import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.BackButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import com.cady.cadysalesapp.data.printing.SavedPrinter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.repository.PdfLayoutMode
import com.cady.cadysalesapp.data.repository.PrintSpeed

@Composable
fun SettingsPrintingScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsPrintingViewModel = hiltViewModel(),
) {
    val devices by viewModel.pairedDevices.collectAsState()
    val savedMac by viewModel.savedPrinterMac.collectAsState()
    val savedPrinters by viewModel.savedPrinters.collectAsState()
    var printerToRemove by remember { mutableStateOf<SavedPrinter?>(null) }
    val connectionCheck by viewModel.connectionCheck.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val testPrint by viewModel.testPrint.collectAsState()

    var fontScale by remember(settings.printFontScale) { mutableFloatStateOf(settings.printFontScale) }
    var lineSpacing by remember(settings.printLineSpacingExtra) { mutableFloatStateOf(settings.printLineSpacingExtra) }
    // The slider stops at 230: above that, blank paper itself counts as "dark"
    // and the whole receipt prints as one solid black block (see ThermalPrintService).
    var blackThreshold by remember(settings.printBlackThreshold) {
        mutableFloatStateOf(settings.printBlackThreshold.toFloat().coerceIn(60f, 230f))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) viewModel.loadPairedDevices() }

    LaunchedEffect(Unit) {
        if (viewModel.hasBluetoothPermission()) {
            viewModel.loadPairedDevices()
        } else if (viewModel.requiredBluetoothPermissions.isNotEmpty()) {
            permissionLauncher.launch(viewModel.requiredBluetoothPermissions)
        } else {
            viewModel.loadPairedDevices()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CadyAppBar(title = { Text("إعدادات الطباعة") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("شكل الطباعة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.printLayoutMode == PdfLayoutMode.THERMAL_80MM,
                    onClick = { viewModel.setLayoutMode(PdfLayoutMode.THERMAL_80MM) },
                    label = { Text("إيصال حراري 80مم") },
                )
                FilterChip(
                    selected = settings.printLayoutMode == PdfLayoutMode.A4,
                    onClick = { viewModel.setLayoutMode(PdfLayoutMode.A4) },
                    label = { Text("A4") },
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("معاينة حية", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CadyCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "غسيل صحون بالرمان 5لتر  —  2 × 500 = 1,000 ر.ي",
                        fontSize = (11 * fontScale).sp,
                        lineHeight = ((11 * fontScale) + lineSpacing).sp,
                    )
                    Spacer(Modifier.height(lineSpacing.dp))
                    Text(
                        "الإجمالي: 1,000 ر.ي",
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * fontScale).sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "عتبة اللون — ما تحت الخط يصبح أسود عند الطباعة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val blackFraction = (blackThreshold / 255f).coerceIn(0.01f, 0.99f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    ) {
                        Row(modifier = Modifier.weight(blackFraction).fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {}
                        Row(modifier = Modifier.weight(1f - blackFraction).fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {}
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("حجم خط الطباعة", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = fontScale,
                onValueChange = { fontScale = it },
                onValueChangeFinished = { viewModel.setPrintFontScale(fontScale) },
                valueRange = 0.8f..1.6f,
            )

            Text("تباعد السطور", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = lineSpacing,
                onValueChange = { lineSpacing = it },
                onValueChangeFinished = { viewModel.setPrintLineSpacingExtra(lineSpacing) },
                valueRange = 0f..6f,
            )

            Text("عتبة تحويل الرمادي إلى أسود", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = blackThreshold,
                onValueChange = { blackThreshold = it },
                onValueChangeFinished = { viewModel.setBlackThreshold(blackThreshold.toInt()) },
                valueRange = 60f..230f,
            )

            Spacer(Modifier.height(12.dp))
            Text("سرعة الإرسال للطابعة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintSpeed.entries.forEach { speed ->
                    FilterChip(
                        selected = settings.printSpeed == speed,
                        onClick = { viewModel.setPrintSpeed(speed) },
                        label = { Text(speed.arabicLabel()) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "إن كانت الطباعة تتقطّع أو تظهر فيها خطوط بيضاء جرّب «سريع»، وإن خرجت الصورة مشوّشة أو سوداء جرّب «آمن». اطبع «صفحة اختبار» بعد كل تغيير لتقارن.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            // Real verified-write status, not a saved-preference badge — matches
            // the reliability fix the current app made after "shows connected,
            // printing fails" turned out to mean the socket died silently.
            if (savedMac != null) {
                CadyCard(
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionCheck) {
                            is ConnectionCheckState.Verified -> MaterialTheme.colorScheme.primaryContainer
                            is ConnectionCheckState.Failed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("الطابعة الافتراضية", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                savedPrinters.firstOrNull { it.mac.equals(savedMac, ignoreCase = true) }?.name ?: (savedMac ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        when (connectionCheck) {
                            is ConnectionCheckState.Checking -> CircularProgressIndicator(modifier = Modifier.height(24.dp))
                            is ConnectionCheckState.Verified -> Icon(Icons.Filled.CheckCircle, contentDescription = "متصلة", tint = MaterialTheme.colorScheme.primary)
                            is ConnectionCheckState.Failed -> Icon(Icons.Filled.Error, contentDescription = "غير متصلة", tint = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    }
                }
                (connectionCheck as? ConnectionCheckState.Failed)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { savedMac?.let(viewModel::checkConnection) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("تحقّق من الاتصال الآن") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::printTestPage,
                    enabled = testPrint !is TestPrintState.Printing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("طباعة صفحة اختبار") }
                when (val result = testPrint) {
                    is TestPrintState.Printing -> Text(
                        "جارٍ الإرسال إلى الطابعة…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is TestPrintState.Done -> Text(
                        "أُرسلت صفحة الاختبار ✓ — افحص الخطوط البيضاء والتقطيع وسماكة الخطوط",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is TestPrintState.Failed -> Text(
                        result.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {}
                }
                Spacer(Modifier.height(20.dp))
            }

            if (savedPrinters.isNotEmpty()) {
                Text("طابعاتي المحفوظة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "عند الطباعة من المعاينة تختار أي طابعة منها إن كان لديك أكثر من واحدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                savedPrinters.forEach { printer ->
                    val isDefault = printer.mac.equals(savedMac, ignoreCase = true)
                    CadyCard(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(printer.name, style = MaterialTheme.typography.titleMedium)
                                Text(printer.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isDefault) {
                                AssistChip(onClick = {}, label = { Text("الافتراضية") })
                            } else {
                                TextButton(onClick = { viewModel.makeDefault(printer.mac) }) { Text("اجعلها افتراضية") }
                            }
                            IconButton(onClick = { printerToRemove = printer }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف الطابعة")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Text("إضافة طابعة من المقترنة بالجهاز", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    "لا يوجد طابعات مقترنة — قرن الطابعة أولًا من إعدادات بلوتوث بالجهاز",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            devices.forEach { device ->
                val alreadySaved = savedPrinters.any { it.mac.equals(device.mac, ignoreCase = true) }
                CadyCard(
                    onClick = { viewModel.selectPrinter(device.mac, device.name) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text(device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (alreadySaved) "محفوظة — اضغط لجعلها الافتراضية" else "اضغط للإضافة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    printerToRemove?.let { printer ->
        RemovePrinterDialog(
            printer = printer,
            onConfirm = { viewModel.removePrinter(printer.mac); printerToRemove = null },
            onDismiss = { printerToRemove = null },
        )
    }
}

@Composable
private fun RemovePrinterDialog(printer: SavedPrinter, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف الطابعة؟") },
        text = { Text("ستُزال «${printer.name}» من قائمة طابعاتك المحفوظة. يمكنك إضافتها من جديد في أي وقت.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun PrintSpeed.arabicLabel(): String = when (this) {
    PrintSpeed.SAFE -> "آمن"
    PrintSpeed.BALANCED -> "متوازن"
    PrintSpeed.FAST -> "سريع"
}
