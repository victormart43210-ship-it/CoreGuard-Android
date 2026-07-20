# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep security check classes (they use reflection internally)
-keep class com.coldboar.coreguard.SecurityChecks { *; }
-keep class com.coldboar.coreguard.SecurityCheckResult { *; }
-keep class com.coldboar.coreguard.SecurityCheckState { *; }

# Keep entitlement interfaces for future billing integration
-keep interface com.coldboar.coreguard.BillingProvider { *; }

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
