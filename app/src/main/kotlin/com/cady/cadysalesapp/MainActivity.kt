package com.cady.cadysalesapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.repository.AppThemeMode
import com.cady.cadysalesapp.data.sync.IncomingFileHolder
import com.cady.cadysalesapp.navigation.CadyNavHost
import com.cady.cadysalesapp.ui.theme.CadyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var incomingFileHolder: IncomingFileHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only the very first launch: after a rotation the same Intent is redelivered and
        // must not offer the file a second time.
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val settings by themeViewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }

            CadyTheme(darkTheme = darkTheme, seedColor = settings.themeColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CadyNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /** A .json shared/opened from another app (the manifest filters only accept application/json). */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            else -> null
        }
        if (uri != null) incomingFileHolder.offer(uri)
    }
}
