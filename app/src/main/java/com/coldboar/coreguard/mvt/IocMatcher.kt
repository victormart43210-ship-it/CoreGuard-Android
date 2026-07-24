package com.coldboar.coreguard.mvt

/**
 * Matches observed on-device artifacts against a set of [Indicator]s.
 *
 * Matching semantics follow MVT's conventions:
 *  - **Domains** match exactly or as a parent of an observed subdomain
 *    (`evil.com` matches `c2.evil.com`), and are also found inside URLs.
 *  - **Processes / packages / filenames** match case-insensitively; a process
 *    or filename also matches on its basename.
 *  - **File paths** match exactly or as a suffix of an observed absolute path.
 *
 * The matcher pre-indexes indicators by type so a scan of thousands of
 * artifacts stays fast.
 */
class IocMatcher(indicators: Collection<Indicator>) {

    private val byType: Map<IndicatorType, List<Indicator>> =
        indicators.groupBy { it.type }

    val size: Int = indicators.size

    /** Returns the matching indicator for an installed package id, or null. */
    fun matchPackage(packageId: String): Indicator? {
        val value = packageId.trim().lowercase()
        return byType[IndicatorType.PACKAGE]?.firstOrNull { it.value == value }
    }

    /** Returns the matching indicator for a process/thread name, or null. */
    fun matchProcess(processName: String): Indicator? {
        val value = processName.trim().lowercase()
        if (value.isEmpty()) return null
        val base = value.substringAfterLast('/')
        return byType[IndicatorType.PROCESS]?.firstOrNull {
            it.value == value || it.value == base
        }
    }

    /** Returns the matching indicator for a file path, or null. */
    fun matchFilePath(path: String): Indicator? {
        val value = path.trim().lowercase()
        if (value.isEmpty()) return null
        val base = value.substringAfterLast('/')
        byType[IndicatorType.FILEPATH]?.firstOrNull {
            value == it.value || value.endsWith(it.value)
        }?.let { return it }
        return byType[IndicatorType.FILENAME]?.firstOrNull { it.value == base }
    }

    /**
     * Returns the matching indicator for a domain (or a domain seen inside a
     * URL). A parent domain matches all of its subdomains.
     */
    fun matchDomain(domain: String): Indicator? {
        val host = normaliseHost(domain) ?: return null
        return byType[IndicatorType.DOMAIN]?.firstOrNull { ioc ->
            host == ioc.value || host.endsWith("." + ioc.value)
        }
    }

    /** Matches a full URL against DOMAIN and URL indicators. */
    fun matchUrl(url: String): Indicator? {
        val value = url.trim().lowercase()
        if (value.isEmpty()) return null
        byType[IndicatorType.URL]?.firstOrNull { value == it.value || value.startsWith(it.value) }
            ?.let { return it }
        return matchDomain(value)
    }

    /**
     * Extracts a hostname from a bare domain or a URL/authority string so the
     * same routine can be used for connection destinations and links.
     */
    private fun normaliseHost(input: String): String? {
        var host = input.trim().lowercase()
        if (host.isEmpty()) return null
        // Strip scheme.
        val schemeIdx = host.indexOf("://")
        if (schemeIdx >= 0) host = host.substring(schemeIdx + 3)
        // Strip userinfo.
        host = host.substringAfterLast('@')
        // Strip path / query.
        host = host.substringBefore('/').substringBefore('?')
        // Strip port.
        host = host.substringBefore(':')
        return host.ifEmpty { null }
    }
}
