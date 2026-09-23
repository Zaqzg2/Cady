package com.cady.cadysalesapp.ui.settings

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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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

@Composable
fun SettingsPrintingScreen(
    viewModel: SettingsPrintingViewModel = hiltViewModel(),
) {
    val devices by viewModel.pairedDevices.collectAsState()
    val savedMac by viewModel.savedPrinterMac.collectAsState()
    val connectionCheck by viewModel.connectionCheck.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var fontScale by remember(settings.printFontScale) { mutableFloatStateOf(settings.printFontScale) }
    var lineSpacing by remember(settings.printLineSpacingExtra) { mutableFloatStateOf(settings.printLineSpacingExtra) }
    var blackThreshold by remember(settings.printBlackThreshold) { mutableFloatStateOf(settings.printBlackThreshold.toFloat()) }

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

    Scaffold(topBar = { TopAppBar(title = { Text("إعدادات الطباعة") }) }) { padding ->
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
            Card(modifier = Modifier.fillMaxWidth()) {
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
                valueRange = 0f..255f,
            )

            Spacer(Modifier.height(20.dp))
            // Real verified-write status, not a saved-preference badge — matches
            // the reliability fix the current app made after "shows connected,
            // printing fails" turned out to mean the socket died silently.
            if (savedMac != null) {
                Card(
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
                            Text("الطابعة المحفوظة", style = MaterialTheme.typography.bodyMedium)
                            Text(savedMac ?: "", style = MaterialTheme.typography.titleMedium)
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
                Spacer(Modifier.height(20.dp))
            }

            Text("الطابعات المقترنة بالجهاز", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    "لا يوجد طابعات مقترنة — قرن الطابعة أولًا من إعدادات بلوتوث بالجهاز",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            devices.forEach { device ->
                Card(
                    onClick = { viewModel.selectPrinter(device.mac) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
