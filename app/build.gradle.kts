import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.x: Compose compiler is a Kotlin plugin; no separate composeCompiler version needed.
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Release signing helpers
//
// Credential priority (highest wins):
//   1. keystore.properties file in the project root
//   2. Gradle project properties  (-Pproperty=value or gradle.properties)
//   3. Environment variables       (CI-friendly)
//
// To create a release keystore locally (one-time setup):
//   keytool -genkeypair -v -keystore release.jks \
//     -alias coreguard -keyalg RSA -keysize 4096 -validity 10000
// Then fill in keystore.properties (add to .gitignore so it is NEVER committed):
//   storeFile=<absolute or project-relative path to release.jks>
//   storePassword=<keystore password>
//   keyAlias=coreguard
//   keyPassword=<key password>
// ---------------------------------------------------------------------------
private fun loadKeystoreProps(project: Project): Properties {
    val props = Properties()
    val propsFile = project.rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { props.load(it) }
    }
    return props
}

android {
    namespace = "com.coldboar.coreguard"
    // API 35 = Android 15 (Vanilla Ice Cream) – latest stable.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coldboar.coreguard"
        // API 26 = Android 8 (Oreo). Covers >99 % of active devices (2025).
        minSdk = 26
        // API 35 satisfies current Google Play target-SDK requirements.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val ksp = loadKeystoreProps(project)

    fun resolveSigningProp(keystoreKey: String, gradlePropKey: String, envKey: String): String? =
        ksp.getProperty(keystoreKey)
            ?: project.findProperty(gradlePropKey)?.toString()
            ?: System.getenv(envKey)

    val storeFilePath  = resolveSigningProp("storeFile",      "SIGNING_STORE_FILE",     "SIGNING_STORE_FILE")
    val storePass      = resolveSigningProp("storePassword",  "SIGNING_STORE_PASSWORD", "SIGNING_STORE_PASSWORD")
    val keyAliasValue  = resolveSigningProp("keyAlias",       "SIGNING_KEY_ALIAS",      "SIGNING_KEY_ALIAS")
    val keyPassValue   = resolveSigningProp("keyPassword",    "SIGNING_KEY_PASSWORD",   "SIGNING_KEY_PASSWORD")

    // Only create the release signing config when all four credentials are present.
    // If any is missing the release build remains UNSIGNED – never fall back to the debug keystore.
    val hasAllSigningCreds = listOf(storeFilePath, storePass, keyAliasValue, keyPassValue).all { !it.isNullOrBlank() }

    if (hasAllSigningCreds) {
        signingConfigs {
            create("release") {
                storeFile     = file(storeFilePath!!)
                storePassword = storePass
                keyAlias      = keyAliasValue
                keyPassword   = keyPassValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply signing config only when credentials are available.
            // IMPORTANT: do NOT add signingConfig = signingConfigs.getByName("debug") here.
            if (hasAllSigningCreds) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    // composeOptions.kotlinCompilerExtensionVersion is not used with Kotlin 2.x –
    // the Compose compiler is now bundled via the kotlin.plugin.compose Gradle plugin.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Fail the build on lint errors so CI catches regressions early.
        abortOnError = true
        // Keep warnings as warnings (non-fatal) to avoid noise on existing code.
        warningsAsErrors = false
        // Write a checkstyle-compatible XML report for CI artefact upload.
        xmlReport = true
        htmlReport = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
