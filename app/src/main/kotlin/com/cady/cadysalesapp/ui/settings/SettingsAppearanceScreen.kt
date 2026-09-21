package com.cady.cadysalesapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.repository.AppThemeMode
import com.cady.cadysalesapp.ui.theme.ThemeColorPreset

@Composable
fun SettingsAppearanceScreen(viewModel: SettingsAppearanceViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("المظهر") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("الوضع", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.themeMode == AppThemeMode.LIGHT, onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) }, label = { Text("فاتح") })
                FilterChip(selected = settings.themeMode == AppThemeMode.DARK, onClick = { viewModel.setThemeMode(AppThemeMode.DARK) }, label = { Text("داكن") })
                FilterChip(selected = settings.themeMode == AppThemeMode.SYSTEM, onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) }, label = { Text("النظام") })
            }

            Spacer(Modifier.height(24.dp))
            Text("لون الهوية", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeColorPreset.entries.forEach { preset ->
                    val selected = settings.themeColor == preset
                    Column(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset.seed)
                            .clickable { viewModel.setThemeColor(preset) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("اهتزاز عند الحفظ", style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.hapticOnSave, onCheckedChange = viewModel::setHapticOnSave)
            }
        }
    }
}
