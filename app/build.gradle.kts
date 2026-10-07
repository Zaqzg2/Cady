import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.cady.cadysalesapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cady.cadysalesapp"
        // minSdk 24 (Android 7.0) — a reasonable default for broad device coverage on
        // field-sales hardware; raise/lower to match whatever the Flutter app currently ships.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // The release build is the OPTIMIZED one (R8 shrinking + the Compose baseline profiles that
            // only take effect outside debug). A debug build is several times slower to start, so cold
            // start must be judged on this one. It is signed with the debug key purely so it installs
            // directly on a test phone; it is not a store build (that needs the real release keystore).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The CI debug build is the one that must always succeed; the optimized build is extra, so lint's
    // release-only vital checks are not allowed to stop it.
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Modern compilerOptions DSL — the old `kotlinOptions { jvmTarget = "17" }` syntax
// is a hard error on current Kotlin Gradle Plugin versions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // TopAppBar (and CenterAlignedTopAppBar, ModalBottomSheet, etc. later) are
        // marked @ExperimentalMaterial3Api in this Compose BOM — @RequiresOptIn
        // defaults to ERROR level, which is exactly what broke the CI build
        // (HomeScreen.kt / ProductsScreen.kt). One project-wide opt-in here covers
        // every current and future screen instead of a per-file @OptIn everywhere.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        // Phase 9 (UI refresh): long-press (combinedClickable), animateItem, FlowRow and the swipe/gesture
        // helpers sit behind these markers in some Compose releases — one project-wide opt-in, same
        // reasoning as the Material3 one above, so a marker that is stable in this BOM costs nothing.
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

ksp {
    // Exports Room's schema JSON per version for migration testing — no extra Gradle plugin needed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
