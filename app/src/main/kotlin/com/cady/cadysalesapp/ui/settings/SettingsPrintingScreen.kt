package com.cady.cadysalesapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsPrintingScreen(
    viewModel: SettingsPrintingViewModel = hiltViewModel(),
) {
    val devices by viewModel.pairedDevices.collectAsState()
    val savedMac by viewModel.savedPrinterMac.collectAsState()
    val connectionCheck by viewModel.connectionCheck.collectAsState()

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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices, key = { it.mac }) { device ->
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
}
