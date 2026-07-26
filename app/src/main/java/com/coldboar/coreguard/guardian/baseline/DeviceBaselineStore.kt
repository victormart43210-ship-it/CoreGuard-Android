package com.coldboar.coreguard.guardian.baseline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.coldboar.coreguard.guardian.DeviceBaseline
import com.coldboar.coreguard.guardian.SecurityFinding
import org.json.JSONArray
import org.json.JSONObject

/**
 * Quilla Private Baseline — local learning of normal posture (Blueprint §11).
 * First seven days are learning mode; deviations are not labeled as compromise.
 */
class DeviceBaselineStore private constructor(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): DeviceBaseline? {
        val created = prefs.getLong(KEY_CREATED, 0L)
        if (created == 0L) return null
        return DeviceBaseline(
            createdAtEpochMillis = created,
            updatedAtEpochMillis = prefs.getLong(KEY_UPDATED, created),
            packageNames = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty(),
            trustedPackages = prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty(),
            securityPatchLevel = prefs.getString(KEY_PATCH, null),
            accessibilityServices = prefs.getStringSet(KEY_A11Y, emptySet()).orEmpty(),
            deviceAdminPackages = prefs.getStringSet(KEY_ADMIN, emptySet()).orEmpty(),
            learningUntilEpochMillis = prefs.getLong(KEY_LEARN_UNTIL, created + LEARNING_MS),
            learningMode = System.currentTimeMillis() < prefs.getLong(KEY_LEARN_UNTIL, created + LEARNING_MS)
        )
    }

    fun ensureStarted(context: Context) {
        if (prefs.getLong(KEY_CREATED, 0L) != 0L) return
        val now = System.currentTimeMillis()
        val packages = installedPackages(context)
        prefs.edit()
            .putLong(KEY_CREATED, now)
            .putLong(KEY_UPDATED, now)
            .putLong(KEY_LEARN_UNTIL, now + LEARNING_MS)
            .putStringSet(KEY_PACKAGES, packages)
            .putString(KEY_PATCH, Build.VERSION.SECURITY_PATCH)
            .putStringSet(KEY_A11Y, accessibilityServices(context))
            .putStringSet(KEY_TRUSTED, emptySet())
            .putStringSet(KEY_ADMIN, emptySet())
            .apply()
    }

    fun markTrusted(packageName: String) {
        val trusted = prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty().toMutableSet()
        trusted.add(packageName)
        prefs.edit().putStringSet(KEY_TRUSTED, trusted).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    /**
     * Refresh snapshots. During learning mode, expand the package set without
     * producing high-confidence compromise claims (caller must respect learningMode).
     */
    fun observeFromFindings(context: Context, findings: List<SecurityFinding>) {
        ensureStarted(context)
        val now = System.currentTimeMillis()
        val packages = installedPackages(context)
        prefs.edit()
            .putLong(KEY_UPDATED, now)
            .putStringSet(KEY_PACKAGES, packages)
            .putString(KEY_PATCH, Build.VERSION.SECURITY_PATCH)
            .putStringSet(KEY_A11Y, accessibilityServices(context))
            .putString(KEY_LAST_FINDING_DIGEST, findings.filter { it.active }.joinToString(",") { it.id })
            .apply()
    }

    /**
     * Explainable deviation score factors (Blueprint §11.5) — never an opaque AI score.
     */
    fun deviationFactors(context: Context): List<Pair<String, Int>> {
        val baseline = current() ?: return emptyList()
        if (baseline.learningMode) {
            return listOf("Learning mode — deviations are informational only" to 0)
        }
        val currentPackages = installedPackages(context)
        val novel = currentPackages - baseline.packageNames - baseline.trustedPackages
        val a11yNow = accessibilityServices(context)
        val a11yNovel = a11yNow - baseline.accessibilityServices
        val patchChanged = Build.VERSION.SECURITY_PATCH != baseline.securityPatchLevel
        return buildList {
            if (novel.isNotEmpty()) add("New packages since baseline (${novel.size})" to (novel.size * 4).coerceAtMost(24))
            if (a11yNovel.isNotEmpty()) add("New accessibility services (${a11yNovel.size})" to 12)
            if (patchChanged) add("Security patch level changed" to 2)
            if (isEmpty()) add("No meaningful baseline deviations" to 0)
        }
    }

    fun exportAuditJson(): String {
        val o = JSONObject()
        o.put("created", prefs.getLong(KEY_CREATED, 0L))
        o.put("updated", prefs.getLong(KEY_UPDATED, 0L))
        o.put("learningUntil", prefs.getLong(KEY_LEARN_UNTIL, 0L))
        o.put("packages", JSONArray(prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toList()))
        o.put("trusted", JSONArray(prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty().toList()))
        return o.toString(2)
    }

    private fun installedPackages(context: Context): Set<String> =
        runCatching {
            context.packageManager.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        }.getOrDefault(emptySet())

    private fun accessibilityServices(context: Context): Set<String> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    companion object {
        private const val PREFS = "guardian_private_baseline"
        private const val KEY_CREATED = "created"
        private const val KEY_UPDATED = "updated"
        private const val KEY_LEARN_UNTIL = "learn_until"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_TRUSTED = "trusted"
        private const val KEY_PATCH = "patch"
        private const val KEY_A11Y = "a11y"
        private const val KEY_ADMIN = "admin"
        private const val KEY_LAST_FINDING_DIGEST = "last_finding_digest"
        private const val LEARNING_MS = 7L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: DeviceBaselineStore? = null

        fun get(context: Context): DeviceBaselineStore =
            instance ?: synchronized(this) {
                instance ?: DeviceBaselineStore(context).also { instance = it }
            }
    }
}
