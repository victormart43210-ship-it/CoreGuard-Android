package com.quilla.intelligence.sdk.intel

import com.quilla.intelligence.sdk.model.StixIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Production [MultiSourceStixFetcher] that pulls public STIX2 IOC bundles from
 * Amnesty Tech investigations, MVT indicator campaigns, and open stalkerware
 * indicator projects.
 *
 * Defensive use only — results feed Quilla correlation / Research, not offensive tooling.
 * Network I/O is synchronous; call from a background thread.
 */
class PublicMultiSourceStixFetcher(
    private val feeds: List<Feed> = DEFAULT_FEEDS,
    private val nowMs: () -> Long = System::currentTimeMillis
) : MultiSourceStixFetcher {

    data class Feed(
        val name: String,
        val url: String,
        val ttlDays: Long = 30L
    )

    override fun fetchAllSources(): List<StixIndicator> {
        val merged = LinkedHashMap<String, StixIndicator>()
        for (feed in feeds) {
            val batch = fetchFeed(feed)
            for (indicator in batch) {
                val key = indicator.patternValue.trim().lowercase()
                if (key.isNotEmpty()) merged.putIfAbsent(key, indicator)
            }
        }
        return merged.values.toList()
    }

    private fun fetchFeed(feed: Feed): List<StixIndicator> {
        if (!feed.url.startsWith("https://", ignoreCase = true)) return emptyList()
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) return emptyList()
            parseStixBundle(
                json = String(bytes, Charsets.UTF_8),
                sourceFeed = feed.name,
                ttlTimestamp = nowMs() + TimeUnit.DAYS.toMillis(feed.ttlDays)
            )
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_BYTES = 8 * 1024 * 1024
        private const val MAX_INDICATORS_PER_FEED = 8_000
        private const val USER_AGENT = "CoreGuard-QuillaIntel/1.0 (defensive research; +https://github.com/victormart43210-ship-it/CoreGuard-Android)"

        val DEFAULT_FEEDS: List<Feed> = listOf(
            Feed(
                "Amnesty Android campaign",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2023-03-29_android_campaign/malware.stix2"
            ),
            Feed(
                "Amnesty NoviSpy",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2024-12-16_serbia_novispy/novispy.stix2"
            ),
            Feed(
                "Amnesty Wintego Helios",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2024-05-02_wintego_helios/wintego_helios.stix2"
            ),
            Feed(
                "Amnesty Pegasus (NSO)",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2021-07-18_nso/pegasus.stix2"
            ),
            Feed(
                "MVT WyrmSpy/DragonEgg",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/2023-07-25_wyrmspy_dragonegg/wyrmspy_dragonegg.stix2"
            ),
            Feed(
                "MVT EagleMsgSpy",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/2024-12-25_eaglemsgspy/eaglemsgspy.stix2"
            ),
            Feed(
                "MVT DarkSword",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/2026-03-30_darksword/darksword.stix2"
            ),
            Feed(
                "Open stalkerware IOCs",
                "https://raw.githubusercontent.com/f00wl/stalkerware-indicators/master/generated/stalkerware.stix2"
            )
        )

        /**
         * Parses a STIX2 bundle into [StixIndicator] records.
         * Exposed for unit tests with fixture JSON (no network).
         */
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
