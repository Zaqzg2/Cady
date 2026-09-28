package com.cady.cadysalesapp.ui.common

import java.text.NumberFormat
import java.util.Locale

/**
 * Money the way the printed documents show it: Latin digits, comma thousands
 * separators and a trailing " ر.ي". Locale.US is used on purpose — it pins the
 * digit style regardless of the device language, so the screen and the PDF can
 * never disagree. Fractions are kept (up to two digits) only when the amount
 * actually has them.
 */
fun formatMoney(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
    return "${format.format(value)} ر.ي"
}

/** Whole quantities read "3", not "3.0". */
fun formatQuantity(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
