package com.cady.cadysalesapp.data.repository

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface BiometricResult {
    data object Success : BiometricResult

    /** The person chose to stop (Cancel, or tapped outside the sheet) — not an error, nothing to explain. */
    data object Cancelled : BiometricResult

    /**
     * The SYSTEM cancelled the sheet (the window did not have focus yet, the sensor was busy…). The person
     * did nothing wrong and nothing was decided, so callers ask again instead of silently giving up.
     */
    data class Interrupted(val code: Int, val message: String) : BiometricResult

    /** A real failure; [message] already says what it was and ends with the system's error code. */
    data class Error(val message: String, val code: Int = 0) : BiometricResult
}

/**
 * Why the fingerprint/face option can or cannot be used right now. The settings
 * screen shows [message] so a disabled switch always says what to do about it,
 * instead of the old blanket "not available on this device".
 */
enum class BiometricAvailability(val message: String?) {
    AVAILABLE(null),
    NO_HARDWARE("هذا الجهاز لا يحتوي على مستشعر بصمة أو وجه"),
    HW_UNAVAILABLE("مستشعر البصمة غير متاح حاليًا — أعد المحاولة بعد قليل"),
    NONE_ENROLLED("لا توجد بصمة مسجّلة على الهاتف — سجّلها من إعدادات الهاتف أولًا"),
    UNAVAILABLE("البصمة غير متاحة على هذا الجهاز حاليًا"),
}

/**
 * Uses the stable (non-alpha) androidx.biometric:biometric:1.1.0 API.
 *
 * BIOMETRIC_WEAK, not BIOMETRIC_STRONG: "weak" is the Android class that means
 * "Class 2 or better", so it already includes every Class 3 (strong)
 * fingerprint sensor. Asking for STRONG only made the switch refuse phones
 * whose maker files its fingerprint/face unlock under Class 2 — and this lock
 * guards the app screen only, it never unlocks a cryptographic key, so the
 * stricter class buys nothing here.
 */
@Singleton
class BiometricAuthHelper @Inject constructor() {

    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun availability(activity: FragmentActivity): BiometricAvailability =
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }

    fun isBiometricAvailable(activity: FragmentActivity): Boolean =
        availability(activity) == BiometricAvailability.AVAILABLE

    /** The raw answer of BiometricManager (0 = OK, 11 = nothing enrolled, 12 = no sensor, 1 = busy, -1/-2 = unknown). */
    fun rawStatus(activity: FragmentActivity): Int =
        BiometricManager.from(activity).canAuthenticate(authenticators)

    /** Arabic wording for the system's error codes, always followed by the code itself (so it can be reported). */
    private fun explain(code: Int, system: CharSequence): String {
        val reason = when (code) {
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "مستشعر البصمة غير متاح الآن"
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "تعذّرت معالجة البصمة — جرّب مرة أخرى"
            BiometricPrompt.ERROR_TIMEOUT -> "انتهت مهلة البصمة"
            BiometricPrompt.ERROR_NO_SPACE -> "لا توجد مساحة كافية في الجهاز"
            BiometricPrompt.ERROR_CANCELED -> "ألغى النظام نافذة البصمة"
            BiometricPrompt.ERROR_LOCKOUT -> "محاولات كثيرة خاطئة — انتظر نحو 30 ثانية ثم أعد المحاولة"
            BiometricPrompt.ERROR_VENDOR -> "خطأ من مصنّع الجهاز"
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                "أوقف النظام البصمة بعد محاولات كثيرة — افتح قفل الهاتف بالرمز ثم أعد المحاولة"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "لا توجد بصمة مسجّلة على الهاتف"
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "لا يوجد مستشعر بصمة في هذا الجهاز"
            14 -> "لا يوجد قفل شاشة على الهاتف"
            15 -> "يلزم تحديث أمان النظام"
            else -> system.toString().ifBlank { "خطأ غير معروف" }
        }
        return "$reason (رمز $code)"
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "تأكيد الهوية",
        subtitle: String = "استخدم بصمتك أو وجهك لفتح كادي",
    ): BiometricResult {
        // A definite "no" needs no sheet. Anything unclear (the sensor busy, an answer some phones give that
        // does not match what the sheet then does) is tried for real — the sheet itself says why it failed.
        val status = availability(activity)
        if (status == BiometricAvailability.NO_HARDWARE || status == BiometricAvailability.NONE_ENROLLED) {
            return BiometricResult.Error(status.message ?: "البصمة غير متاحة", rawStatus(activity))
        }
        return showPrompt(activity, title, subtitle)
    }

    private suspend fun showPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!continuation.isActive) return
                val result = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricResult.Cancelled
                    // Used to be lumped in with "the person cancelled" and swallowed silently — which is exactly
                    // what a sheet the system cancelled before it ever showed looks like: nothing happens at all.
                    BiometricPrompt.ERROR_CANCELED -> BiometricResult.Interrupted(errorCode, explain(errorCode, errString))
                    else -> BiometricResult.Error(explain(errorCode, errString), errorCode)
                }
                continuation.resume(result)
            }

            override fun onAuthenticationFailed() {
                // A single failed attempt (e.g. unrecognized fingerprint) — the
                // system prompt itself lets the person retry, so this isn't a
                // terminal result and the coroutine stays suspended.
            }
        }

        try {
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("إلغاء")
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation {
                try { prompt.cancelAuthentication() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            // The prompt can refuse to start (activity state, no sensor…). A failed
            // start must come back as a normal result, never as a crash or a hang.
            if (continuation.isActive) {
                continuation.resume(BiometricResult.Error(e.message ?: "تعذّر فتح نافذة البصمة"))
            }
        }
    }
}
