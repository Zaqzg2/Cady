package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsPrivacyScreen(viewModel: SettingsPrivacyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    var showSetPinDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("الخصوصية") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("قفل التطبيق برمز", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.isPasswordSet) "مفعّل" else "غير مفعّل",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isPasswordSet,
                    onCheckedChange = { enabled -> if (enabled) showSetPinDialog = true else viewModel.clearAppLockPassword() },
                )
            }

            if (state.isPasswordSet) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showSetPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تغيير الرمز")
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("فتح بالبصمة أو الوجه", style = MaterialTheme.typography.titleMedium)
                        val available = activity?.let(viewModel::isBiometricAvailable) ?: false
                        if (!available) {
                            Text(
                                "غير متاح على هذا الجهاز",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.isBiometricEnabled,
                        enabled = activity?.let(viewModel::isBiometricAvailable) == true,
                        onCheckedChange = viewModel::setBiometricEnabled,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("إخفاء المبالغ بآخر العمليات", style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.hideAmountsInRecents, onCheckedChange = viewModel::setHideAmountsInRecents)
            }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin -> viewModel.setAppLockPassword(pin); showSetPinDialog = false },
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رمز قفل جديد") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("الرمز") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (pin.isNotBlank()) onConfirm(pin) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
