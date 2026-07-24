plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.coldboar.coreguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.coldboar.coreguard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Override at build time: -Pcoreguard.verifyUrl=https://your.api.example
        // or env COREGUARD_VERIFY_URL. Empty → UnconfiguredPurchaseVerifier (no premium grant).
        val verifyUrl = (project.findProperty("coreguard.verifyUrl") as String?)
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("COREGUARD_VERIFY_URL")
            ?: ""
        buildConfigField("String", "VERIFICATION_BASE_URL", "\"${verifyUrl.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "coreguard"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    // Release signing: credentials are supplied via environment variables set by CI.
    // Set SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, and
    // SIGNING_KEY_PASSWORD in the build environment. If any variable is absent the
    // release build will be unsigned (suitable for local development only).
    val storeFile = System.getenv("SIGNING_STORE_FILE")
    val storePass = System.getenv("SIGNING_STORE_PASSWORD")
    val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
    val keyPass = System.getenv("SIGNING_KEY_PASSWORD")

    if (storeFile != null && storePass != null && keyAlias != null && keyPass != null) {
        signingConfigs {
            create("release") {
                this.storeFile = file(storeFile)
                this.storePassword = storePass
                this.keyAlias = keyAlias
                this.keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "USE_DEMO_BILLING", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg != null) {
                signingConfig = releaseCfg
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "USE_DEMO_BILLING", "true")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.billing.ktx)

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
