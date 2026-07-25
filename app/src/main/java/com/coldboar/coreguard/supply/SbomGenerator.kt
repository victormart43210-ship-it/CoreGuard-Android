package com.coldboar.coreguard.supply

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Generates a [CycloneDX](https://cyclonedx.org/) 1.5-compatible Software Bill
 * of Materials (SBOM) in JSON format describing the packages installed on the
 * device that are visible to CoreGuard.
 *
 * The SBOM enumerates every installed package (including third-party SDKs
 * bundled into other apps) that the system exposes via [PackageManager]. On
 * Android 11+ the list is restricted to packages that have declared visibility
 * in their manifest; on older versions all packages are returned.
 *
 * Usage (off the main thread):
 * ```kotlin
 * val json = SbomGenerator(context).generate()
 * ```
 */
class SbomGenerator(private val context: Context) {

    /**
     * Builds and returns the full CycloneDX SBOM JSON string.
     * This is a moderately expensive operation (PackageManager query) and
     * should be called from a background coroutine.
     */
    fun generate(): String {
        val packages = getInstalledPackages()
        return buildCycloneDxJson(packages)
    }

    // -------------------------------------------------------------------------
    // Package discovery
    // -------------------------------------------------------------------------

    private fun getInstalledPackages(): List<PackageInfo> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
    }

    // -------------------------------------------------------------------------
    // CycloneDX JSON construction
    // -------------------------------------------------------------------------

    private fun buildCycloneDxJson(packages: List<PackageInfo>): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val pm = context.packageManager

        val root = JSONObject()
        root.put("bomFormat", "CycloneDX")
        root.put("specVersion", "1.5")
        root.put("serialNumber", "urn:uuid:${UUID.randomUUID()}")
        root.put("version", 1)

        val metadata = JSONObject()
        metadata.put("timestamp", timestamp)

        val toolComponent = JSONObject()
        toolComponent.put("type", "application")
        toolComponent.put("name", "CoreGuard-Android")
        toolComponent.put("version", context.packageManager
            .runCatching { getPackageInfo(context.packageName, 0).versionName }
            .getOrDefault("unknown"))

        val tools = JSONObject()
        tools.put("components", JSONArray().put(toolComponent))
        metadata.put("tools", tools)

        val subject = JSONObject()
        subject.put("type", "application")
        subject.put("name", context.packageName)
        metadata.put("component", subject)
        root.put("metadata", metadata)

        val components = JSONArray()
        for (pkg in packages) {
            val appInfo = pkg.applicationInfo
            val label = if (appInfo != null) {
                try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
            } else {
                pkg.packageName
            }
            val comp = JSONObject()
            comp.put("type", "library")
            comp.put("name", label)
            comp.put("version", pkg.versionName ?: "unknown")
            comp.put("purl", "pkg:android/${pkg.packageName}@${pkg.versionName ?: "unknown"}")

            val props = JSONArray()
            props.put(property("android:packageName", pkg.packageName))
            props.put(property("android:versionCode", packageVersionCode(pkg).toString()))
            props.put(property("android:targetSdk", pkg.applicationInfo?.targetSdkVersion?.toString() ?: "unknown"))
            comp.put("properties", props)
            components.put(comp)
        }
        root.put("components", components)
        return root.toString(2)
    }

    private fun property(name: String, value: String): JSONObject =
        JSONObject().apply { put("name", name); put("value", value) }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode
        else pkg.versionCode.toLong()
}
