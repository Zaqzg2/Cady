package com.cady.cadysalesapp.ui.manager

import androidx.compose.ui.graphics.Color
import com.cady.cadysalesapp.ui.common.formatDateTime
import com.cady.cadysalesapp.ui.theme.SyncColors
import java.time.Duration
import java.time.Instant

/** How fresh a rep's last sync is, as the one colour every manager screen uses for it. */
fun syncRecencyColor(lastSyncAt: Instant?, now: Instant = Instant.now()): Color {
    if (lastSyncAt == null) return SyncColors.ErrorState
    val age = Duration.between(lastSyncAt, now)
    return when {
        age < Duration.ofHours(24) -> SyncColors.Synced
        age < Duration.ofHours(72) -> SyncColors.Pending
        else -> SyncColors.ErrorState
    }
}

fun syncRecencyText(lastSyncAt: Instant?): String =
    if (lastSyncAt == null) "لم تتم مزامنة بعد" else "آخر مزامنة: ${formatDateTime(lastSyncAt)}"
