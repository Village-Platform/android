import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

val apiUrlDebug = providers.gradleProperty("AGHOUR_API_URL_DEBUG").getOrElse("http://10.0.2.2:3000/api/v1/")
val apiUrlRelease = providers.gradleProperty("AGHOUR_API_URL_RELEASE").getOrElse("https://api.example.com/api/v1/")

// Upload key for Google Play (Play App Signing re-signs with the app signing key). The key and its
// passwords live outside the repo; without the file, release builds are simply left unsigned.
val uploadKeyProps = Properties().apply {
    val path = providers.gradleProperty("AGHOUR_UPLOAD_KEY_PROPERTIES")
        .getOrElse("${System.getProperty("user.home")}/.android-keys/aghour-legacy.properties")
    file(path).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.aghourservices"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aghourservices"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 36 // Google Play requires API 36+ for new apps (since Aug 31, 2026)
        versionCode = 55
        versionName = "10.0.1"
    }

    signingConfigs {
        if (uploadKeyProps.isNotEmpty()) {
            create("upload") {
                storeFile = file(uploadKeyProps.getProperty("storeFile"))
                storePassword = uploadKeyProps.getProperty("storePassword")
                keyAlias = uploadKeyProps.getProperty("keyAlias")
                keyPassword = uploadKeyProps.getProperty("keyPassword")
            }
        }
    }

    // Each build type reports to its own Firebase project: src/debug/google-services.json is
    // aghour-dev, src/release/google-services.json is aghour-app. There is deliberately no
    // app/google-services.json fallback, so a variant without its own file fails to build
    // instead of silently sending data to the wrong project.
    buildTypes {
        debug {
            // Separate package so debug and release install side by side and never share a
            // Firebase app. aghour-dev must register com.aghourservices.dev.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"$apiUrlDebug\"")
        }
        release {
            // R8: shrink + optimize + obfuscate. Play flags bundles below 25 % obfuscation (DEX
            // optimization, enforced Feb 2027). Crashlytics uploads the mapping file automatically.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$apiUrlRelease\"")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // Not used directly: pins the version Play Services would otherwise pull in (1.1.0, flagged
    // as outdated by Play Console).
    implementation(libs.androidx.fragment)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    // Category / menu icons (Mosque, LocalPharmacy, Storefront…). Large: R8 strips unused icons in release.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    // Category icon images from the API (memory + disk cache, honours the server's Cache-Control).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
