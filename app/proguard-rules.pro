# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep security check classes (they use reflection internally)
-keep class com.coldboar.coreguard.SecurityChecks { *; }
-keep class com.coldboar.coreguard.SecurityCheckResult { *; }
-keep class com.coldboar.coreguard.SecurityCheckState { *; }

# Keep entitlement interfaces for future billing integration
-keep interface com.coldboar.coreguard.BillingProvider { *; }

# Keep Room entities and DAOs (accessed via reflection by Room's generated code)
-keep class com.coldboar.coreguard.data.local.entity.** { *; }
-keep class com.coldboar.coreguard.data.local.dao.** { *; }

# Keep Quilla domain events (serialised to JSON strings stored in Room)
-keep class com.coldboar.coreguard.domain.quilla.NetworkEvent { *; }
-keep class com.coldboar.coreguard.domain.quilla.RaspEvent { *; }

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
