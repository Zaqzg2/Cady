package com.cady.cadysalesapp.ui.common

import com.cady.cadysalesapp.data.sync.RecordCounts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private val weekdayNames = arrayOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
private val monthNames = arrayOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
)

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

fun formatTime(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(timeFormat)

/** "اليوم 14:30" / "أمس 09:10" / "2026/10/04  18:20" — what a list row says about when. */
fun friendlyDateTime(instant: Instant, today: LocalDate = LocalDate.now()): String {
    val day = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (day) {
        today -> "اليوم ${formatTime(instant)}"
        today.minusDays(1) -> "أمس ${formatTime(instant)}"
        else -> formatDateTime(instant)
    }
}

fun weekdayName(day: LocalDate): String = weekdayNames[day.dayOfWeek.value - 1]

/** Gregorian month names as the Yemeni press writes them (1 = يناير). */
fun monthName(month: Int): String = monthNames[(month - 1).coerceIn(0, 11)]

/** The heading of a day's group in a list: "اليوم" / "أمس" / "الأحد 4 أكتوبر". */
fun dayHeaderLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    day == today -> "اليوم"
    day == today.minusDays(1) -> "أمس"
    else -> "${weekdayName(day)} ${day.dayOfMonth} ${monthName(day.monthValue)}" +
        if (day.year != today.year) " ${day.year}" else ""
}
