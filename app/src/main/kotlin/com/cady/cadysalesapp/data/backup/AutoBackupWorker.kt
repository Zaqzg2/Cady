package com.cady.cadysalesapp.data.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * The scheduled automatic backup. A plain CoroutineWorker that reaches Hilt through an
 * EntryPoint instead of @HiltWorker — that keeps WorkManager on its default initializer
 * (no custom WorkerFactory, no extra dependency, nothing to change in CadyApplication).
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun backupService(): BackupService
    }

    override suspend fun doWork(): Result {
        val service = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .backupService()
        return try {
            service.createBackup(BackupKind.AUTO)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CadyBackup", "Automatic backup failed (attempt ${runAttemptCount + 1}): ${e.message}")
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "cady_auto_backup"
        private const val MAX_ATTEMPTS = 3
    }
}

object AutoBackupScheduler {
    /** Idempotent: turning it on (or changing the frequency) replaces the scheduled job, off cancels it. */
    fun apply(context: Context, enabled: Boolean, frequency: AutoBackupFrequency) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
