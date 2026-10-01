package com.cady.cadysalesapp.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cady.cadysalesapp.data.sync.SyncActivityEntry
import com.cady.cadysalesapp.data.sync.SyncActivityKind
import com.cady.cadysalesapp.data.sync.SyncActivityStatus
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.common.summaryText
import com.cady.cadysalesapp.ui.theme.SyncColors

internal fun statusColor(status: SyncActivityStatus): Color = when (status) {
    SyncActivityStatus.SUCCESS -> SyncColors.Synced
    SyncActivityStatus.PARTIAL -> SyncColors.Pending
    SyncActivityStatus.FAILED -> SyncColors.ErrorState
}

internal fun statusLabel(status: SyncActivityStatus): String = when (status) {
    SyncActivityStatus.SUCCESS -> "تمّت"
    SyncActivityStatus.PARTIAL -> "جزئية"
    SyncActivityStatus.FAILED -> "فشلت"
}

/** One line of the sync log. [onResend] is only passed for exports whose file is still stored. */
@Composable
internal fun ActivityEntryCard(
    entry: SyncActivityEntry,
    modifier: Modifier = Modifier,
    onResend: (() -> Unit)? = null,
) {
    val icon = when (entry.kind) {
        SyncActivityKind.FIREBASE_SYNC -> Icons.Filled.Sync
        SyncActivityKind.MANUAL_EXPORT -> Icons.Filled.Upload
        SyncActivityKind.MANUAL_IMPORT -> Icons.Filled.Download
    }
    val color = statusColor(entry.status)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(entry.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(statusLabel(entry.status), style = MaterialTheme.typography.labelMedium, color = color)
            }
            Text(
                formatDateTime(entry.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.counts.total > 0) {
                Text(entry.counts.summaryText(), style = MaterialTheme.typography.bodyMedium)
            }
            if (!entry.detail.isNullOrBlank() && entry.kind != SyncActivityKind.MANUAL_EXPORT) {
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.kind == SyncActivityKind.MANUAL_EXPORT) {
                Text(
                    if (entry.acknowledged) "✓ أكّد المدير الاستلام" else "بانتظار تأكيد استلام المدير",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.acknowledged) SyncColors.Synced else SyncColors.Pending,
                )
                if (onResend != null) {
                    TextButton(onClick = onResend) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة إرسال الملف")
                    }
                }
            }
        }
    }
}
