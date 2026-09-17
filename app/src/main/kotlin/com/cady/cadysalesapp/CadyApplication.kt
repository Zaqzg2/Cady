package com.cady.cadysalesapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's dependency graph is rooted here. Everything DbService/AccountService/etc.
 * did as singletons in the Flutter app's AppProvider becomes a set of Hilt-provided
 * singletons instead (see di/), reachable via constructor injection everywhere else.
 */
@HiltAndroidApp
class CadyApplication : Application()
