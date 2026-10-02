package com.cady.cadysalesapp.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.cady.cadysalesapp.ui.common.findFragmentActivity
import kotlinx.coroutines.delay

private const val MAX_PIN_LENGTH = 8
private const val MIN_PIN_LENGTH = 4
private const val CONFIRM_KEY = "✓"
private const val BACKSPACE_KEY = "⌫"

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var confirmForgot by remember { mutableStateOf(false) }
    val error by viewModel.errorMessage.collectAsState()
    val biometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val activity = LocalContext.current.findFragmentActivity()

    // The lock screen is reused for every lock, so an error left over from the
    // previous one must not greet the next.
    LaunchedEffect(Unit) { viewModel.clearError() }

    // Offer biometric immediately on arriving at the lock screen, not only on
    // a manual tap — this is the actual point of enabling it in settings.
    LaunchedEffect(biometricEnabled, activity) {
        if (!biometricEnabled || activity == null) return@LaunchedEffect
        // The system sheet only appears for an activity that is fully in front
        // (RESUMED); asked any earlier — right as the app returns from the
        // background — it is silently cancelled and the person sees nothing.
        while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) delay(50)
        delay(250)
        viewModel.tryBiometric(activity, onUnlocked)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("التطبيق مقفل", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Dot progress indicator, one filled dot per entered digit.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(minOf(pin.length, MAX_PIN_LENGTH).coerceAtLeast(MIN_PIN_LENGTH)) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        if (biometricEnabled) {
            Spacer(Modifier.height(16.dp))
            IconButton(onClick = { activity?.let { viewModel.tryBiometric(it, onUnlocked) } }) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = "فتح بالبصمة",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(CONFIRM_KEY, "0", BACKSPACE_KEY),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    NumPadKey(key) {
                        when {
                            key == BACKSPACE_KEY -> {
                                pin = pin.dropLast(1)
                                viewModel.clearError()
                            }
                            key == CONFIRM_KEY -> {
                                // The explicit "check it now" for PINs shorter than the maximum.
                                if (pin.isNotEmpty()) {
                                    viewModel.verify(pin, showErrorIfWrong = true, onUnlocked = onUnlocked, onWrong = { pin = "" })
                                }
                            }
                            pin.length < MAX_PIN_LENGTH -> {
                                pin += key
                                viewModel.clearError()
                                if (pin.length >= MIN_PIN_LENGTH) {
                                    // Any length from 4 up may already be the whole PIN, so try it
                                    // quietly; only a full-length entry reports a wrong PIN.
                                    viewModel.verify(
                                        pin,
                                        showErrorIfWrong = pin.length == MAX_PIN_LENGTH,
                                        onUnlocked = onUnlocked,
                                        onWrong = { pin = "" },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { confirmForgot = true }) {
            Text("نسيت الرمز؟")
        }
    }

    if (confirmForgot) {
        AlertDialog(
            onDismissRequest = { confirmForgot = false },
            title = { Text("نسيت الرمز؟") },
            text = {
                Text("ستخرج من الحساب ويُلغى رمز القفل الحالي. ادخل بكلمة مرور حسابك ثم اضبط رمزًا جديدًا من الإعدادات ← الخصوصية. لن تُحذف أي بيانات من الجهاز.")
            },
            confirmButton = {
                TextButton(onClick = { confirmForgot = false; viewModel.forgotPin() }) { Text("خروج وإلغاء الرمز") }
            },
            dismissButton = { TextButton(onClick = { confirmForgot = false }) { Text("رجوع") } },
        )
    }
}

@Composable
private fun NumPadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .then(if (label.isNotEmpty()) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
