package com.coldboar.coreguard.mvt

/**
 * Categories of Indicator of Compromise (IOC), mirroring the indicator types
 * used by Amnesty International's Mobile Verification Toolkit (MVT).
 *
 * See: https://github.com/mvt-project/mvt
 */
enum class IndicatorType(val stixTypes: List<String>) {
    /** A malicious network domain (matches the domain and its subdomains). */
    DOMAIN(listOf("domain-name:value", "domain")),

    /** A full malicious URL. */
    URL(listOf("url:value", "url")),

    /** A process / thread name observed for the spyware. */
    PROCESS(listOf("process:name", "process")),

    /** An Android application package id (e.g. com.evil.spy). */
    PACKAGE(listOf("app:id", "package", "package_name")),

    /** A known malicious file name (basename). */
    FILENAME(listOf("file:name", "filename")),

    /** A known malicious absolute file path. */
    FILEPATH(listOf("file:path", "filepath")),

    /** An attacker email address. */
    EMAIL(listOf("email-addr:value", "email")),

    /** A SHA-256 hash of a malicious artifact. */
    SHA256(listOf("file:hashes.'SHA-256'", "sha256"));

    companion object {
        /** Resolves a free-form type string (STIX or short form) to a type. */
        fun fromString(raw: String): IndicatorType? {
            val key = raw.trim().lowercase()
            return entries.firstOrNull { type ->
                type.name.lowercase() == key ||
                    type.stixTypes.any { it.lowercase() == key } ||
                    // Tolerate common plural short forms used in MVT indicator files.
                    key.removeSuffix("s") == type.name.lowercase() ||
                    key == when (type) {
                        DOMAIN -> "domains"
                        URL -> "urls"
                        PROCESS -> "processes"
                        PACKAGE -> "package_names"
                        FILENAME -> "files_names"
                        FILEPATH -> "files_paths"
                        EMAIL -> "emails"
                        SHA256 -> "sha256"
                    }
            }
        }
    }
}

/**
 * A single Indicator of Compromise.
 *
 * @param type    The indicator category.
 * @param value   The normalised (lower-cased, trimmed) indicator value.
 * @param malware Human-readable malware / campaign name (e.g. "Pegasus").
 * @param reference Optional public reference (report URL) for the indicator.
 */
data class Indicator(
    val type: IndicatorType,
    val value: String,
    val malware: String,
    val reference: String? = null
) {
    companion object {
        fun of(type: IndicatorType, rawValue: String, malware: String, reference: String? = null): Indicator? {
            val normalised = rawValue.trim().lowercase()
            if (normalised.isEmpty()) return null
            return Indicator(type, normalised, malware.ifBlank { "Unknown" }, reference)
        }
    }
}

/** A named collection of indicators loaded from one source (e.g. an MVT STIX2 file). */
data class IndicatorBundle(
    val name: String,
    val source: String,
    val indicators: List<Indicator>
)
