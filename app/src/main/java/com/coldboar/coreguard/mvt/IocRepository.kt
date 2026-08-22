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
 *  2. Digest-verified remote feed (`remote_feed.json` + meta sidecar)
 *  3. Other user-imported JSON under filesDir/ioc (not cryptographically verified)
 *  4. [DefaultIndicators] as last-resort fallback
 *
 * Provenance is tracked so scan sessions never inherit a remote-verified label
 * for bundled, imported, fallback, mixed, or unavailable data.
 */
object IocRepository {

    private const val TAG = "IocRepository"
    private const val ASSET_DIR = "ioc"
    private const val USER_DIR = "ioc"
    const val REMOTE_FEED_FILE = "remote_feed.json"
    const val REMOTE_FEED_META_FILE = "remote_feed.meta.json"

    @Volatile
    private var cached: List<Indicator>? = null

    @Volatile
    private var cachedProvenance: IocProvenanceSnapshot = IocProvenanceSnapshot.unavailable()

    @Volatile
    private var loadedAtMs: Long = 0L

    fun loadedAtMs(): Long = loadedAtMs

    /** Last computed provenance for the cached IOC set (or UNAVAILABLE). */
    fun provenance(): IocProvenanceSnapshot = cachedProvenance

    fun indicators(context: Context): List<Indicator> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = load(context)
            cached = loaded.indicators
            cachedProvenance = loaded.provenance
            loadedAtMs = System.currentTimeMillis()
            return loaded.indicators
        }
    }

    fun matcher(context: Context): IocMatcher = IocMatcher(indicators(context))

    /**
     * Returns provenance for the active set, loading if needed.
     * When the cache has never been populated, returns UNAVAILABLE without claiming authenticity.
     */
    fun provenance(context: Context): IocProvenanceSnapshot {
        if (cached == null && loadedAtMs == 0L) {
            // Force load so scan paths that read provenance after indicators() stay consistent.
            indicators(context)
        }
        return cachedProvenance
    }

    /**
     * Session-safe capture: if nothing has been loaded yet, return UNAVAILABLE
     * (do not invent VERIFIED_REMOTE authenticity).
     */
    fun provenanceForSession(context: Context?, forceLoad: Boolean): IocProvenanceSnapshot {
        if (!forceLoad && loadedAtMs <= 0L) {
            return IocProvenanceSnapshot.unavailable()
        }
        return if (context != null) provenance(context) else cachedProvenance
    }

    fun invalidate() {
        cached = null
        loadedAtMs = 0L
        cachedProvenance = IocProvenanceSnapshot.unavailable()
    }

    private data class Loaded(
        val indicators: List<Indicator>,
        val provenance: IocProvenanceSnapshot
    )

    private fun load(context: Context): Loaded {
        val bundled = LinkedHashSet<Indicator>()
        val verifiedRemote = LinkedHashSet<Indicator>()
        val userImported = LinkedHashSet<Indicator>()
        var verifiedMeta: VerifiedRemoteMeta? = null

        runCatching {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".json") }?.forEach { name ->
                val json = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                bundled += IocParser.parse(json)
            }
        }.onFailure { Log.w(TAG, "Failed reading bundled IOC assets: ${it.message}") }

        runCatching {
            val dir = File(context.filesDir, USER_DIR)
            if (dir.isDirectory) {
                val remoteFile = File(dir, REMOTE_FEED_FILE)
                val metaFile = File(dir, REMOTE_FEED_META_FILE)
                if (remoteFile.isFile) {
                    val body = remoteFile.readText()
                    val meta = readVerifiedMeta(metaFile)
                    val bodySha = HardenedSha.sha256Hex(body.toByteArray(Charsets.UTF_8))
                    if (meta != null && meta.sha256Hex.equals(bodySha, ignoreCase = true)) {
                        verifiedRemote += IocParser.parse(body)
                        verifiedMeta = meta
                    } else {
                        // File present without matching meta ⇒ treat as user import, not verified.
                        userImported += IocParser.parse(body)
                        Log.w(TAG, "remote_feed.json present without matching verified meta")
                    }
                }
                dir.listFiles { f ->
                    f.isFile &&
                        f.extension.equals("json", ignoreCase = true) &&
                        f.name != REMOTE_FEED_FILE &&
                        f.name != REMOTE_FEED_META_FILE
                }?.forEach { file ->
                    userImported += IocParser.parse(file.readText())
                }
            }
        }.onFailure { Log.w(TAG, "Failed reading user IOC feeds: ${it.message}") }

        val merged = LinkedHashSet<Indicator>()
        merged += bundled
        merged += verifiedRemote
        merged += userImported
        val usedFallback = merged.isEmpty()
        if (usedFallback) merged += DefaultIndicators.list

        val now = System.currentTimeMillis()
        val provenance = IocProvenanceResolver.resolve(
            bundledCount = bundled.size,
            verifiedRemoteCount = verifiedRemote.size,
            userImportedCount = userImported.size,
            usedFallback = usedFallback,
            loadedAtMs = now,
            verifiedMeta = verifiedMeta
        )
        Log.i(TAG, "Loaded ${merged.size} indicators (${provenance.provenanceClass})")
        return Loaded(merged.toList(), provenance)
    }

    private fun readVerifiedMeta(file: File): VerifiedRemoteMeta? {
        if (!file.isFile) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            VerifiedRemoteMeta(
                name = obj.getString("name"),
                url = obj.getString("url"),
                sha256Hex = obj.getString("sha256").lowercase(),
                commitPin = obj.optString("commit", ""),
                verifiedAtMs = obj.optLong("verifiedAtMs", 0L)
            )
        }.getOrNull()
    }
}

/** Tiny SHA-256 helper to avoid a circular import with the net package from load path. */
internal object HardenedSha {
    fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
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

    private fun parseStixPattern(pattern: String): List<Pair<IndicatorType, String>> {
        if (pattern.isBlank()) return emptyList()
        val result = mutableListOf<Pair<IndicatorType, String>>()
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
