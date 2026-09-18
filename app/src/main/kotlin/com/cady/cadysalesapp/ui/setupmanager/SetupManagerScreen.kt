package com.cady.cadysalesapp.ui.setupmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.UiState

@Composable
fun SetupManagerScreen(
    onManagerCreated: () -> Unit,
    onGoToLoginClick: () -> Unit,
    viewModel: SetupManagerViewModel = hiltViewModel(),
) {
    val checkResult by viewModel.checkResult.collectAsState()
    val state by viewModel.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("إعداد أول حساب مدير", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))

        when (checkResult) {
            ManagerCheckResult.CHECKING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("جارٍ التأكد من عدم وجود حساب مدير سابق…")
                }
            }

            ManagerCheckResult.MANAGER_EXISTS -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "يوجد حساب مدير مسجّل بالفعل لهذا العمل.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "إذا كان هذا حسابك، سجّل الدخول به بدل إنشاء حساب جديد — إنشاء حساب آخر ينشئ هوية منفصلة وفارغة من كل بياناتك القديمة.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGoToLoginClick, modifier = Modifier.fillMaxWidth()) {
                    Text("الذهاب لتسجيل الدخول")
                }
            }

            ManagerCheckResult.CHECK_UNAVAILABLE -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "تعذّر التأكد من عدم وجود حساب مدير سابق — تحقّق من اتصال الإنترنت.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.recheck() }, modifier = Modifier.fillMaxWidth()) {
                    Text("إعادة المحاولة")
                }
            }

            ManagerCheckResult.SAFE_TO_CREATE -> {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم (أحرف إنجليزية)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                // The deliberate-friction confirmation from the review's fix —
                // a one-tap link is exactly what causes the current app's bug.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(
                        "أؤكد أنه لا يوجد حساب مدير لهذا العمل من قبل",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (state is UiState.Error) {
                    Text((state as UiState.Error).message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        viewModel.createManager(username, password, displayName, confirmed, onManagerCreated)
                    },
                    enabled = confirmed && state !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("إنشاء الحساب")
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onGoToLoginClick, modifier = Modifier.fillMaxWidth()) {
                    Text("عندك حساب بالفعل؟ سجّل الدخول")
                }
            }
        }
    }
}
