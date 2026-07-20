package com.coldboar.coreguard

import android.os.Build
import android.os.Debug

// ---------------------------------------------------------------------------
// Evaluators
// Each evaluator accepts lambda parameters so it can be unit-tested without
// an Android device by injecting fake values.
// ---------------------------------------------------------------------------

/**
 * Detects whether a Java debugger is currently attached to this process.
 *
 * @param isDebuggerConnected Lambda that returns the actual debugger state.
 *                            Defaults to [Debug.isDebuggerConnected].
 */
class DebuggerCheckEvaluator(
    private val isDebuggerConnected: () -> Boolean = { Debug.isDebuggerConnected() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val attached = isDebuggerConnected()
        return SecurityCheckResult(
            id = "debugger",
            displayName = "Debugger Attached",
            state = if (attached) SecurityCheckState.FAIL else SecurityCheckState.PASS,
            explanation = if (attached)
                "A Java debugger is currently connected. An attacker could inspect or alter app state."
            else
                "No debugger detected. The app is running without a debugger attached."
        )
    }
}

/**
 * Detects common emulator indicators using build properties.
 *
 * @param fingerprint  [Build.FINGERPRINT] value (injectable for tests).
 * @param model        [Build.MODEL] value.
 * @param manufacturer [Build.MANUFACTURER] value.
 * @param brand        [Build.BRAND] value.
 * @param hardware     [Build.HARDWARE] value.
 * @param product      [Build.PRODUCT] value.
 */
class EmulatorCheckEvaluator(
    private val fingerprint: String = Build.FINGERPRINT,
    private val model: String = Build.MODEL,
    private val manufacturer: String = Build.MANUFACTURER,
    private val brand: String = Build.BRAND,
    private val hardware: String = Build.HARDWARE,
    private val product: String = Build.PRODUCT
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val isEmulator = fingerprint.startsWith("generic")
            || fingerprint.contains("emulator", ignoreCase = true)
            || model.contains("google_sdk", ignoreCase = true)
            || model.contains("emulator", ignoreCase = true)
            || model.contains("android sdk built for x86", ignoreCase = true)
            || manufacturer.contains("genymotion", ignoreCase = true)
            || brand.startsWith("generic", ignoreCase = true)
            || product.contains("sdk_gphone", ignoreCase = true)
            || hardware.contains("goldfish", ignoreCase = true)
            || hardware.contains("ranchu", ignoreCase = true)

        return SecurityCheckResult(
            id = "emulator",
            displayName = "Emulator Detected",
            state = if (isEmulator) SecurityCheckState.WARN else SecurityCheckState.PASS,
            explanation = if (isEmulator)
                "Device characteristics suggest this is an emulator. Security guarantees may differ from a real device."
            else
                "No emulator indicators found. Running on what appears to be a physical device."
        )
    }
}

/**
 * Checks for common root indicators such as known su binary paths and build tags.
 *
 * @param suPaths         Paths to probe for su binary (injectable for tests).
 * @param buildTags       [Build.TAGS] value.
 * @param fileExistsCheck Lambda that returns true when a path exists on disk.
 */
class RootCheckEvaluator(
    private val suPaths: List<String> = listOf(
        "/system/app/Superuser.apk",
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    ),
    private val buildTags: String = Build.TAGS ?: "",
    private val fileExistsCheck: (String) -> Boolean = { path ->
        try { java.io.File(path).exists() } catch (_: Exception) { false }
    }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val testKeysPresent = buildTags.contains("test-keys")
        val suFound = suPaths.any { fileExistsCheck(it) }
        val isRooted = testKeysPresent || suFound

        return SecurityCheckResult(
            id = "root",
            displayName = "Root Indicators",
            state = if (isRooted) SecurityCheckState.FAIL else SecurityCheckState.PASS,
            explanation = if (isRooted)
                "Root indicators detected (su binary or test-keys build). The device may be compromised."
            else
                "No common root indicators found. Note: advanced root frameworks may not be detected."
        )
    }
}

/**
 * Reports whether the current build is a debug build.
 *
 * @param isDebugBuild Whether [BuildConfig.DEBUG] is true.
 */
class BuildTypeCheckEvaluator(
    private val isDebugBuild: Boolean = BuildConfig.DEBUG
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return SecurityCheckResult(
            id = "build_type",
            displayName = "Build Type",
            state = if (isDebugBuild) SecurityCheckState.WARN else SecurityCheckState.PASS,
            explanation = if (isDebugBuild)
                "This is a DEBUG build. Debug builds have relaxed security and must not be distributed."
            else
                "This is a RELEASE build with code shrinking and obfuscation enabled."
        )
    }
}

/**
 * Checks the APK signing certificate by comparing the first certificate's SHA-256
 * hash against an expected value.
 *
 * In this demo build, [expectedSha256] defaults to empty string which always
 * produces WARN (unknown/not configured). Integrate a real certificate hash in
 * production before distributing.
 *
 * @param actualSha256  Lambda returning the runtime certificate hash (injectable).
 * @param expectedSha256 Expected SHA-256 hash. Empty string means "not configured".
 */
class SignatureCheckEvaluator(
    private val actualSha256: () -> String,
    private val expectedSha256: String = ""
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        if (expectedSha256.isEmpty()) {
            return SecurityCheckResult(
                id = "signature",
                displayName = "App Signature",
                state = SecurityCheckState.WARN,
                explanation = "No expected certificate hash configured. Signature pinning is not active."
            )
        }

        val actual = actualSha256()
        val matches = actual.equals(expectedSha256, ignoreCase = true)

        return SecurityCheckResult(
            id = "signature",
            displayName = "App Signature",
            state = if (matches) SecurityCheckState.PASS else SecurityCheckState.FAIL,
            explanation = if (matches)
                "APK signing certificate matches the expected hash."
            else
                "APK signing certificate does NOT match the expected hash. Possible repackaging."
        )
    }
}
