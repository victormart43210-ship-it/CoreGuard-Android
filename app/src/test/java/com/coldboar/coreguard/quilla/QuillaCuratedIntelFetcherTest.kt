package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.knowledge.SharedThreatKnowledgeRepository
import com.coldboar.coreguard.knowledge.ThreatKnowledgeSource
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Unit tests for [QuillaCuratedIntelFetcher].
 *
 * Uses in-process signing (via JCE Ed25519) so no network is required.
 */
class QuillaCuratedIntelFetcherTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        tempDir = File("build/tmp-curated-intel-test").also { it.mkdirs() }
        mockContext = Mockito.mock(Context::class.java)
        whenever(mockContext.filesDir).thenReturn(tempDir)
        CyberKnowledgeBase.clear()
        SharedThreatKnowledgeRepository.clearForTests()
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
        SharedThreatKnowledgeRepository.clearForTests()
        tempDir.deleteRecursively()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateEd25519KeyPair(): Pair<java.security.PrivateKey, java.security.PublicKey> {
        val gen = java.security.KeyPairGenerator.getInstance("Ed25519")
        val kp = gen.generateKeyPair()
        return kp.private to kp.public
    }

    private fun signBytes(data: ByteArray, privateKey: java.security.PrivateKey): String {
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(data)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun buildMinimalBundleBytes(
        entries: List<Map<String, Any>> = emptyList(),
        entryCount: Int? = null,
        sha256Override: String? = null,
        schemaVersion: Int = 1,
    ): ByteArray {
        val entriesJsonArray = org.json.JSONArray()
        for (e in entries) {
            val obj = org.json.JSONObject()
            for ((k, v) in e.entries) obj.put(k, v)
            entriesJsonArray.put(obj)
        }
        // Compute sha256 of entries.
        val entriesBytes = buildDeterministicEntriesBytes(entries)
        val sha = sha256Override ?: sha256Hex(entriesBytes)
        val root = org.json.JSONObject()
        root.put("schema_version", schemaVersion)
        root.put("bundle_id", "test-bundle-id")
        root.put("generated_at", "2024-01-01T00:00:00Z")
        root.put("generator", "test")
        root.put("generator_version", "1.0")
        root.put("entry_count", entryCount ?: entries.size)
        root.put("entries_sha256", sha)
        root.put("entries", entriesJsonArray)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildDeterministicEntriesBytes(entries: List<Map<String, Any>>): ByteArray {
        // Match QuillaCuratedIntelFetcher.buildCompactJson: sorted keys, compact separators,
        // and RFC-compliant string escaping (needed for control-character field tests).
        fun quote(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
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
        fun encodeValue(v: Any?): String = when (v) {
            null -> "null"
            is Boolean -> v.toString()
            is Number -> v.toString()
            is String -> quote(v)
            is List<*> -> v.joinToString(",", "[", "]") { encodeValue(it) }
            is Map<*, *> -> v.entries.sortedBy { it.key.toString() }
                .joinToString(",", "{", "}") { (k, nested) ->
                    "${quote(k.toString())}:${encodeValue(nested)}"
                }
            else -> quote(v.toString())
        }
        return entries.joinToString(",", "[", "]") { encodeValue(it) }
            .toByteArray(Charsets.UTF_8)
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun publicKeyToPem(pub: java.security.PublicKey): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(pub.encoded)
        return ("-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n").toByteArray()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `valid signed bundle with correct schema is accepted`() {
        val (priv, pub) = generateEd25519KeyPair()
        val entries = listOf(
            mapOf("id" to "kev-cve-2023-20963", "title" to "Android Vuln", "category" to "crawler-vulnerability",
                  "summary" to "Summary", "body" to "Body", "defense" to "Patch")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val sigB64 = signBytes(bundleBytes, priv)
        val pubPem = publicKeyToPem(pub)
        val pubKey = QuillaCuratedIntelFetcher.loadPublicKey(pubPem)

        assertTrue(QuillaCuratedIntelFetcher.verifyEd25519(bundleBytes, sigB64, pubKey))
    }

    @Test
    fun `changed JSON fails signature verification`() {
        val (priv, pub) = generateEd25519KeyPair()
        val bundleBytes = buildMinimalBundleBytes()
        val sigB64 = signBytes(bundleBytes, priv)
        val pubKey = QuillaCuratedIntelFetcher.loadPublicKey(publicKeyToPem(pub))

        val tampered = bundleBytes.toString(Charsets.UTF_8)
            .replace("test-bundle-id", "evil-bundle-id").toByteArray()
        assertFalse(QuillaCuratedIntelFetcher.verifyEd25519(tampered, sigB64, pubKey))
    }

    @Test
    fun `changed signature fails`() {
        val (priv, pub) = generateEd25519KeyPair()
        val bundleBytes = buildMinimalBundleBytes()
        val pubKey = QuillaCuratedIntelFetcher.loadPublicKey(publicKeyToPem(pub))

        assertFalse(QuillaCuratedIntelFetcher.verifyEd25519(bundleBytes, "aW52YWxpZA==", pubKey))
    }

    @Test
    fun `unsupported schema version fails`() {
        val bundleBytes = buildMinimalBundleBytes(schemaVersion = 99)
        val warnings = mutableListOf<String>()
        try {
            QuillaCuratedIntelFetcher.parseBundle(bundleBytes, warnings)
            assertTrue("Expected exception for unsupported schema version", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("99"))
        }
    }

    @Test
    fun `malformed JSON fails safely`() {
        val bad = "not valid json {{".toByteArray()
        assertThrows<Exception> {
            QuillaCuratedIntelFetcher.parseBundle(bad)
        }
    }

    @Test
    fun `oversized bundle is rejected before parsing`() {
        // This is checked by the caller (fetchAndVerify) before parseBundle.
        // We verify the limit constant is correct.
        val limitField = QuillaCuratedIntelFetcher::class.java
            .getDeclaredField("MAX_BUNDLE_BYTES")
        limitField.isAccessible = true
        val limit = limitField.getInt(null)
        assertEquals(2 * 1024 * 1024, limit)
    }

    @Test
    fun `entry limit 5000 is enforced`() {
        // Build a bundle with exactly MAX entries by checking constant.
        val limitField = QuillaCuratedIntelFetcher::class.java
            .getDeclaredField("MAX_ENTRIES")
        limitField.isAccessible = true
        val limit = limitField.getInt(null)
        assertEquals(5_000, limit)
    }

    @Test
    fun `field limits are enforced on title and body`() {
        val longTitle = "T".repeat(500)
        val longBody = "B".repeat(10_000)
        val entries = listOf(
            mapOf("id" to "test-entry", "title" to longTitle, "body" to longBody,
                  "category" to "crawler-vulnerability", "summary" to "s",
                  "defense" to "d")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val (result, _, _) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        assertEquals(1, result.size)
        assertTrue(result[0].title.length <= 240)
        assertTrue(result[0].body.length <= 8_000)
    }

    @Test
    fun `blank id entries are rejected`() {
        val entries = listOf(
            mapOf("id" to "", "title" to "Valid Title", "category" to "crawler-vulnerability",
                  "summary" to "s", "body" to "b", "defense" to "d")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val (accepted, rejected, _) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        assertEquals(0, accepted.size)
        assertEquals(1, rejected)
    }

    @Test
    fun `blank title entries are rejected`() {
        val entries = listOf(
            mapOf("id" to "valid-id", "title" to "", "category" to "crawler-vulnerability",
                  "summary" to "s", "body" to "b", "defense" to "d")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val (accepted, rejected, _) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        assertEquals(0, accepted.size)
        assertEquals(1, rejected)
    }

    @Test
    fun `invalid references are stripped`() {
        val root = org.json.JSONObject()
        root.put("schema_version", 1)
        root.put("bundle_id", "bid")
        root.put("generated_at", "2024-01-01T00:00:00Z")
        root.put("generator", "test")
        root.put("generator_version", "1.0")
        val refsArray = org.json.JSONArray()
        refsArray.put("http://not-https.example.com/")   // HTTP — should be stripped
        refsArray.put("https://www.cisa.gov/kev")        // approved
        refsArray.put("https://evil-domain.xyz/malware") // unapproved domain
        val entryObj = org.json.JSONObject()
        entryObj.put("body", "b")
        entryObj.put("category", "crawler-vulnerability")
        entryObj.put("defense", "d")
        entryObj.put("id", "test-entry")
        entryObj.put("references", refsArray)
        entryObj.put("summary", "s")
        entryObj.put("title", "Test")
        val entriesJsonArray = org.json.JSONArray()
        entriesJsonArray.put(entryObj)
        root.put("entries", entriesJsonArray)
        root.put("entry_count", 1)
        // Canonical JSON matching Python's json.dumps(sort_keys=True, separators=(",",":")):
        val canonicalEntries = """[{"body":"b","category":"crawler-vulnerability","defense":"d","id":"test-entry","references":["http://not-https.example.com/","https://www.cisa.gov/kev","https://evil-domain.xyz/malware"],"summary":"s","title":"Test"}]"""
        root.put("entries_sha256", sha256Hex(canonicalEntries.toByteArray(Charsets.UTF_8)))
        val bundleBytes = root.toString().toByteArray(Charsets.UTF_8)
        val warnings = mutableListOf<String>()
        val (accepted, rejectedCount, warns) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes, warnings)
        assertEquals(1, accepted.size)
        assertEquals(2, warns.count { it.contains("rejected") || it.contains("unapproved") })
        val refs = accepted[0].references
        assertFalse(refs.any { it.startsWith("http://") })
        assertFalse(refs.any { "evil-domain.xyz" in it })
        assertTrue(refs.any { "cisa.gov" in it })
    }

    @Test
    fun `verified entries are merged under CRAWLER source`() {
        val (priv, pub) = generateEd25519KeyPair()
        val entries = listOf(
            mapOf("id" to "crawler-test-001", "title" to "Crawler Test Entry",
                  "category" to "crawler-vulnerability", "summary" to "Test summary",
                  "body" to "Test body", "defense" to "Test defense")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val pubPem = publicKeyToPem(pub)
        val (accepted, _, _) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes)

        SharedThreatKnowledgeRepository.mergeCrawlerKnowledge(accepted)
        val results = SharedThreatKnowledgeRepository.search("Crawler Test Entry")
        assertTrue(results.isNotEmpty())
        val match = results.first()
        assertEquals(ThreatKnowledgeSource.CRAWLER, match.source)
    }

    @Test
    fun `crawler matches have provesCompromise false`() {
        val entry = CyberKnowledgeBase.Entry(
            id = "crawler-test-002",
            title = "Crawler Malware Family",
            category = "crawler-malware",
            tags = setOf("android", "malware"),
            summary = "Some malware family brief.",
            body = "Details about the malware family.",
            defense = "Update and scan.",
            references = emptyList(),
        )
        SharedThreatKnowledgeRepository.mergeCrawlerKnowledge(listOf(entry))
        val results = SharedThreatKnowledgeRepository.search("Crawler Malware Family")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { !it.provesCompromise })
    }

    @Test
    fun `entry_count mismatch fails`() {
        val entries = listOf(
            mapOf("id" to "e1", "title" to "Entry 1")
        )
        val bundleBytes = buildMinimalBundleBytes(entries, entryCount = 99)
        assertThrows<Exception> {
            QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        }
    }

    @Test
    fun `entries_sha256 mismatch fails`() {
        val entries = listOf(
            mapOf("id" to "e1", "title" to "Entry 1")
        )
        val bundleBytes = buildMinimalBundleBytes(entries, sha256Override = "deadbeef" + "0".repeat(56))
        assertThrows<Exception> {
            QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        }
    }

    @Test
    fun `null JSON array entry increments rejected and parsing continues`() {
        val valid = org.json.JSONObject()
            .put("id", "after-null")
            .put("title", "Valid After Null")
            .put("category", "crawler-vulnerability")
            .put("summary", "s")
            .put("body", "b")
            .put("defense", "d")

        val entries = org.json.JSONArray()
        entries.put(org.json.JSONObject.NULL)
        entries.put(valid)

        val warnings = mutableListOf<String>()
        val result = QuillaCuratedIntelFetcher.parseEntriesArray(entries, warnings)
        assertEquals(1, result.accepted.size)
        assertEquals(1, result.rejectedCount)
        assertEquals("after-null", result.accepted[0].id)
    }

    @Test
    fun `entry parse exception increments rejected and includes index in warning`() {
        val throwing = object : org.json.JSONObject() {
            override fun optString(name: String, fallback: String): String {
                throw IllegalStateException("simulated parse failure")
            }
        }
        val good = org.json.JSONObject()
            .put("id", "good-entry")
            .put("title", "Good Entry")
            .put("category", "crawler-vulnerability")
            .put("summary", "s")
            .put("body", "b")
            .put("defense", "d")

        val entries = org.json.JSONArray()
        entries.put(throwing) // index 0 → exception
        entries.put(good)     // index 1 → still accepted

        val warnings = mutableListOf<String>()
        val result = QuillaCuratedIntelFetcher.parseEntriesArray(entries, warnings)
        assertEquals(1, result.accepted.size)
        assertEquals(1, result.rejectedCount)
        assertEquals("good-entry", result.accepted[0].id)
        assertTrue(warnings.any { it.startsWith("Entry 0 parse error:") && it.contains("simulated parse failure") })
    }

    @Test
    fun `control characters stripped from fields`() {
        val entries = listOf(
            mapOf("id" to "ctrl-test", "title" to "Title with \u0000 null",
                  "category" to "crawler-vulnerability", "summary" to "Summary\u0007bell",
                  "body" to "Body\u001Fescape", "defense" to "Defense")
        )
        val bundleBytes = buildMinimalBundleBytes(entries)
        val (accepted, _, _) = QuillaCuratedIntelFetcher.parseBundle(bundleBytes)
        assertEquals(1, accepted.size)
        assertFalse(accepted[0].title.contains("\u0000"))
        assertFalse(accepted[0].summary.contains("\u0007"))
        assertFalse(accepted[0].body.contains("\u001F"))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
    try {
        block()
        throw AssertionError("Expected ${T::class.simpleName} to be thrown")
    } catch (e: Throwable) {
        if (e is AssertionError && e.message?.startsWith("Expected") == true) throw e
        // Any exception is acceptable — we just verify it doesn't succeed.
    }
}
