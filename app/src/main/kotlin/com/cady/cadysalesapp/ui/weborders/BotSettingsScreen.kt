package com.cady.cadysalesapp.ui.weborders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadysalesapp.data.weborders.BotConfig
import com.cady.cadysalesapp.ui.common.BackButton
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.DecimalKeyboard
import com.cady.cadysalesapp.ui.common.SectionTitle
import com.cady.cadysalesapp.ui.common.parseNumber
import java.util.Locale

/**
 * The manager's rules for the AI seller on the public sales page: on/off, the carton price, how much it may take
 * off in total, the size of each concession and how long a quote stays valid. The Worker applies these in code
 * (the lowest price is never sent to the language model), and re-reads them about once a minute.
 */
@Composable
fun BotSettingsScreen(
    onBack: () -> Unit,
    viewModel: BotSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var seeded by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var maxText by rememberSaveable { mutableStateOf("") }
    var stepText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }

    // The fields start from what is saved (or the defaults when nothing is) — once, so typing is never overwritten.
    LaunchedEffect(state.loading) {
        if (!state.loading && !seeded) {
            val current = state.saved ?: BotConfig()
            enabled = current.enabled
            priceText = current.price.toString()
            maxText = current.maxDiscount.toString()
            stepText = current.step.toString()
            hoursText = current.validHours.toString()
            seeded = true
        }
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.dismissMessage()
    }

    val (draft, problem) = checkDraft(
        enabled = enabled,
        price = parseNumber(priceText)?.toLong(),
        maxDiscount = parseNumber(maxText)?.toLong(),
        step = parseNumber(stepText)?.toLong(),
        hours = parseNumber(hoursText)?.toLong(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CadyAppBar(
                title = { Text("البائع الآلي") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            CadyCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("تشغيل البائع الآلي", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "عند الإيقاف تعود الصفحة إلى أجوبة القائمة الجاهزة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            SectionTitle("التسعير (بالريال)")
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("سعر الكرتونة") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it },
                label = { Text("أقصى خصم للكرتونة (0 يوقف التفاوض)") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = stepText,
                onValueChange = { stepText = it },
                label = { Text("حجم خطوة التنازل") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it },
                label = { Text("صلاحية العرض (ساعات)") },
                singleLine = true,
                keyboardOptions = DecimalKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )

            if (seeded) {
                CadyCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (draft != null) {
                            Text(
                                "سلّم التفاوض: " + draft.ladder.joinToString(" ثم ") { money(it) } + " (آخر سعر)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "أقل سعر يصله البائع ${money(draft.floor)} ريال، ولا يُرسل لأي نموذج ذكاء ولا يظهر في الصفحة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Button(
                onClick = { draft?.let { viewModel.save(it) } },
                enabled = seeded && draft != null && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.saving) "جارٍ الحفظ…" else "حفظ") }
        }
    }
}

private fun money(value: Long): String = String.format(Locale.US, "%,d", value)

/** The settings as typed, or the first thing that is wrong with them. */
private fun checkDraft(
    enabled: Boolean,
    price: Long?,
    maxDiscount: Long?,
    step: Long?,
    hours: Long?,
): Pair<BotConfig?, String?> {
    if (price == null || price <= 0) return null to "اكتب سعر الكرتونة"
    if (maxDiscount == null || maxDiscount < 0) return null to "اكتب أقصى خصم للكرتونة (0 يوقف التفاوض)"
    if (maxDiscount >= price) return null to "أقصى خصم يجب أن يكون أقل من سعر الكرتونة"
    if (step == null || step <= 0) return null to "اكتب حجم خطوة التنازل"
    if (hours == null || hours <= 0) return null to "اكتب مدة صلاحية العرض بالساعات"
    return BotConfig(enabled, price, maxDiscount, step, hours) to null
}
