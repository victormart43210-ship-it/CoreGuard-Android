package com.coldboar.coreguard.quilla

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches public Indicators of Compromise (IOCs) from Amnesty International /
 * MVT open repositories for Quilla Research.
 *
 * Network I/O is synchronous; callers must invoke fetch helpers on a
 * background thread.
 *
 * Feeds:
 * - Amnesty Tech Android campaign STIX2 (AmnestyTech/investigations)
 * - MVT Pegasus STIX2 (mvt-project/mvt-indicators)
 *
 * These pulls feed Quilla Research / correlation only. They do **not** write
 * into Nemesis [com.coldboar.coreguard.mvt.IocRepository] (Premium Scanner
 * refresh uses [com.coldboar.coreguard.mvt.IocFeedFetcher]).
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
     * Official MVT / Amnesty-linked Pegasus indicators (STIX2 JSON).
     * Same public URL Premium Nemesis refresh uses — Quilla keeps a read-only copy.
     */
    const val MVT_PEGASUS_FEED_URL =
        "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators/pegasus.stix2"

    /**
     * Downloads and parses [AmnestyIndicator] records from [FEED_URL].
     *
     * Returns an empty list (without throwing) on any network or parse failure.
     */
    fun fetchAmnestyIndicators(): List<AmnestyIndicator> =
        fetchStixUrl(FEED_URL) ?: emptyList()

    /**
     * Pulls Amnesty campaign + MVT Pegasus public STIX feeds for Quilla Research.
     *
     * Throws [IOException] only when **both** remote feeds fail, so callers can
     * mark [QuillaResearchSnapshot.syncFailed] honestly while still merging
     * on-device MVT inventory separately.
     */
    fun fetchPublicResearchIndicators(): List<AmnestyIndicator> {
        val amnesty = fetchStixUrl(FEED_URL)
        val mvt = fetchStixUrl(MVT_PEGASUS_FEED_URL)
        if (amnesty == null && mvt == null) {
            throw IOException("Amnesty and MVT research feeds both failed")
        }
        return QuillaIocBridge.mergeUnique(amnesty.orEmpty(), mvt.orEmpty())
    }

    /**
     * @return parsed indicators, or null when the HTTP/parse path hard-failed.
     * An empty successful bundle returns an empty list (not null).
     */
    private fun fetchStixUrl(url: String): List<AmnestyIndicator>? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) return null

            parseStixBundle(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            System.err.println("AmnestyThreatIntelFetcher: fetch failed ($url): ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses a STIX2 bundle JSON string and returns the extracted [AmnestyIndicator] list.
     * Exposed as internal to allow unit-testing the parser without a network call.
     */
    internal fun parseStixBundle(json: String): List<AmnestyIndicator> {
        val indicators = mutableListOf<AmnestyIndicator>()
        val stixBundle = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return indicators
        if (stixBundle.optString("type") != "bundle") return indicators

        val objects = stixBundle.optJSONArray("objects") ?: org.json.JSONArray()
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
                pattern.contains("file:name") || pattern.contains("file:path") -> "PATH"
                pattern.contains("process:name") -> "PROCESS"
                pattern.contains("app:id") -> "PACKAGE"
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
