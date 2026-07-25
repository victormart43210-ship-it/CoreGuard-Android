package com.coldboar.coreguard.supply

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A single network event attributed to a third-party SDK call.
 *
 * @param sdkTag       Caller class or package prefix that initiated the call.
 * @param url          Destination URL.
 * @param method       HTTP method (GET, POST, …).
 * @param isSensitive  True when the URL path matches a known sensitive-API pattern
 *                     (e.g. device ID, contacts, location).
 * @param epochMs      Wall-clock timestamp.
 */
data class SdkNetworkEvent(
    val sdkTag: String,
    val url: String,
    val method: String,
    val isSensitive: Boolean,
    val epochMs: Long = System.currentTimeMillis()
)

/**
 * A summary of audited network behaviour for a single SDK.
 */
data class SdkAuditSummary(
    val sdkTag: String,
    val totalRequests: Int,
    val sensitiveRequests: Int,
    val flagged: Boolean
)

/**
 * Real-time SDK behaviour auditor that tracks outbound network calls and flags
 * third-party SDKs that appear to silently exfiltrate user data.
 *
 * Instrumentation is done at the call-site: wherever an outbound HTTP request is
 * constructed (e.g. in a custom network interceptor, OkHttp `Interceptor`, or
 * `URLConnection` wrapper) call [record] with the originating SDK tag and request
 * details. CoreGuard's network defence layer can also feed events here.
 *
 * Sensitive-URL detection is heuristic: URLs whose path contains keywords
 * associated with user data exfiltration are flagged. The keyword list is
 * injectable for testing and can be extended at runtime via [addSensitiveKeyword].
 *
 * Usage:
 * ```kotlin
 * SdkBehaviorAuditor.record("com.example.analytics", "POST", "https://api.example.com/events")
 * val summaries = SdkBehaviorAuditor.summaries()
 * ```
 */
object SdkBehaviorAuditor {

    /** Default keywords whose presence in a request URL signals sensitive-data exfiltration. */
    private val DEFAULT_SENSITIVE_KEYWORDS = setOf(
        "device_id", "deviceid", "imei", "android_id", "advertising_id", "gaid",
        "location", "latitude", "longitude", "gps",
        "contacts", "phone_number", "email",
        "biometric", "fingerprint",
        "token", "auth", "credential", "password", "secret",
        "keylog", "clipboard"
    )

    private val sensitiveKeywords = DEFAULT_SENSITIVE_KEYWORDS.toMutableSet()
    private val events = CopyOnWriteArrayList<SdkNetworkEvent>()

    /** Adds a custom keyword to the sensitive-URL heuristic. */
    fun addSensitiveKeyword(keyword: String) {
        sensitiveKeywords += keyword.lowercase()
    }

    /**
     * Records an outbound network request attributed to [sdkTag].
     *
     * @param sdkTag   Caller package prefix or class name (e.g. `"com.facebook.ads"`).
     * @param method   HTTP method.
     * @param url      Full destination URL.
     */
    fun record(sdkTag: String, method: String, url: String) {
        val sensitive = isSensitiveUrl(url)
        events += SdkNetworkEvent(
            sdkTag = sdkTag,
            url = url,
            method = method.uppercase(),
            isSensitive = sensitive
        )
    }

    /**
     * Returns a per-SDK aggregated summary, sorted so the most-flagged SDKs
     * appear first.
     */
    fun summaries(): List<SdkAuditSummary> =
        events
            .groupBy { it.sdkTag }
            .map { (tag, evts) ->
                val sensitive = evts.count { it.isSensitive }
                SdkAuditSummary(
                    sdkTag = tag,
                    totalRequests = evts.size,
                    sensitiveRequests = sensitive,
                    flagged = sensitive > 0
                )
            }
            .sortedByDescending { it.sensitiveRequests }

    /** All raw events since the last [reset]. */
    fun events(): List<SdkNetworkEvent> = events.toList()

    /** Clears all recorded events. */
    fun reset() = events.clear()

    // -------------------------------------------------------------------------

    internal fun isSensitiveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }
}
