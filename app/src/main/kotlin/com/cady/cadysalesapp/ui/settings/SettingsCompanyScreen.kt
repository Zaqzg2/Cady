package com.cady.cadysalesapp.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.LocalFileImage
import com.cady.cadysalesapp.ui.common.SignaturePad
import com.cady.cadysalesapp.ui.common.rememberSignaturePadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsCompanyScreen(viewModel: SettingsCompanyViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signatureState = rememberSignaturePadState()

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var repDisplayName by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // Populate the fields once from the loaded settings — after that, the
    // user's own edits are the source of truth until they tap "حفظ".
    LaunchedEffect(settings) {
        if (!initialized) {
            name = settings.companyName
            address = settings.companyAddress
            phone = settings.companyPhone
            footer = settings.invoiceFooterText
            repDisplayName = settings.repDisplayName
            initialized = true
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bitmap != null) {
                    val file = File(context.filesDir, "company/logo.png")
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    withContext(Dispatchers.Main) { viewModel.updateLogo(file.absolutePath) }
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("بيانات الشركة") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("شعار الشركة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (!settings.companyLogoPath.isNullOrBlank()) {
                        LocalFileImage(
                            path = settings.companyLogoPath,
                            contentDescription = "شعار الشركة",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { logoPickerLauncher.launch("image/*") }) {
                    Text(if (settings.companyLogoPath.isNullOrBlank()) "اختر شعارًا" else "تغيير الشعار")
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الشركة") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = repDisplayName,
                onValueChange = { repDisplayName = it },
                label = { Text("اسم المندوب الظاهر بالمستندات (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = footer,
                onValueChange = { footer = it },
                label = { Text("نص تذييل الفاتورة") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text("التوقيع الافتراضي", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!settings.repSignaturePath.isNullOrBlank() && signatureState.isEmpty) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        LocalFileImage(
                            path = settings.repSignaturePath,
                            contentDescription = "التوقيع المحفوظ",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { viewModel.clearDefaultSignature() }) { Text("مسح التوقيع المحفوظ") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "أو ارسم توقيعًا جديدًا هنا ليحل محله عند الحفظ:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            SignaturePad(state = signatureState, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { signatureState.clear() }) { Text("مسح") }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val newSignaturePath = if (!signatureState.isEmpty) {
                        val file = File(context.filesDir, "company/default_signature.png")
                        if (signatureState.saveTo(file)) file.absolutePath else null
                    } else {
                        null
                    }
                    viewModel.save(name, address, phone, footer, repDisplayName, newSignaturePath)
                },
                enabled = saveState !is SettingsSaveState.Saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (saveState is SettingsSaveState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("حفظ")
                }
            }
        }
    }

    if (saveState is SettingsSaveState.Saved) {
        AlertDialog(
            onDismissRequest = viewModel::acknowledgeSaved,
            title = { Text("تم الحفظ ✓") },
            text = {},
            confirmButton = { TextButton(onClick = viewModel::acknowledgeSaved) { Text("حسنًا") } },
        )
    }
}
