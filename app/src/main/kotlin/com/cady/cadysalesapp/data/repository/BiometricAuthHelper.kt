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
 * One of the additions flagged in the original review: the current app has a
 * biometric row in its privacy settings that's wired to a dialog explaining
 * it "needs additional Android-level development, not enabled in this
 * version" — in a native app that additional development is just this class,
 * using the stable (non-alpha) androidx.biometric:biometric:1.1.0 API.
 */
@Singleton
class BiometricAuthHelper @Inject constructor() {

    fun isBiometricAvailable(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(activity: FragmentActivity): BiometricResult = suspendCancellableCoroutine { continuation ->
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

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد الهوية")
            .setSubtitle("استخدم بصمتك أو وجهك لفتح كادي")
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info)

        continuation.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
    }
}
