package com.cady.cadysalesapp.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.UiState
import com.cady.cadysalesapp.ui.common.soapBubbles
import com.cady.cadysalesapp.ui.theme.cady

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateFirstManagerClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Brand header — the same gradient and soap bubbles as the hero panels inside the app.
        val extra = MaterialTheme.cady
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
                .soapBubbles(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "كادي",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = extra.onHero,
                )
                Text(
                    text = "إدارة المبيعات والتحصيل",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.onHero.copy(alpha = 0.85f),
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("تسجيل الدخول", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم") },
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
                Spacer(Modifier.height(20.dp))

                if (state is UiState.Error) {
                    Text(
                        text = (state as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = { viewModel.login(username, password, onLoginSuccess) },
                    enabled = state !is UiState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("دخول")
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCreateFirstManagerClick) {
                    Text("أول مرة تستخدم التطبيق؟ إنشاء حساب مدير جديد")
                }
            }
        }
    }
}
