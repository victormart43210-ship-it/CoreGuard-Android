package com.coldboar.coreguard.quilla

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches public Indicators of Compromise (IOCs) from Amnesty International's
 * Security Lab open repositories.
 *
 * Network I/O is synchronous; callers must invoke [fetchAmnestyIndicators] on a
 * background thread.
 *
 * The feed is the official Amnesty Tech Android campaign STIX2 bundle:
 * https://github.com/AmnestyTech/investigations
 */
object AmnestyThreatIntelFetcher {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB sanity cap

    /**
     * Official Amnesty Tech Android-campaign STIX2 indicator bundle.
     */
    const val FEED_URL =
        "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2023-03-29_android_campaign/malware.stix2"

    /**
     * Downloads and parses [AmnestyIndicator] records from [FEED_URL].
     *
     * Returns an empty list (without throwing) on any network or parse failure.
     */
    fun fetchAmnestyIndicators(): List<AmnestyIndicator> {
        val indicators = mutableListOf<AmnestyIndicator>()
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return indicators

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) return indicators

            indicators += parseStixBundle(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            System.err.println("AmnestyThreatIntelFetcher: fetch failed: ${e.message}")
        } finally {
            connection.disconnect()
        }
        return indicators
    }

    /**
     * Parses a STIX2 bundle JSON string and returns the extracted [AmnestyIndicator] list.
     * Exposed as internal to allow unit-testing the parser without a network call.
     */
    internal fun parseStixBundle(json: String): List<AmnestyIndicator> {
        val indicators = mutableListOf<AmnestyIndicator>()
        val stixBundle = runCatching { JSONObject(json) }.getOrNull() ?: return indicators
        if (stixBundle.optString("type") != "bundle") return indicators

        val objects = stixBundle.optJSONArray("objects") ?: JSONArray()
        for (i in 0 until objects.length()) {
            val obj = objects.optJSONObject(i) ?: continue
            if (obj.optString("type") != "indicator") continue

            val pattern = obj.optString("pattern")
            val id = obj.optString("id")
            val desc = obj.optString("description", "Amnesty International IOC")

            val extractedValue = extractPatternValue(pattern)
            if (extractedValue.isBlank()) continue

            val type = when {
                pattern.contains("domain-name") -> "DOMAIN"
                pattern.contains("ipv4-addr") -> "IP"
                pattern.contains("file:hashes") -> "HASH"
                else -> "GENERIC"
            }

            indicators += AmnestyIndicator(id, type, extractedValue, desc)
        }
        return indicators
    }

    /** Extracts the first single-quoted value from a STIX2 pattern string. */
    private fun extractPatternValue(stixPattern: String): String {
        val match = Regex("'([^']*)'").find(stixPattern)
        return match?.groupValues?.get(1) ?: ""
    }
}
