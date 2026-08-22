package com.coldboar.coreguard.knowledge

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Imports defensive Viper threat-intelligence records into [CyberKnowledgeBase.Entry].
 *
 * Viper intelligence is educational correlation context only — never automatic proof
 * of infection/compromise.
 */
object ViperThreatIntelImporter {

    data class ImportResult(
        val entries: List<CyberKnowledgeBase.Entry>,
        val acceptedCount: Int,
        val rejectedCount: Int
    )

    fun importPayload(payload: String): ImportResult {
        if (payload.isBlank()) return ImportResult(emptyList(), acceptedCount = 0, rejectedCount = 0)
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: return ImportResult(emptyList(), acceptedCount = 0, rejectedCount = 1)
        val records = root.optJSONArray("records") ?: JSONArray()
        return mapRecords(records)
    }

    private fun mapRecords(records: JSONArray): ImportResult {
        val out = mutableListOf<CyberKnowledgeBase.Entry>()
        var rejected = 0
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i)
            if (record == null) {
                rejected += 1
                continue
            }
            val mapped = record.toEntryOrNull(i)
            if (mapped == null) {
                rejected += 1
                continue
            }
            out += mapped
        }
        return ImportResult(entries = out, acceptedCount = out.size, rejectedCount = rejected)
    }

    private fun JSONObject.toEntryOrNull(index: Int): CyberKnowledgeBase.Entry? {
        val rawId = sanitizeText(optString("id"), 80)
        val indicator = sanitizeText(optString("indicator"), 120)
        val title = sanitizeText(optString("title"), 160).ifBlank { indicator }
        val summary = sanitizeText(optString("summary"), 280)
        val description = sanitizeText(optString("description"), 1500)
        if (title.isBlank() || summary.isBlank()) return null

        val severity = normalizeSeverity(sanitizeText(optString("severity"), 24))
        val confidence = normalizeConfidence(opt("confidence"))

        val tags = buildSet {
            add("viper")
            add("threat-intel")
            add("knowledge-only")
            add("no-proof")
            if (severity.isNotBlank()) add(severity.lowercase(Locale.US))
            val incomingTags = optJSONArray("tags") ?: JSONArray()
            for (i in 0 until incomingTags.length()) {
                sanitizeToken(incomingTags.optString(i))?.let { add(it) }
            }
            sanitizeToken(indicator)?.let { add(it) }
        }

        val references = buildList {
            val refs = optJSONArray("references") ?: JSONArray()
            for (i in 0 until refs.length()) {
                val ref = sanitizeText(refs.optString(i), 400)
                if (ref.startsWith("https://")) add(ref)
            }
        }.distinct().take(6)

        val entryId = buildEntryId(rawId, indicator, index)
        return CyberKnowledgeBase.Entry(
            id = entryId,
            title = title,
            category = "viper-threat-intel",
            tags = tags,
            summary = summary,
            body = buildString {
                append(description.ifBlank { "No Viper description provided." })
                append(" Confidence capped at ")
                append("%.2f".format(Locale.US, confidence))
                append(" for knowledge-only handling.")
                append(" Severity normalized to ")
                append(severity)
                append(".")
            },
            defense = "Use this Viper intel as a correlation clue only. It is not automatic proof of infection or compromise. " +
                "Validate with on-device evidence (Nemesis scan, app/process checks, and timeline context) before escalation.",
            references = references
        )
    }

    private fun normalizeSeverity(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
        "critical", "high" -> "MEDIUM"
        "medium", "moderate" -> "MEDIUM"
        "low", "info", "informational" -> "LOW"
        else -> "MEDIUM"
    }

    private fun normalizeConfidence(raw: Any?): Double {
        val parsed = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        } ?: 0.5
        return parsed.coerceIn(0.0, 0.6)
    }

    private fun buildEntryId(rawId: String, indicator: String, index: Int): String {
        if (rawId.isNotBlank()) {
            val cleaned = rawId.lowercase(Locale.US).replace(Regex("[^a-z0-9-]+"), "-").trim('-')
            if (cleaned.isNotBlank()) return "viper-$cleaned"
        }
        val fromIndicator = indicator.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (fromIndicator.isNotBlank()) "viper-$fromIndicator" else "viper-record-$index"
    }

    private fun sanitizeText(input: String, maxLen: Int): String {
        val noControls = input.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        val noMarkup = noControls.replace(Regex("<[^>]+>"), " ")
        return noMarkup.replace(Regex("\\s+"), " ").trim().take(maxLen)
    }

    private fun sanitizeToken(input: String): String? {
        val normalized = sanitizeText(input, 60)
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
        return normalized.takeIf { it.length >= 2 }
    }

}
