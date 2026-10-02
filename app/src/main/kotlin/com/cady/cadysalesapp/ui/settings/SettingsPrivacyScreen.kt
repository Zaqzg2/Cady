package com.cady.cadysalesapp.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cady.cadysalesapp.data.repository.BiometricAvailability
import com.cady.cadysalesapp.ui.common.findFragmentActivity

@Composable
fun SettingsPrivacyScreen(viewModel: SettingsPrivacyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    var showSetPinDialog by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    // Re-check the sensor whenever the person comes back to this screen — typically
    // from the system settings, right after enrolling a fingerprint there.
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val availability = remember(activity, refreshTick) {
        activity?.let(viewModel::biometricAvailability) ?: BiometricAvailability.UNAVAILABLE
    }

    Scaffold(topBar = { TopAppBar(title = { Text("الخصوصية") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
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
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("فتح بالبصمة أو الوجه", style = MaterialTheme.typography.titleMedium)
                        availability.message?.let { reason ->
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.isBiometricEnabled,
                        // An already-enabled switch must stay switchable to "off" even if the
                        // sensor went away since.
                        enabled = availability == BiometricAvailability.AVAILABLE || state.isBiometricEnabled,
                        onCheckedChange = { wantOn ->
                            if (wantOn) {
                                biometricError = null
                                activity?.let { viewModel.enableBiometric(it) { message -> biometricError = message } }
                            } else {
                                viewModel.setBiometricEnabled(false)
                            }
                        },
                    )
                }
                if (availability == BiometricAvailability.NONE_ENROLLED) {
                    TextButton(onClick = { openBiometricEnrollment(context) }) {
                        Text("تسجيل بصمة في إعدادات الهاتف")
                    }
                }
                biometricError?.let { message ->
                    Text(
                        "تعذّر تفعيل البصمة: $message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
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

/** Sends the person to the system screen for adding a fingerprint/face. */
private fun openBiometricEnrollment(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            // Nothing on this device can open it; the message above already says what to do.
        }
    }
}

/**
 * The lock screen's pad only has the digits 0–9 (4 to 8 of them), so a PIN made of
 * anything else could never be typed back in. Eastern-Arabic digits from an Arabic
 * keyboard are converted to the plain ones the pad sends.
 */
private fun String.toPinDigits(): String = buildString {
    for (c in this@toPinDigits) {
        when (c) {
            in '0'..'9' -> append(c)
            in '٠'..'٩' -> append('0' + (c - '٠'))
            in '۰'..'۹' -> append('0' + (c - '۰'))
        }
    }
}.take(8)

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رمز قفل جديد") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { input -> pin = input.toPinDigits() },
                label = { Text("الرمز (من 4 إلى 8 أرقام)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4, onClick = { onConfirm(pin) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
