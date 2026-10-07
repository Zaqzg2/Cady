package com.cady.cadysalesapp.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** Numeric keypad with a decimal point — for quantities, prices, percentages and amounts. */
val DecimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

private val plainNumber = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)$")
private val thousandsGrouped = Regex("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$")
private val decimalComma = Regex("^\\d+,\\d{1,2}$")

/**
 * A number typed the way people really type it on a phone with an Arabic keyboard: Arabic-Indic
 * (٠١٢٣…) and Persian digits, the Arabic decimal mark (٫), thousands separators ("1,500") and a
 * decimal comma ("2,5"). `toDoubleOrNull()` alone returns null for all of those, which silently
 * turned a perfectly valid "٣" into "nothing entered". Returns null for anything that is not a
 * plain number (letters, "NaN", "1d", empty).
 */
fun parseNumber(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val normalized = StringBuilder(trimmed.length)
    for (ch in trimmed) {
        when (ch) {
            in '\u0660'..'\u0669' -> normalized.append('0' + (ch - '\u0660'))
            in '\u06F0'..'\u06F9' -> normalized.append('0' + (ch - '\u06F0'))
            '\u066B' -> normalized.append('.')
            '\u066C', '\u060C' -> normalized.append(',')
            ' ', '\u00A0' -> Unit
            else -> normalized.append(ch)
        }
    }
    var t = normalized.toString()
    if (t.contains(',')) {
        t = when {
            thousandsGrouped.matches(t) -> t.replace(",", "")
            decimalComma.matches(t) -> t.replace(',', '.')
            else -> return null
        }
    }
    if (!plainNumber.matches(t)) return null
    val value = t.toDoubleOrNull() ?: return null
    return if (value.isNaN() || value.isInfinite()) null else value
}
