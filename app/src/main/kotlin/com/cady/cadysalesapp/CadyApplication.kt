package com.cady.cadysalesapp

import android.app.Application
import com.cady.cadysalesapp.data.backup.AutoBackupScheduler
import com.cady.cadysalesapp.data.backup.BackupSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt's dependency graph is rooted here. Everything DbService/AccountService/etc.
 * did as singletons in the Flutter app's AppProvider becomes a set of Hilt-provided
 * singletons instead (see di/), reachable via constructor injection everywhere else.
 *
 * TODO(perf, needs a real device to verify): the screen recording that flagged a
 * slow cold start showed ~10s of blank time before Login even appears. themes.xml
 * now shows a branded window background instead of blank white for that gap, but
 * the gap itself is still there — Firebase Auth/Firestore's own ContentProvider
 * auto-inits before this class's onCreate() even runs, and this is currently an
 * unminified debug build (see app/build.gradle.kts's isMinifyEnabled comment).
 * Disabling that auto-init (manifest `<provider tools:node="remove">` for
 * com.google.firebase.provider.FirebaseInitProvider) and calling
 * FirebaseApp.initializeApp() manually on a background dispatcher would very
 * likely help, but every Firebase call site (AccountRepository first among them)
 * would need auditing to make sure nothing can run before that init completes —
 * not something to change without a device to actually test the login flow on.
 */
@HiltAndroidApp
class CadyApplication : Application() {

    @Inject
    lateinit var backupSettings: BackupSettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Re-assert the automatic-backup schedule on every start. WorkManager keeps its own job
        // across restarts, so this is normally a no-op (ExistingPeriodicWorkPolicy.UPDATE) — it
        // matters after a restore of app settings onto a new device, where the switch is "on"
        // but no job exists yet.
        appScope.launch {
            try {
                AutoBackupScheduler.apply(
                    this@CadyApplication,
                    backupSettings.autoBackupEnabled.first(),
                    backupSettings.autoBackupFrequency.first(),
                )
            } catch (e: Exception) {
                android.util.Log.w("CadyBackup", "Could not re-apply the backup schedule: ${e.message}")
            }
        }
    }
}
