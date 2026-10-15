import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// AdMob IDs
// ---------------------------------------------------------------------------
// Google's official *test* IDs — used unconditionally by debug builds so dev taps
// are harmless, and as the fallback for release so a fresh checkout still builds.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerUnitId = "ca-app-pub-3940256099942544/9214589741"

// Real IDs for the shipping build are read from gradle.properties
// (`admobAppId` / `admobBannerUnitId`) so no production ID is ever hard-coded here.
val prodAdmobAppId = (findProperty("admobAppId") as String?)
    ?.takeIf { it.isNotBlank() } ?: testAdmobAppId
val prodBannerUnitId = (findProperty("admobBannerUnitId") as String?)
    ?.takeIf { it.isNotBlank() } ?: testBannerUnitId

// ---------------------------------------------------------------------------
// Release signing
// ---------------------------------------------------------------------------
// Credentials live in a git-ignored keystore.properties (see .gitignore). When it
// is absent the release build falls back to the debug key so `assembleRelease`
// still produces an installable APK for local testing.
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasReleaseKeystore) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.hafeziquran.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hafeziquran.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }
        // Overridden per build type below: test ID in debug, real ID in release.
        manifestPlaceholders["admobAppId"] = prodAdmobAppId

        // Ship only the two ABIs real phones use. Pdfium's native libs for
        // x86/x86_64 are ~5 MB each and only matter to emulators.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Only created when a keystore.properties is present; a missing file must
        // not fail configuration.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Debug always ships Google's test Ads so development taps are safe.
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$testBannerUnitId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real IDs from gradle.properties; falls back to the test IDs if unset.
            manifestPlaceholders["admobAppId"] = prodAdmobAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$prodBannerUnitId\"")
            // Signed with the release key when available, else the debug key so the
            // APK is still installable for local smoke-testing.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The 13 MB mushaf PDF is already compressed; keep it uncompressed-in-APK-safe
    // and never let aapt try to re-compress it (which corrupts Pdfium seeking).
    androidResources {
        noCompress += "pdf"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core / lifecycle ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // --- Compose (BOM keeps every artifact on the same version) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Room ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- DataStore ---
    implementation(libs.androidx.datastore.preferences)

    // --- Hilt ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // --- PDF rendering ---
    // AndroidX-native Pdfium binding. The old `com.github.barteksc:pdfium-android`
    // referenced `android.support.v4.util.ArrayMap`, which is absent in an
    // AndroidX-only app — that caused "Failed resolution of
    // Landroid/support/v4/util/ArrayMap;" at runtime. This fork has no support-lib
    // dependency at all, so no exclude/jetify workaround is needed.
    implementation(libs.pdfium)

    // --- JSON (tasbeeh.json) ---
    implementation(libs.kotlinx.serialization.json)

    // --- Ads (banner on Home only; never shown on the Quran reader) ---
    implementation(libs.play.services.ads)

    // --- Audio (ExoPlayer + MediaSession: streaming, offline, background play) ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
