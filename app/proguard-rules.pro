# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep security check classes
-keep class com.coldboar.coreguard.SecurityCheckResult { *; }
-keep class com.coldboar.coreguard.SecurityCheckState { *; }

# Keep entitlement interfaces for billing integration
-keep interface com.coldboar.coreguard.BillingProvider { *; }

# Google Play Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
