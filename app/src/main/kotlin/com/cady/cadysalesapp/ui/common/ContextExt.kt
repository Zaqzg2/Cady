package com.cady.cadysalesapp.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

/**
 * LocalContext inside Compose is not always the Activity itself — themed
 * contexts and dialogs wrap it. A plain `as? FragmentActivity` then quietly
 * yields null, and anything needing the Activity (the biometric prompt) just
 * never appears. Walking the wrappers finds the real one.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

/** Opens the dialer with [phone] typed in (the person still presses call). */
fun Context.dial(phone: String) {
    try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim())))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "لا يوجد تطبيق اتصال على هذا الجهاز", Toast.LENGTH_SHORT).show()
    }
}

/**
 * wa.me wants the number in full international form with digits only. Numbers are typed the way people
 * write them locally ("0771234567", "771234567", "+967 771 234 567"), so they are normalised to Yemen's
 * code (967) first — a number that already carries a country code is left alone.
 */
fun normalizeWhatsAppNumber(raw: String): String {
    var digits = raw.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    return when {
        digits.startsWith("967") -> digits
        digits.startsWith("0") -> "967" + digits.trimStart('0')
        digits.length == 9 && digits.startsWith("7") -> "967$digits"
        else -> digits
    }
}

fun Context.openWhatsApp(phone: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + normalizeWhatsAppNumber(phone))))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "تعذّر فتح واتساب على هذا الجهاز", Toast.LENGTH_SHORT).show()
    }
}

fun Context.openMap(address: String) {
    // A geo: query needs no geocoding of our own — the maps app searches the text.
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "لا يوجد تطبيق خرائط على هذا الجهاز", Toast.LENGTH_SHORT).show()
    }
}
