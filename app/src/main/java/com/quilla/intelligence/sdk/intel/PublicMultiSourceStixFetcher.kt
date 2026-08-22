package com.quilla.intelligence.sdk.intel

import com.quilla.intelligence.sdk.model.StixIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
        // The production correlator accepts only the reviewed immutable feed set.
        // Arbitrary caller-provided URLs would bypass this trust boundary.
        if (feed.url !in expectedFeedHashes) return emptyList()
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = false
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()
            if (connection.contentLength > MAX_BYTES) return emptyList()
            val bytes = ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8_192)
                connection.inputStream.use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (output.size() + read > MAX_BYTES) return emptyList()
                        output.write(buffer, 0, read)
                    }
                }
                output.toByteArray()
            }
            if (sha256Hex(bytes) != expectedFeedHashes[feed.url]) return emptyList()
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

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_BYTES = 12 * 1024 * 1024
        /** No artificial IOC teaching ceiling — Infinity correlator ingest. */
        private const val MAX_INDICATORS_PER_FEED = Int.MAX_VALUE
        private const val USER_AGENT = "CoreGuard-QuillaIntel/1.0 (defensive research; +https://github.com/victormart43210-ship-it/CoreGuard-Android)"

        val DEFAULT_FEEDS: List<Feed> = listOf(
            Feed(
                "Amnesty Android campaign",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2023-03-29_android_campaign/malware.stix2"
            ),
            Feed(
                "Amnesty NoviSpy",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2024-12-16_serbia_novispy/novispy.stix2"
            ),
            Feed(
                "Amnesty Wintego Helios",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2024-05-02_wintego_helios/wintego_helios.stix2"
            ),
            Feed(
                "Amnesty Pegasus (NSO)",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2021-07-18_nso/pegasus.stix2"
            ),
            Feed(
                "Amnesty Cytrox / Predator",
                "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2021-12-16_cytrox/cytrox.stix2"
            ),
            Feed(
                "MVT WyrmSpy/DragonEgg",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/2023-07-25_wyrmspy_dragonegg/wyrmspy_dragonegg.stix2"
            ),
            Feed(
                "MVT EagleMsgSpy",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/2024-12-25_eaglemsgspy/eaglemsgspy.stix2"
            ),
            Feed(
                "MVT DarkSword",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/2026-03-30_darksword/darksword.stix2"
            ),
            Feed(
                "MVT Coruna / CryptoWaters",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/2026-03-03_coruna_cryptowaters/coruna.stix2"
            ),
            Feed(
                "MVT IPS Morpheus",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/2026-04-23_ips_morpheus/morpheus.stix2"
            ),
            Feed(
                "MVT ResidentBat",
                "https://raw.githubusercontent.com/mvt-project/mvt-indicators/162685398d842d8217ea8d6f69f9b565a0778d93/ResidentBat/residentbat.stix2"
            ),
            Feed(
                "Open stalkerware IOCs",
                "https://raw.githubusercontent.com/f00wl/stalkerware-indicators/426119d27e5597ec1b6976153bbe6d58ec0fc08e/generated/stalkerware.stix2"
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
