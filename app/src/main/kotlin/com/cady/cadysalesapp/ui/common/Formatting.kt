package com.cady.cadysalesapp.ui.common

import com.cady.cadysalesapp.data.sync.RecordCounts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)

/** Latin digits on purpose, same as formatMoney — the screen and the printed documents never disagree. */
fun formatDateTime(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(dateTimeFormat)

fun formatDate(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(dateFormat)

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes بايت"
    bytes < 1024 * 1024 -> "${(bytes / 1024.0 * 10).roundToInt() / 10.0} ك.ب"
    else -> "${(bytes / (1024.0 * 1024.0) * 10).roundToInt() / 10.0} م.ب"
}

/** "عملاء 2 · فواتير 3" — label-then-number reads correctly without Arabic plural rules. */
fun RecordCounts.summaryText(): String {
    val parts = ArrayList<String>(4)
    if (customers > 0) parts.add("عملاء $customers")
    if (products > 0) parts.add("منتجات $products")
    if (invoices > 0) parts.add("فواتير $invoices")
    if (receipts > 0) parts.add("سندات $receipts")
    return if (parts.isEmpty()) "لا شيء" else parts.joinToString(" · ")
}
