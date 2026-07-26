package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls public web security intelligence (CISA KEV + MISP Android galaxy) and
 * converts it into defensive [CyberKnowledgeBase.Entry] records for Quilla.
 *
 * Framing is always defensive / educational. Unauthorized offensive how-to is
 * rejected by [com.coldboar.coreguard.quilla.knowledge.QuillaEthicsGuard].
 *
 * Network I/O is synchronous — call from a background thread.
 */
object QuillaWebSecurityIntelFetcher {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_KEV_ENTRIES = 40
    private const val MAX_GALAXY_ENTRIES = 60
    private const val USER_AGENT =
        "CoreGuard-QuillaIntel/1.0 (defensive research; +https://github.com/victormart43210-ship-it/CoreGuard-Android)"

    const val CISA_KEV_URL =
        "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"

    const val MISP_ANDROID_GALAXY_URL =
        "https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json"

    data class WebIntelResult(
        val entries: List<CyberKnowledgeBase.Entry>,
        val sourcesOk: List<String>,
        val sourcesFailed: List<String>
    )

    fun fetchDefensiveKnowledge(): WebIntelResult {
        val entries = mutableListOf<CyberKnowledgeBase.Entry>()
        val ok = mutableListOf<String>()
        val failed = mutableListOf<String>()

        when (val kev = runCatching { fetchKevAndroidEntries() }.getOrNull()) {
            null -> failed += "CISA KEV"
            else -> {
                entries += kev
                ok += "CISA KEV (${kev.size} Android-relevant)"
            }
        }

        when (val galaxy = runCatching { fetchMispAndroidGalaxyEntries() }.getOrNull()) {
            null -> failed += "MISP Android galaxy"
            else -> {
                entries += galaxy
                ok += "MISP Android galaxy (${galaxy.size})"
            }
        }

        return WebIntelResult(entries = entries, sourcesOk = ok, sourcesFailed = failed)
    }

    /** Parses CISA KEV JSON into defensive knowledge entries (Android/mobile filter). */
    internal fun parseKevAndroidEntries(json: String): List<CyberKnowledgeBase.Entry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val vulns = root.optJSONArray("vulnerabilities") ?: return emptyList()
        val out = mutableListOf<CyberKnowledgeBase.Entry>()
        for (i in 0 until vulns.length()) {
            if (out.size >= MAX_KEV_ENTRIES) break
            val v = vulns.optJSONObject(i) ?: continue
            val name = v.optString("vulnerabilityName")
            val vendor = v.optString("vendorProject")
            val product = v.optString("product")
            val desc = v.optString("shortDescription")
            val cve = v.optString("cveID")
            // Prefer vendor/product/name — descriptions can say "not mobile" and false-positive.
            val blob = "$name $vendor $product".lowercase()
            if (!isMobileRelevant(blob)) continue
            val id = "kev-${cve.lowercase().ifBlank { "row-$i" }}"
            out += CyberKnowledgeBase.Entry(
                id = id,
                title = if (cve.isNotBlank()) "$cve — $name" else name.ifBlank { "Known exploited vulnerability" },
                category = "web-intel-kev",
                tags = setOf(
                    "cisa", "kev", "vulnerability", "patch", "exploit",
                    "android", "mobile", cve.lowercase(), vendor.lowercase(), product.lowercase()
                ).filter { it.isNotBlank() }.toSet(),
                summary = "CISA Known Exploited Vulnerability (actively abused in the wild).",
                body = buildString {
                    append(desc.ifBlank { "No short description provided." })
                    append(" Vendor/product: ")
                    append(vendor.ifBlank { "?" })
                    append(" / ")
                    append(product.ifBlank { "?" })
                    append(". Date added: ")
                    append(v.optString("dateAdded").ifBlank { "unknown" })
                    append(". Required action: ")
                    append(v.optString("requiredAction").ifBlank { "Apply vendor patches promptly." })
                },
                defense = "Patch or mitigate immediately. On Android devices: install OS/security updates, " +
                    "remove abandoned apps, re-run Nemesis after updates, and keep Privacy Shield on untrusted networks. " +
                    "Quilla will not help weaponize CVEs.",
                references = listOf(
                    "https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
                    if (cve.isNotBlank()) "https://nvd.nist.gov/vuln/detail/$cve" else ""
                ).filter { it.isNotBlank() }
            )
        }
        return out
    }

    /** Parses MISP Android malware galaxy into defensive family briefs. */
    internal fun parseMispAndroidGalaxyEntries(json: String): List<CyberKnowledgeBase.Entry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val values = root.optJSONArray("values") ?: return emptyList()
        val out = mutableListOf<CyberKnowledgeBase.Entry>()
        for (i in 0 until values.length()) {
            if (out.size >= MAX_GALAXY_ENTRIES) break
            val obj = values.optJSONObject(i) ?: continue
            val name = obj.optString("value").trim()
            if (name.isBlank()) continue
            val description = obj.optString("description").trim()
            if (description.isBlank()) continue
            val meta = obj.optJSONObject("meta")
            val synonyms = meta?.optJSONArray("synonyms") ?: JSONArray()
            val synTags = buildSet {
                for (s in 0 until synonyms.length()) {
                    val syn = synonyms.optString(s).trim().lowercase()
                    if (syn.isNotEmpty()) add(syn)
                }
            }
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            out += CyberKnowledgeBase.Entry(
                id = "misp-android-$slug",
                title = "Android malware family: $name",
                category = "web-intel-malware",
                tags = setOf(
                    "android", "malware", "misp", "family", "spyware", "trojan", name.lowercase()
                ) + synTags,
                summary = "Open-source MISP Android galaxy brief for defenders.",
                body = description.take(1_200),
                defense = "Treat family IOCs as detection clues, not proof of infection alone. " +
                    "Run Nemesis, review suspicious apps/permissions, enable Privacy Shield, " +
                    "and update the OS. Quilla teaches defense — not how to deploy malware.",
                references = listOf(
                    "https://www.misp-galaxy.org/android",
                    "https://github.com/MISP/misp-galaxy"
                )
            )
        }
        return out
    }

    private fun fetchKevAndroidEntries(): List<CyberKnowledgeBase.Entry> {
        val body = httpGet(CISA_KEV_URL) ?: throw IllegalStateException("KEV fetch failed")
        val parsed = parseKevAndroidEntries(body)
        if (parsed.isEmpty()) throw IllegalStateException("KEV parse empty")
        return parsed
    }

    private fun fetchMispAndroidGalaxyEntries(): List<CyberKnowledgeBase.Entry> {
        val body = httpGet(MISP_ANDROID_GALAXY_URL) ?: throw IllegalStateException("Galaxy fetch failed")
        val parsed = parseMispAndroidGalaxyEntries(body)
        if (parsed.isEmpty()) throw IllegalStateException("Galaxy parse empty")
        return parsed
    }

    private fun httpGet(url: String): String? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) return null
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun isMobileRelevant(blob: String): Boolean {
        // Word-ish tokens to avoid false positives (e.g. "arm" inside "firmware").
        val pattern = Regex(
            """(?i)(?<![a-z0-9])(android|chromium|chrome|webkit|samsung|qualcomm|mediatek|pixel|mobile|iphone|ipad|ios|webview|bluetooth|wifi|modem|baseband|aosp)(?![a-z0-9])"""
        )
        return pattern.containsMatchIn(blob)
    }
}
