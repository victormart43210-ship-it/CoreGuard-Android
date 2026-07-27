# CoreGuard R8 / ProGuard rules (release minifyEnabled + shrinkResources).
# Configured from gradle/android-app.gradle (Groovy), not build.gradle.kts.

# ---------------------------------------------------------------------------
# Security checks
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.SecurityChecks { *; }
-keep class com.coldboar.coreguard.SecurityCheckResult { *; }
-keep class com.coldboar.coreguard.SecurityCheckState { *; }
-keep class com.coldboar.coreguard.SecurityCheckRunner { *; }
-keep enum  com.coldboar.coreguard.SecurityCheckState { *; }
-keep class * implements com.coldboar.coreguard.SecurityCheckEvaluator { *; }

# ---------------------------------------------------------------------------
# Billing / entitlements
# ---------------------------------------------------------------------------
-keep interface com.coldboar.coreguard.BillingProvider { *; }
-keep class com.coldboar.coreguard.PurchaseResult { *; }
-keep class com.coldboar.coreguard.PurchaseResult$* { *; }
-keep class com.coldboar.coreguard.PlayBillingProvider { *; }
-keep class com.coldboar.coreguard.Entitlements { *; }
-keep class com.coldboar.coreguard.EntitlementPolicy { *; }
-keep class com.coldboar.coreguard.SubscriptionManager { *; }
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.android.billingclient.** { *; }

# ---------------------------------------------------------------------------
# MVT / VPN / scanner
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.mvt.** { *; }
-keep class com.coldboar.coreguard.mvt.GuardVpnService { *; }

# ---------------------------------------------------------------------------
# Elite / Scam Guard / notification listener
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.elite.** { *; }
-keep class com.coldboar.coreguard.elite.ScamGuardNotificationListener { *; }

# ---------------------------------------------------------------------------
# Jetpack Compose navigation routes
# ---------------------------------------------------------------------------
-keepclassmembers class com.coldboar.coreguard.ui.navigation.CoreGuardRoute {
    *;
}
-keep class com.coldboar.coreguard.ui.navigation.CoreGuardRoute$* { *; }

# ---------------------------------------------------------------------------
# WorkManager security pulse
# ---------------------------------------------------------------------------
-keep class com.coldboar.coreguard.monitor.SecurityPulseWorker { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------
# JNI / native
# ---------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.coldboar.coreguard.NativeTamperGuard { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class com.coreguard.android.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# JSON (org.json used in forensic journal / IOC)
# ---------------------------------------------------------------------------
-keep class org.json.** { *; }

# ---------------------------------------------------------------------------
# Kotlin metadata
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# Crash reporting: preserve line numbers
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
