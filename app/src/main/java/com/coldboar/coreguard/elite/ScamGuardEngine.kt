package com.coldboar.coreguard.elite

import android.content.Context
import com.coldboar.coreguard.mvt.IocRepository
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device **Scam Guard** — smishing / phishing URL heuristics.
 *
 * Parses notification text locally, extracts URLs, scores against IOC feeds and
 * zero-trust heuristics (IP literals, homoglyph-ish banks, suspicious TLDs,
 * credential-bait keywords). No cloud LLM. No reading SMS inbox without OS
 * notification access granted by the user.
 */
object ScamGuardEngine {

    data class Finding(
        val url: String,
        val host: String,
        val score: Int,
        val reasons: List<String>,
        val source: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val latest = AtomicReference<Finding?>(null)
    private val recent = CopyOnWriteArrayList<Finding>()

    fun latestFinding(): Finding? = latest.get()
    fun recentFindings(): List<Finding> = recent.toList().asReversed()

    fun clear() {
        latest.set(null)
        recent.clear()
    }

    fun inspectNotificationText(context: Context, text: String, source: String = "notification"): Finding? {
        val urls = extractUrls(text)
        if (urls.isEmpty()) return null
        var best: Finding? = null
        for (url in urls) {
            val f = scoreUrl(context, url, source) ?: continue
            if (best == null || f.score > best.score) best = f
        }
        best?.let { publish(context, it) }
        return best
    }

    fun scoreUrl(context: Context, rawUrl: String, source: String = "manual"): Finding? {
        val normalized = normalizeUrl(rawUrl) ?: return null
        val host = hostOf(normalized) ?: return null
        val reasons = mutableListOf<String>()
        var score = 0

        // IOC hit (Amnesty/MVT style domain list when loaded).
        val iocHit = runCatching {
            val matcher = IocRepository.matcher(context)
            matcher.matchUrl(normalized) != null || matcher.matchDomain(host) != null
        }.getOrDefault(false)
        if (iocHit) {
            score += 70
            reasons += "Matched on-device threat intel IOC"
        }

        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            score += 40
            reasons += "URL uses raw IP address"
        }
        if (host.count { it == '.' } >= 3 && host.length > 28) {
            score += 15
            reasons += "Unusually deep subdomain chain"
        }
        val bait = listOf(
            "verify-account", "secure-login", "wallet-connect", "update-billing",
            "otp", "password-reset", "crypto-airdrop", "bank-secure"
        )
        val lower = normalized.lowercase()
        if (bait.any { lower.contains(it) }) {
            score += 25
            reasons += "Credential-bait path keywords"
        }
        val riskyTld = listOf(".zip", ".mov", ".top", ".xyz", ".tk", ".gq", ".ml", ".cf")
        if (riskyTld.any { host.endsWith(it) }) {
            score += 20
            reasons += "High-risk TLD often used in smishing"
        }
        val spoofBanks = listOf("paypa1", "app1e", "micros0ft", "chase-secure", "wellsfargo-login")
        if (spoofBanks.any { host.contains(it) }) {
            score += 45
            reasons += "Possible brand spoof host"
        }

        score = score.coerceIn(0, 100)
        if (score < 25 && reasons.isEmpty()) return null
        return Finding(
            url = normalized,
            host = host,
            score = score,
            reasons = reasons.ifEmpty { listOf("Heuristic watch") },
            source = source
        )
    }

    private fun publish(context: Context, finding: Finding) {
        latest.set(finding)
        recent.add(finding)
        while (recent.size > 40) recent.removeAt(0)
        if (finding.score >= 50) {
            runCatching {
                ForensicJournal.append(
                    context,
                    ForensicJournal.EventKind.SCAM_URL,
                    packageName = finding.source,
                    details = "Scam Guard hit ${finding.host} score=${finding.score}",
                    metadata = mapOf(
                        "url" to finding.url,
                        "score" to finding.score.toString(),
                        "reasons" to finding.reasons.joinToString(";")
                    )
                )
            }
        }
    }

    fun extractUrls(text: String): List<String> {
        val regex = Regex("""https?://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
    }

    private fun normalizeUrl(raw: String): String? {
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            "https://$raw"
        }
        return runCatching { URI(withScheme).toString() }.getOrNull()
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()
}
