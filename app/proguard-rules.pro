# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ---------------------------------------------------------------------------
# Security checks – keep all data classes to prevent R8 from stripping
# fields that are accessed only via reflection or data-binding.
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.SecurityChecks { *; }
-keep class com.coldboar.coreguard.SecurityCheckResult { *; }
-keep class com.coldboar.coreguard.SecurityCheckState { *; }
-keep class com.coldboar.coreguard.SecurityCheckRunner { *; }
-keep enum  com.coldboar.coreguard.SecurityCheckState { *; }

# Keep all evaluator classes (instantiated by name / reflection in some paths)
-keep class * implements com.coldboar.coreguard.SecurityCheckEvaluator { *; }

# ---------------------------------------------------------------------------
# Billing / entitlement interfaces
# ---------------------------------------------------------------------------
-keep interface com.coldboar.coreguard.BillingProvider { *; }
-keep class com.coldboar.coreguard.PurchaseResult { *; }
-keep class com.coldboar.coreguard.PurchaseResult$* { *; }

# ---------------------------------------------------------------------------
# MVT / shield
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.mvt.** { *; }

# ---------------------------------------------------------------------------
# Jetpack Compose – R8 full-mode rules
# (The kotlin.plugin.compose Gradle plugin adds the standard rules automatically;
# these are extra guards for our own sealed classes used inside NavHost.)
# ---------------------------------------------------------------------------
-keepclassmembers class com.coldboar.coreguard.ui.navigation.CoreGuardRoute {
    *;
}

# ---------------------------------------------------------------------------
# Debugging / crash reporting: preserve line numbers in stack traces.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin metadata – needed by kotlin-reflect and some Compose internals.
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
