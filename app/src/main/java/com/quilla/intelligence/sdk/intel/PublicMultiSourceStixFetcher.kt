package com.quilla.intelligence.sdk.intel

import com.coldboar.coreguard.net.HardenedHttpsDownloader
import com.coldboar.coreguard.net.HttpTransport
import com.coldboar.coreguard.net.PublicIntelFeedPins
import com.coldboar.coreguard.net.UrlConnectionHttpTransport
import com.quilla.intelligence.sdk.model.StixIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Production [MultiSourceStixFetcher] that pulls public STIX2 IOC bundles from
 * Amnesty Tech investigations, MVT indicator campaigns, and open stalkerware
 * indicator projects.
 *
 * Only SHA-256-pinned immutable URLs from [PublicIntelFeedPins] are consumed.
 * Integrity failures and empty bundles fail closed per source (UNAVAILABLE).
 */
class PublicMultiSourceStixFetcher(
    private val feeds: List<Feed> = DEFAULT_FEEDS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val transport: HttpTransport = UrlConnectionHttpTransport
) : MultiSourceStixFetcher {

    data class Feed(
        val name: String,
        val url: String,
        val sha256Hex: String,
        val maxBytes: Int = 12 * 1024 * 1024,
        val ttlDays: Long = 30L
    )

    override fun fetchReport(): StixFetchReport {
        if (feeds.isEmpty()) {
            return StixFetchReport.unavailable("no STIX feeds configured")
        }
        val merged = LinkedHashMap<String, StixIndicator>()
        val results = mutableListOf<StixSourceResult>()
        var verified = 0
        var failed = 0
        for (feed in feeds) {
            val source = fetchFeedDetailed(feed)
            results += source
            if (source.success) {
                verified++
                for (indicator in source.indicators) {
                    val key = indicator.patternValue.trim().lowercase()
                    if (key.isNotEmpty()) merged.putIfAbsent(key, indicator)
                }
            } else {
                failed++
            }
        }
        return StixFetchReport(
            indicators = merged.values.toList(),
            sourceResults = results,
            verifiedSourceCount = verified,
            failedSourceCount = failed,
            allUnavailable = verified == 0
        )
    }

    override fun fetchAllSources(): List<StixIndicator> = fetchReport().indicators

    private fun fetchFeedDetailed(feed: Feed): StixSourceResult {
        if (PublicIntelFeedPins.isFloatingBranchUrl(feed.url)) {
            return fail(feed, "floating branch URL rejected")
        }
        if (!feed.url.startsWith("https://", ignoreCase = true)) {
            return fail(feed, "non-HTTPS URL rejected")
        }
        if (feed.sha256Hex.isBlank()) {
            return fail(feed, "missing digest pin")
        }

        val download = HardenedHttpsDownloader.download(
            url = feed.url,
            policy = HardenedHttpsDownloader.Policy(
                allowedHosts = PublicIntelFeedPins.ALLOWED_HOSTS,
                maxBytes = feed.maxBytes,
                expectedSha256Hex = feed.sha256Hex,
                acceptHeader = "application/json, */*",
                userAgent = USER_AGENT
            ),
            transport = transport
        )
        return when (download) {
            is HardenedHttpsDownloader.Result.Failure ->
                fail(feed, download.reason)
            is HardenedHttpsDownloader.Result.Success -> {
                val parsed = parseStixBundle(
                    json = download.bytes.toString(Charsets.UTF_8),
                    sourceFeed = feed.name,
                    ttlTimestamp = nowMs() + TimeUnit.DAYS.toMillis(feed.ttlDays)
                )
                if (parsed.isEmpty()) {
                    // Valid transport + digest but empty production bundle ⇒ failure.
                    fail(feed, "empty STIX bundle after verify — UNAVAILABLE")
                } else {
                    StixSourceResult(
                        name = feed.name,
                        url = feed.url,
                        success = true,
                        indicators = parsed,
                        status = StixSourceResult.STATUS_VERIFIED
                    )
                }
            }
        }
    }

    private fun fail(feed: Feed, reason: String): StixSourceResult =
        StixSourceResult(
            name = feed.name,
            url = feed.url,
            success = false,
            failureReason = reason,
            status = StixSourceResult.STATUS_UNAVAILABLE
        )

    companion object {
        private const val MAX_INDICATORS_PER_FEED = Int.MAX_VALUE
        private const val USER_AGENT =
            "CoreGuard-QuillaIntel/1.0 (defensive research; +https://github.com/victormart43210-ship-it/CoreGuard-Android)"

        val DEFAULT_FEEDS: List<Feed> = PublicIntelFeedPins.STIX_RESEARCH_PINS.map { pin ->
            Feed(
                name = pin.name,
                url = pin.url,
                sha256Hex = pin.sha256Hex,
                maxBytes = pin.maxBytes
            )
        }

        fun parseStixBundle(
            json: String,
            sourceFeed: String,
            ttlTimestamp: Long
        ): List<StixIndicator> {
            val out = mutableListOf<StixIndicator>()
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
            if (root.optString("type") != "bundle") return out
            val objects = root.optJSONArray("objects") ?: JSONArray()
            for (i in 0 until objects.length()) {
                if (out.size >= MAX_INDICATORS_PER_FEED) break
                val obj = objects.optJSONObject(i) ?: continue
                if (obj.optString("type") != "indicator") continue
                val pattern = obj.optString("pattern")
                val value = extractPatternValue(pattern)
                if (value.isBlank()) continue
                val type = when {
                    pattern.contains("domain-name") -> "DOMAIN"
                    pattern.contains("ipv4-addr") -> "IP"
                    pattern.contains("file:hashes") -> "HASH"
                    pattern.contains("file:name") || pattern.contains("file:path") -> "PATH"
                    pattern.contains("process:name") -> "PROCESS"
                    pattern.contains("app:id") -> "PACKAGE"
                    else -> "GENERIC"
                }
                out += StixIndicator(
                    id = obj.optString("id").ifBlank { "indicator--${value.hashCode().toUInt()}" },
                    sourceFeed = sourceFeed,
                    indicatorType = type,
                    patternValue = value,
                    description = obj.optString(
                        "description",
                        obj.optString("name", "Public STIX IOC")
                    ),
                    ttlTimestamp = ttlTimestamp
                )
            }
            return out
        }

        private fun extractPatternValue(stixPattern: String): String {
            val match = Regex("'([^']*)'").find(stixPattern)
            return match?.groupValues?.get(1) ?: ""
        }
    }
}
