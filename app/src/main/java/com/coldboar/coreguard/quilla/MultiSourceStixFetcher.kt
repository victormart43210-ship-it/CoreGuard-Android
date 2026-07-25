package com.coldboar.coreguard.quilla

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches threat intelligence from multiple public STIX 2.1 indicator feeds and merges
 * them into a unified [StixIndicator] list.
 *
 * Network I/O is synchronous; callers must invoke [fetchAllSources] on a background thread.
 *
 * Additional feeds can be registered at construction time via [extraFeeds]. Each entry is a
 * pair of (feedUrl, sourceName).
 *
 * @param extraFeeds Optional additional (url, sourceName) pairs to ingest alongside the
 *                   built-in feeds.
 */
class MultiSourceStixFetcher(
    private val extraFeeds: List<Pair<String, String>> = emptyList()
) {

    companion object {
        /** 30-day indicator TTL for rule-decay tracking. */
        private const val DEFAULT_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** Amnesty International Android campaign STIX 2.1 bundle. */
        private const val AMNESTY_FEED_URL =
            "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2023-03-29_android_campaign/malware.stix2"

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB sanity cap

        /** Convenience singleton for production use without constructor injection. */
        val Default: MultiSourceStixFetcher by lazy { MultiSourceStixFetcher() }
    }

    /**
     * Fetches and merges all configured STIX 2.1 feeds.
     *
     * Returns an empty list (without throwing) when all feeds fail. Expired indicators
     * (past [StixIndicator.ttlTimestamp]) are excluded from the returned list.
     *
     * Must be called on a background thread.
     */
    fun fetchAllSources(): List<StixIndicator> {
        val now = System.currentTimeMillis()
        val all = mutableListOf<StixIndicator>()

        // Built-in feeds
        all += fetchStixBundle(AMNESTY_FEED_URL, "Amnesty International", now)

        // Caller-supplied feeds
        for ((url, name) in extraFeeds) {
            all += fetchStixBundle(url, name, now)
        }

        // Strip expired indicators
        return all.filter { it.ttlTimestamp > now }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Downloads and parses a single STIX 2.1 bundle from [feedUrl].
     *
     * Returns an empty list on any network or parse error.
     */
    private fun fetchStixBundle(feedUrl: String, sourceName: String, now: Long): List<StixIndicator> {
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) return emptyList()

            parseStixBundle(String(bytes, Charsets.UTF_8), sourceName, now)
        } catch (e: Exception) {
            System.err.println("MultiSourceStixFetcher: fetch failed for $feedUrl: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses a STIX 2.1 bundle JSON string into [StixIndicator] records.
     * Exposed as internal to allow unit-testing the parser without a network call.
     */
    internal fun parseStixBundle(
        json: String,
        sourceName: String,
        now: Long = System.currentTimeMillis()
    ): List<StixIndicator> {
        val indicators = mutableListOf<StixIndicator>()
        val bundle = runCatching { JSONObject(json) }.getOrNull() ?: return indicators
        if (bundle.optString("type") != "bundle") return indicators

        val objects = bundle.optJSONArray("objects") ?: JSONArray()
        for (i in 0 until objects.length()) {
            val obj = objects.optJSONObject(i) ?: continue
            if (obj.optString("type") != "indicator") continue

            val pattern = obj.optString("pattern")
            val id = obj.optString("id")
            val desc = obj.optString("description", "Threat Indicator")

            val value = extractPatternValue(pattern)
            if (value.isBlank()) continue

            val indicatorType = when {
                pattern.contains("domain-name") -> "DOMAIN"
                pattern.contains("ipv4-addr") -> "IP"
                pattern.contains("file:hashes") -> "HASH"
                else -> "GENERIC"
            }

            indicators += StixIndicator(
                id = id,
                sourceFeed = sourceName,
                indicatorType = indicatorType,
                patternValue = value,
                description = desc,
                ttlTimestamp = now + DEFAULT_TTL_MS
            )
        }
        return indicators
    }

    /** Extracts the first single-quoted value from a STIX 2.1 pattern string. */
    private fun extractPatternValue(stixPattern: String): String {
        return Regex("'([^']*)'").find(stixPattern)?.groupValues?.get(1) ?: ""
    }
}
