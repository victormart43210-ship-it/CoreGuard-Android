package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Loads and caches the active Indicator of Compromise (IOC) set.
 *
 * Sources, merged in order:
 *  1. Bundled indicators shipped as JSON under the `ioc` assets folder
 *     (CoreGuard or STIX2 format).
 *  2. User-imported JSON feeds placed under the `ioc` folder in filesDir
 *     (e.g. a fresh MVT `mvt-indicators` STIX2 export the user imports).
 *  3. [DefaultIndicators] as a last-resort fallback so the scanner is never empty.
 *
 * Duplicate indicators (same type + value) are de-duplicated.
 */
object IocRepository {

    private const val TAG = "IocRepository"
    private const val ASSET_DIR = "ioc"
    private const val USER_DIR = "ioc"

    @Volatile
    private var cached: List<Indicator>? = null

    /** Returns the merged indicator set, loading and caching on first use. */
    fun indicators(context: Context): List<Indicator> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val merged = load(context)
            cached = merged
            return merged
        }
    }

    /** Builds an [IocMatcher] over the active indicator set. */
    fun matcher(context: Context): IocMatcher = IocMatcher(indicators(context))

    /** Forces a reload on next access (e.g. after importing a new feed). */
    fun invalidate() {
        cached = null
    }

    private fun load(context: Context): List<Indicator> {
        val out = LinkedHashSet<Indicator>()

        // 1. Bundled assets.
        runCatching {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".json") }?.forEach { name ->
                val json = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                out += IocParser.parse(json)
            }
        }.onFailure { Log.w(TAG, "Failed reading bundled IOC assets: ${it.message}") }

        // 2. User-imported feeds.
        runCatching {
            val dir = File(context.filesDir, USER_DIR)
            if (dir.isDirectory) {
                dir.listFiles { f -> f.extension.equals("json", ignoreCase = true) }?.forEach { file ->
                    out += IocParser.parse(file.readText())
                }
            }
        }.onFailure { Log.w(TAG, "Failed reading user IOC feeds: ${it.message}") }

        // 3. Fallback.
        if (out.isEmpty()) out += DefaultIndicators.list

        Log.i(TAG, "Loaded ${out.size} indicators")
        return out.toList()
    }
}

/**
 * Parses IOC JSON in two accepted shapes:
 *  - CoreGuard: an object with an "indicators" array of
 *    type / value / malware / reference records.
 *  - STIX2: a bundle whose "objects" array contains "indicator" objects with a
 *    "pattern" (e.g. a domain-name comparison) and a "name".
 */
object IocParser {

    fun parse(json: String): List<Indicator> = buildList {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return@buildList
        parseCoreGuard(root, this)
        parseStix2(root, this)
    }

    private fun parseCoreGuard(root: JSONObject, out: MutableList<Indicator>) {
        val arr = root.optJSONArray("indicators") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val type = IndicatorType.fromString(obj.optString("type")) ?: continue
            val value = obj.optString("value")
            val malware = obj.optString("malware", obj.optString("name", "Unknown"))
            val reference = obj.optString("reference").ifBlank { null }
            Indicator.of(type, value, malware, reference)?.let(out::add)
        }
    }

    private fun parseStix2(root: JSONObject, out: MutableList<Indicator>) {
        val objects = root.optJSONArray("objects") ?: return
        for (i in 0 until objects.length()) {
            val obj = objects.optJSONObject(i) ?: continue
            if (obj.optString("type") != "indicator") continue
            val pattern = obj.optString("pattern")
            val name = obj.optString("name", "Unknown")
            parseStixPattern(pattern).forEach { (type, value) ->
                Indicator.of(type, value, name)?.let(out::add)
            }
        }
    }

    /**
     * Extracts (type, value) pairs from a simple STIX2 pattern such as
     * `[domain-name:value = 'evil.com']` or comparisons joined by OR.
     */
    private fun parseStixPattern(pattern: String): List<Pair<IndicatorType, String>> {
        if (pattern.isBlank()) return emptyList()
        val result = mutableListOf<Pair<IndicatorType, String>>()
        // Match  <lhs> = '<value>'  clauses.
        val regex = Regex("""([\w\-:.'\[\]]+?)\s*=\s*'([^']+)'""")
        regex.findAll(pattern).forEach { m ->
            val lhs = m.groupValues[1].trim().trim('[', ']')
            val value = m.groupValues[2]
            val type = IndicatorType.fromString(lhs)
            if (type != null) result += type to value
        }
        return result
    }
}
