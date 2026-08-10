package com.coldboar.coreguard.quilla

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.PublicKey
import java.security.Signature

/**
 * Downloads and verifies the cryptographically signed Quilla curated intelligence
 * bundle produced by the off-device defensive crawler.
 *
 * Security contract:
 *  - Only fetches from a statically configured HTTPS endpoint.
 *  - Verifies Ed25519 signature before any JSON parsing.
 *  - Validates schema_version, entry_count, and entries_sha256.
 *  - Enforces a 2 MiB bundle size limit.
 *  - Applies all field and character limits from the spec.
 *  - Strips control characters; rejects blank id/title.
 *  - Requires HTTPS references on approved source domains.
 *  - Caches only the last verified-good bundle in app-private storage.
 *  - Uses atomic cache replacement; retains previous valid cache on failure.
 *  - Never uploads user or device information.
 *  - Never claims a matched entry proves device compromise.
 *
 * The Ed25519 public key is embedded in the app as a PEM resource.
 * The bundle URL is configured in res/values/strings.xml (HTTPS only).
 * Release builds fail closed when configuration is missing.
 */
object QuillaCuratedIntelFetcher {

    private const val TAG = "QuillaCuratedIntelFetcher"

    // Hard limits (mirrors the Python spec).
    private const val MAX_BUNDLE_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TITLE_CHARS = 240
    private const val MAX_SUMMARY_CHARS = 1_000
    private const val MAX_BODY_CHARS = 8_000
    private const val MAX_DEFENSE_CHARS = 4_000
    private const val MAX_TAGS = 64
    private const val MAX_REFS = 8
    private const val SUPPORTED_SCHEMA_VERSION = 1

    private const val CACHE_FILENAME = "quilla-intel-verified.json"
    private const val CACHE_SIG_FILENAME = "quilla-intel-verified.sig"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 45_000

    /** Approved reference domains (HTTPS only). */
    private val APPROVED_REF_DOMAINS = setOf(
        "www.cisa.gov",
        "nvd.nist.gov",
        "source.android.com",
        "raw.githubusercontent.com",
        "www.misp-galaxy.org",
        "malpedia.caad.fkie.fraunhofer.de",
    )

    data class FetchResult(
        val entries: List<CyberKnowledgeBase.Entry> = emptyList(),
        val acceptedCount: Int = 0,
        val rejectedCount: Int = 0,
        val signatureValid: Boolean = false,
        val sourceLabel: String = "Quilla Curated Intel",
        val warnings: List<String> = emptyList(),
        val failureReason: String = "",
    ) {
        val success: Boolean get() = signatureValid && entries.isNotEmpty()
    }

    /**
     * Download, verify, and import the curated bundle.
     * Must be called on a background dispatcher.
     *
     * @param context         Application context.
     * @param publicKeyPem    Ed25519 public key PEM bytes (embedded in app resources).
     * @param bundleUrl       HTTPS URL to quilla-intel.json (from app configuration).
     * @param sigUrl          HTTPS URL to quilla-intel.sig.
     */
    suspend fun fetchAndVerify(
        context: Context,
        publicKeyPem: ByteArray,
        bundleUrl: String,
        sigUrl: String,
    ): FetchResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()

        // Configuration safety check — fail closed in release builds.
        if (bundleUrl.isBlank() || sigUrl.isBlank()) {
            return@withContext FetchResult(
                failureReason = "Bundle URL or signature URL is not configured.",
                warnings = warnings,
            )
        }
        if (!bundleUrl.startsWith("https://") || !sigUrl.startsWith("https://")) {
            return@withContext FetchResult(
                failureReason = "Bundle URL must use HTTPS.",
                warnings = warnings,
            )
        }

        val publicKey = runCatching { loadPublicKey(publicKeyPem) }.getOrElse { e ->
            return@withContext FetchResult(
                failureReason = "Failed to load public key: ${e.message}",
                warnings = warnings,
            )
        }

        // Download bundle bytes.
        val bundleBytes = runCatching { downloadBytes(bundleUrl) }.getOrElse { e ->
            Log.w(TAG, "Bundle download failed: ${e.message}")
            // Fall back to verified cache.
            return@withContext loadVerifiedCache(context, publicKey, warnings)
                ?: FetchResult(
                    failureReason = "Download failed and no valid cache available: ${e.message}",
                    warnings = warnings,
                )
        }

        if (bundleBytes.size > MAX_BUNDLE_BYTES) {
            Log.w(TAG, "Bundle exceeds ${MAX_BUNDLE_BYTES} bytes; falling back to cache.")
            return@withContext loadVerifiedCache(context, publicKey, warnings)
                ?: FetchResult(
                    failureReason = "Bundle too large (${bundleBytes.size} bytes).",
                    warnings = warnings,
                )
        }

        // Download signature.
        val sigB64 = runCatching { downloadString(sigUrl) }.getOrElse { e ->
            Log.w(TAG, "Signature download failed: ${e.message}")
            return@withContext loadVerifiedCache(context, publicKey, warnings)
                ?: FetchResult(
                    failureReason = "Signature download failed: ${e.message}",
                    warnings = warnings,
                )
        }

        // Verify signature BEFORE any JSON parsing.
        val sigValid = verifyEd25519(bundleBytes, sigB64, publicKey)
        if (!sigValid) {
            Log.e(TAG, "Bundle signature verification FAILED — rejecting.")
            warnings += "Bundle signature invalid; using previous verified cache if available."
            return@withContext loadVerifiedCache(context, publicKey, warnings)
                ?: FetchResult(
                    signatureValid = false,
                    failureReason = "Signature verification failed.",
                    warnings = warnings,
                )
        }

        // Parse and validate.
        val parseResult = runCatching { parseBundle(bundleBytes, warnings) }
        if (parseResult.isFailure) {
            Log.e(TAG, "Bundle parse failed: ${parseResult.exceptionOrNull()?.message}")
            return@withContext loadVerifiedCache(context, publicKey, warnings)
                ?: FetchResult(
                    failureReason = "Bundle parse failed: ${parseResult.exceptionOrNull()?.message}",
                    warnings = warnings,
                )
        }

        val (accepted, rejected, parseWarnings) = parseResult.getOrThrow()
        warnings += parseWarnings

        // Atomically update the verified cache.
        saveVerifiedCache(context, bundleBytes, sigB64)

        Log.i(TAG, "Curated bundle imported: ${accepted.size} entries, $rejected rejected.")
        FetchResult(
            entries = accepted,
            acceptedCount = accepted.size,
            rejectedCount = rejected,
            signatureValid = true,
            sourceLabel = "Quilla Curated Intel (signed bundle)",
            warnings = warnings,
        )
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    internal data class ParseResult(
        val accepted: List<CyberKnowledgeBase.Entry>,
        val rejectedCount: Int,
        val warnings: List<String>,
    )

    internal fun parseBundle(bundleBytes: ByteArray, warnings: MutableList<String> = mutableListOf()): ParseResult {
        val bundleText = bundleBytes.toString(Charsets.UTF_8)
        val root = JSONObject(bundleText)

        // Schema version check.
        val schemaVersion = root.optInt("schema_version", -1)
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema_version: $schemaVersion")
        }

        // Entry count validation.
        val entryCount = root.optInt("entry_count", -1)
        val entriesArray = root.optJSONArray("entries") ?: JSONArray()
        if (entryCount < 0 || entryCount != entriesArray.length()) {
            throw IllegalArgumentException("entry_count mismatch: declared=$entryCount actual=${entriesArray.length()}")
        }
        if (entryCount > MAX_ENTRIES) {
            throw IllegalArgumentException("entry_count $entryCount exceeds maximum $MAX_ENTRIES")
        }

        // entries_sha256 verification.
        val claimedSha256 = root.optString("entries_sha256", "")
        val actualSha256 = sha256Hex(buildDeterministicEntriesBytes(entriesArray))
        if (claimedSha256 != actualSha256) {
            throw IllegalArgumentException("entries_sha256 mismatch")
        }

        // Parse individual entries.
        val accepted = mutableListOf<CyberKnowledgeBase.Entry>()
        var rejected = 0

        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.optJSONObject(i)

            if (obj == null) {
                rejected++
                continue
            }

            val entryResult = runCatching { parseEntry(obj, warnings) }
            if (entryResult.isSuccess) {
                val entry = entryResult.getOrThrow()
                if (entry != null) {
                    accepted += entry
                } else {
                    rejected++
                }
            } else {
                rejected++
                warnings += "Entry $i parse error: ${entryResult.exceptionOrNull()?.message?.take(120)}"
            }
        }

        return ParseResult(accepted = accepted, rejectedCount = rejected, warnings = warnings)
    }

    private fun parseEntry(obj: JSONObject, warnings: MutableList<String>): CyberKnowledgeBase.Entry? {
        val id = stripControls(obj.optString("id", "").trim())
        val title = stripControls(obj.optString("title", "").trim())

        // Require non-blank id and title.
        if (id.isBlank() || title.isBlank()) return null

        val category = stripControls(obj.optString("category", "crawler-vulnerability").trim())
            .take(64).ifBlank { "crawler-vulnerability" }

        val summary = stripControls(obj.optString("summary", "").trim()).take(MAX_SUMMARY_CHARS)
        val body = stripControls(obj.optString("body", "").trim()).take(MAX_BODY_CHARS)
        val defense = stripControls(obj.optString("defense", "").trim()).take(MAX_DEFENSE_CHARS)

        // Tags.
        val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
        val tags = buildSet {
            for (t in 0 until minOf(tagsArray.length(), MAX_TAGS)) {
                val tag = stripControls(tagsArray.optString(t).trim().lowercase())
                if (tag.isNotBlank()) add(tag)
            }
        }

        // References — require HTTPS on approved domains.
        val refsArray = obj.optJSONArray("references") ?: JSONArray()
        val refs = buildList {
            for (r in 0 until minOf(refsArray.length(), MAX_REFS)) {
                val ref = refsArray.optString(r).trim()
                if (isApprovedReference(ref)) {
                    add(ref)
                } else if (ref.isNotBlank()) {
                    warnings += "Reference rejected (unapproved domain or not HTTPS): ${ref.take(100)}"
                }
            }
        }

        return CyberKnowledgeBase.Entry(
            id = id.take(128),
            title = title.take(MAX_TITLE_CHARS),
            category = category,
            tags = tags,
            summary = summary,
            body = body,
            defense = defense,
            references = refs,
        )
    }

    // ── Signature verification ────────────────────────────────────────────────

    // java.util.Base64 is API 26 and this module ships at minSdk 24; android.util.Base64
    // returns stubs under the unit tests' returnDefaultValues. Hence a local decoder.
    private val BASE64_INVERSE = IntArray(128) { -1 }.also { table ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            .forEachIndexed { index, c -> table[c.code] = index }
    }

    internal fun decodeBase64(input: String): ByteArray {
        val out = ByteArrayOutputStream(input.length / 4 * 3 + 3)
        var accumulator = 0
        var bitsCollected = 0
        for (c in input) {
            if (c == '=') break
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue
            val value = if (c.code < 128) BASE64_INVERSE[c.code] else -1
            require(value >= 0) { "Invalid base64 character: $c" }
            accumulator = (accumulator shl 6) or value
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                out.write((accumulator shr bitsCollected) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    internal fun verifyEd25519(data: ByteArray, sigB64: String, publicKey: PublicKey): Boolean {
        return runCatching {
            val sigBytes = decodeBase64(sigB64.trim())
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(sigBytes)
        }.getOrElse { false }
    }

    internal fun loadPublicKey(pemBytes: ByteArray): PublicKey {
        val pem = String(pemBytes, Charsets.UTF_8)
        val b64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val der = decodeBase64(b64)
        val keySpec = java.security.spec.X509EncodedKeySpec(der)
        return java.security.KeyFactory.getInstance("Ed25519").generatePublic(keySpec)
    }

    // ── Cache management ──────────────────────────────────────────────────────

    private fun saveVerifiedCache(context: Context, bundleBytes: ByteArray, sigB64: String) {
        runCatching {
            val cacheDir = context.filesDir
            val tmpJson = File(cacheDir, "$CACHE_FILENAME.tmp")
            val tmpSig = File(cacheDir, "$CACHE_SIG_FILENAME.tmp")
            tmpJson.writeBytes(bundleBytes)
            tmpSig.writeText(sigB64, Charsets.US_ASCII)
            // Atomic rename.
            tmpJson.renameTo(File(cacheDir, CACHE_FILENAME))
            tmpSig.renameTo(File(cacheDir, CACHE_SIG_FILENAME))
        }.onFailure { Log.w(TAG, "Cache write failed: ${it.message}") }
    }

    private fun loadVerifiedCache(
        context: Context,
        publicKey: PublicKey,
        warnings: MutableList<String>,
    ): FetchResult? {
        return runCatching {
            val cacheDir = context.filesDir
            val jsonFile = File(cacheDir, CACHE_FILENAME)
            val sigFile = File(cacheDir, CACHE_SIG_FILENAME)
            if (!jsonFile.exists() || !sigFile.exists()) return@runCatching null

            val bundleBytes = jsonFile.readBytes()
            val sigB64 = sigFile.readText(Charsets.US_ASCII).trim()

            // Never load unverified cache.
            if (!verifyEd25519(bundleBytes, sigB64, publicKey)) {
                Log.e(TAG, "Cached bundle signature invalid — discarding cache.")
                jsonFile.delete()
                sigFile.delete()
                return@runCatching null
            }

            val (accepted, rejected, parseWarnings) = parseBundle(bundleBytes, warnings)
            warnings += parseWarnings
            warnings += "Loaded from verified cache (refresh failed)."
            FetchResult(
                entries = accepted,
                acceptedCount = accepted.size,
                rejectedCount = rejected,
                signatureValid = true,
                sourceLabel = "Quilla Curated Intel (verified cache)",
                warnings = warnings,
            )
        }.getOrElse { e ->
            Log.w(TAG, "Cache load failed: ${e.message}")
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun downloadBytes(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CoreGuard-QuillaIntelImporter/1.0")
        }
        return conn.use {
            it.connect()
            check(it.responseCode in 200..299) {
                "HTTP ${it.responseCode} from $url"
            }
            val buf = it.inputStream.readBytes()
            check(buf.size <= MAX_BUNDLE_BYTES) {
                "Response too large: ${buf.size} bytes"
            }
            buf
        }
    }

    private fun downloadString(url: String): String =
        downloadBytes(url).toString(Charsets.US_ASCII)

    private fun isApprovedReference(ref: String): Boolean {
        if (!ref.startsWith("https://")) return false
        return try {
            val host = URL(ref).host.lowercase()
            APPROVED_REF_DOMAINS.any { approved -> host == approved || host.endsWith(".$approved") }
        } catch (_: Exception) {
            false
        }
    }

    private fun stripControls(text: String): String =
        text.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]"), "")

    private fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * Re-serialize entries array in a deterministic way matching the Python bundle format:
     * sorted JSON keys, compact separators, UTF-8.
     */
    private fun buildDeterministicEntriesBytes(entriesArray: JSONArray): ByteArray {
        // Use the raw array string from the bundle (already deterministic from the crawler).
        // We re-serialize it in canonical form to verify the sha256.
        val entries = mutableListOf<Map<String, Any>>()
        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.optJSONObject(i) ?: continue
            val sorted = sortedMapOf<String, Any>()
            for (key in obj.keys()) {
                sorted[key] = obj.get(key)
            }
            entries.add(sorted)
        }
        // Re-serialize with sorted keys and compact separators to match Python's json.dumps.
        return buildCompactJson(entries).toByteArray(Charsets.UTF_8)
    }

    private fun buildCompactJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is String -> quoteJsonString(value)
        is JSONArray -> (0 until value.length())
            .joinToString(",", "[", "]") { index -> buildCompactJson(value.get(index)) }
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .joinToString(",", "{", "}") { key ->
                "${quoteJsonString(key)}:${buildCompactJson(value.get(key))}"
            }
        is List<*> -> value.joinToString(",", "[", "]") { buildCompactJson(it) }
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }
            .joinToString(",", "{", "}") { (key, nestedValue) ->
                "${quoteJsonString(key.toString())}:${buildCompactJson(nestedValue)}"
            }
        else -> quoteJsonString(value.toString())
    }

    private fun quoteJsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"'  -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u%04x".format(c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    private fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }
}
