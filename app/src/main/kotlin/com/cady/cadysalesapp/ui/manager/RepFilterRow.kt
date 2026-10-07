package com.cady.cadysalesapp.ui.manager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cady.cadysalesapp.data.local.entity.UserAccountEntity

/**
 * "All reps / one rep" chips, shared by every screen where a manager sees the reps' data
 * together (customers, documents, live activity). Only ever shown to a manager.
 */
@Composable
fun RepFilterRow(
    reps: List<UserAccountEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("كل المندوبين") })
        reps.forEach { rep ->
            FilterChip(selected = selectedId == rep.id, onClick = { onSelect(rep.id) }, label = { Text(rep.displayName) })
        }
    }
}
