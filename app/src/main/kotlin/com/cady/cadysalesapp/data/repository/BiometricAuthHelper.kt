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
    data object Cancelled : BiometricResult
    data class Error(val message: String) : BiometricResult
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

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "تأكيد الهوية",
        subtitle: String = "استخدم بصمتك أو وجهك لفتح كادي",
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!continuation.isActive) return
                val result = if (
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    BiometricResult.Cancelled
                } else {
                    BiometricResult.Error(errString.toString())
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
