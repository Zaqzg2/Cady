package com.cady.cadysalesapp.data.weborders

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.data.repository.AccountRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks for new website orders while the app is closed. WorkManager's shortest period is 15 minutes, so an order
 * is announced within ~15 minutes (later if the phone is dozing or the maker's battery saver is strict) — the
 * price of not having a server that can push. Like the backup job it is a plain CoroutineWorker that reaches Hilt
 * through an EntryPoint, so nothing about WorkManager's initialisation changes.
 */
class WebOrdersCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): WebOrdersRepository
        fun notifier(): WebOrdersNotifier
        fun auth(): FirebaseAuth
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            // Signed out in the meantime (or the saved sign-in could not be restored in time): nothing to read with.
            if (deps.auth().settledUser(AUTH_WAIT_MS) == null) return Result.success()
            // Alerts off or notifications not allowed: skip the read, there is nothing it could announce with.
            if (!deps.notifier().canNotify()) return Result.success()
            val orders = deps.repository().fetchRecent()
            deps.notifier().announce(orders)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline, or the rules refused: the next period simply tries again (a retry would only pile up back-offs).
            Log.w(TAG, "Web-order check failed: ${e.message}")
            Result.success()
        }
    }

    /**
     * Firebase restores the saved sign-in by itself after the process starts, and a background start can ask
     * before that is done — reading `currentUser` at once would wrongly look signed out. (Same reason as
     * SessionViewModel's settledCloudUser.)
     */
    private suspend fun FirebaseAuth.settledUser(timeoutMs: Long): FirebaseUser? {
        currentUser?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            callbackFlow<FirebaseUser?> {
                val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
                this@settledUser.addAuthStateListener(listener)
                awaitClose { this@settledUser.removeAuthStateListener(listener) }
            }.first()
        }
    }

    companion object {
        const val UNIQUE_NAME = "cady_web_orders_check"
        private const val TAG = "CadyWebOrders"
        private const val AUTH_WAIT_MS = 5_000L
    }
}

object WebOrdersScheduler {
    /** Idempotent: on schedules the periodic check (keeping an existing one's timing), off cancels it. */
    fun apply(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(WebOrdersCheckWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WebOrdersCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            WebOrdersCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

/**
 * Keeps the background check in step with who is signed in: it runs only for a manager account that has the
 * alerts switched on, and is cancelled — together with any notification still showing — on sign-out, when the
 * account is not a manager, or when the manager switches the alerts off. Started once from the Application.
 */
@Singleton
class WebOrdersAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val settings: WebOrdersAlertSettings,
    private val notifier: WebOrdersNotifier,
) {
    fun start(scope: CoroutineScope) {
        notifier.ensureChannel()
        scope.launch {
            combine(
                accountRepository.currentUser.map { it?.role == UserRole.MANAGER }.distinctUntilChanged(),
                settings.alertsEnabled,
            ) { isManager, enabled -> isManager && enabled }
                .distinctUntilChanged()
                .collect { active ->
                    try {
                        WebOrdersScheduler.apply(context, active)
                        if (!active) notifier.cancel()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("CadyWebOrders", "Could not update the web-order check: ${e.message}")
                    }
                }
        }
    }
}
