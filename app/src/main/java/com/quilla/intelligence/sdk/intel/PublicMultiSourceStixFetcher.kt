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

    private val expectedFeedHashes = mapOf(
        DEFAULT_FEEDS[0].url to "28da50042006281d56c17dff08f06bba3ba310bc18cc23040780850131b2efdb",
        DEFAULT_FEEDS[1].url to "02bfceea5a2c32b159f11569736448398e35e7f0fc7d137385b943ec60697360",
        DEFAULT_FEEDS[2].url to "d0d546c388207e8a162eb3901259ee20c0ddd03efeff145d3cc9b3a0a02a5419",
        DEFAULT_FEEDS[3].url to "df1bcaa78abc7b85781b1ebc2daa3cc225371e2024d9ef96e84f80f927256586",
        DEFAULT_FEEDS[4].url to "6fe92193d9e17c21a16eb7abe93a418a2e40c0176dcb56fb30539f84136391bb",
        DEFAULT_FEEDS[5].url to "82143861aa57cf570acc19023a7059dc5d3901202dd7338b418a83169e1e7e87",
        DEFAULT_FEEDS[6].url to "c40ca826d3eeef1e095af18d77531246b4849d2fa350464c07326d1b12015b50",
        DEFAULT_FEEDS[7].url to "a2387f14ae7e7f176b0bd543be9b5ff151c77a22377cee5ae38ac5c3c4973a20",
        DEFAULT_FEEDS[8].url to "0046552adf6127ebcaeac9f825a8082a9fa201dd7c921bb7d596fb0c02f12c24",
        DEFAULT_FEEDS[9].url to "b2327156670ed5c1748600fa2c7a2a1756496c53de4534394ab6b80d57b13ed5",
        DEFAULT_FEEDS[10].url to "47270c7236d55e2fa2a05a3fa432da79af138bbbe2b7f243109bfec0686996bf",
        DEFAULT_FEEDS[11].url to "0c14a0eab0404adfdf93d224a6be3bedb0c5dd4c3630a443d338ccfa70dc04e7"
    )

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
            // Per-feed isolation: one throwing/malformed feed must not discard others.
            val source = try {
                fetchFeedDetailed(feed)
            } catch (t: Throwable) {
                fail(feed, "feed exception: ${t.message ?: t.javaClass.simpleName}")
            }
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
                val parsed = try {
                    parseStixBundle(
                        json = download.bytes.toString(Charsets.UTF_8),
                        sourceFeed = feed.name,
                        ttlTimestamp = nowMs() + TimeUnit.DAYS.toMillis(feed.ttlDays)
                    )
                } catch (t: Throwable) {
                    return fail(feed, "malformed STIX: ${t.message ?: t.javaClass.simpleName}")
                }
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
