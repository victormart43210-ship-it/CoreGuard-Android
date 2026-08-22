package com.coldboar.coreguard.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class HardenedHttpsDownloaderTransportTest {

    private class ScriptedTransport(
        private val responses: MutableList<HttpTransport.Response>
    ) : HttpTransport {
        val requested = mutableListOf<String>()
        override fun get(
            url: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            headers: Map<String, String>
        ): HttpTransport.Response {
            requested += url
            if (responses.isEmpty()) error("no scripted response for $url")
            return responses.removeAt(0)
        }
    }

    private fun policy(
        expectedSha: String? = null,
        maxBytes: Int = 1024,
        maxRedirects: Int = 3,
        contentTypePrefix: String? = null
    ) = HardenedHttpsDownloader.Policy(
        allowedHosts = setOf("raw.githubusercontent.com", "www.cisa.gov"),
        maxBytes = maxBytes,
        maxRedirects = maxRedirects,
        expectedSha256Hex = expectedSha,
        requireContentTypePrefix = contentTypePrefix
    )

    @Test
    fun `sha256 mismatch returns failure with no success bytes`() {
        val body = "poison".toByteArray()
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "application/json",
                    body = ByteArrayInputStream(body)
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(expectedSha = "00".repeat(32)),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("SHA-256"))
    }

    @Test
    fun `successful digest verification returns bytes`() {
        val body = "hello-intel".toByteArray()
        val sha = HardenedHttpsDownloader.sha256Hex(body)
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "application/json",
                    body = ByteArrayInputStream(body)
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(expectedSha = sha),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Success)
        assertEquals("hello-intel", (result as HardenedHttpsDownloader.Result.Success).bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `redirect to unapproved host is rejected`() {
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 302,
                    location = "https://evil.example/steal.json"
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(expectedSha = "aa"),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("allowlist"))
    }

    @Test
    fun `redirect exhaustion fails closed`() {
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(code = 302, location = "https://raw.githubusercontent.com/a/b/c/1"),
                HttpTransport.Response(code = 302, location = "https://raw.githubusercontent.com/a/b/c/2"),
                HttpTransport.Response(code = 302, location = "https://raw.githubusercontent.com/a/b/c/3"),
                HttpTransport.Response(code = 302, location = "https://raw.githubusercontent.com/a/b/c/4")
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(expectedSha = "aa", maxRedirects = 2),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("redirect", ignoreCase = true))
    }

    @Test
    fun `streamed body exceeding maxBytes fails`() {
        val big = ByteArray(200) { 1 }
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "application/json",
                    body = ByteArrayInputStream(big)
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(expectedSha = HardenedHttpsDownloader.sha256Hex(big), maxBytes = 50),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("too large", ignoreCase = true))
    }

    @Test
    fun `content-type rejection`() {
        val body = "{}".toByteArray()
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "text/html",
                    body = ByteArrayInputStream(body)
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(
                expectedSha = HardenedHttpsDownloader.sha256Hex(body),
                contentTypePrefix = "application/json"
            ),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("Content-Type"))
    }

    @Test
    fun `malformed and non-HTTPS URLs fail without transport`() {
        val t = ScriptedTransport(mutableListOf())
        val clear = HardenedHttpsDownloader.download("http://example.com/x", policy(), t)
        assertTrue(clear is HardenedHttpsDownloader.Result.Failure)
        val malformed = HardenedHttpsDownloader.download("https://", policy(), t)
        assertTrue(malformed is HardenedHttpsDownloader.Result.Failure)
        assertTrue(t.requested.isEmpty())
    }
}
