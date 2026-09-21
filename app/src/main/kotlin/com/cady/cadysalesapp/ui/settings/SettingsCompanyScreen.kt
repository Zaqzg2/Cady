package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsCompanyScreen(viewModel: SettingsCompanyViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // Populate the fields once from the loaded settings — after that, the
    // user's own edits are the source of truth until they tap "حفظ".
    LaunchedEffect(settings) {
        if (!initialized) {
            name = settings.companyName
            address = settings.companyAddress
            phone = settings.companyPhone
            footer = settings.invoiceFooterText
            initialized = true
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
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الشركة") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = footer,
                onValueChange = { footer = it },
                label = { Text("نص تذييل الفاتورة") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.save(name, address, phone, footer) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("حفظ") }
        }
    }
}
