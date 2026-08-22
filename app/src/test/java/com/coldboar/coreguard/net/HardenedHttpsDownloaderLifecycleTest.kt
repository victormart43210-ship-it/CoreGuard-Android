package com.coldboar.coreguard.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HardenedHttpsDownloaderLifecycleTest {

    private class TrackingStream(
        private val delegate: InputStream,
        private val onClose: () -> Unit
    ) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() {
            onClose()
            delegate.close()
        }
    }

    private class ThrowingReadStream : InputStream() {
        var closed = false
        override fun read(): Int = throw IOException("read boom")
        override fun close() {
            closed = true
        }
    }

    private class ScriptedTransport(
        private val responses: MutableList<HttpTransport.Response>
    ) : HttpTransport {
        override fun get(
            url: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            headers: Map<String, String>
        ): HttpTransport.Response {
            if (responses.isEmpty()) error("no response")
            return responses.removeAt(0)
        }
    }

    private fun policy(
        hosts: Set<String> = setOf("raw.githubusercontent.com", "www.cisa.gov"),
        maxBytes: Int = 1024,
        expectedSha: String? = null,
        contentTypePrefix: String? = null
    ) = HardenedHttpsDownloader.Policy(
        allowedHosts = hosts,
        maxBytes = maxBytes,
        expectedSha256Hex = expectedSha,
        requireContentTypePrefix = contentTypePrefix
    )

    @Test
    fun `host allowlist is exact and case-insensitive without implicit subdomains`() {
        val allowed = setOf("raw.githubusercontent.com", "www.cisa.gov")
        assertTrue(HardenedHttpsDownloader.hostAllowed("raw.githubusercontent.com", allowed))
        assertTrue(HardenedHttpsDownloader.hostAllowed("RAW.GitHubusercontent.COM", allowed))
        assertFalse(
            "implicit subdomain must not be accepted",
            HardenedHttpsDownloader.hostAllowed("cdn.www.cisa.gov", allowed)
        )
        assertFalse(HardenedHttpsDownloader.hostAllowed("evil.com", allowed))
        assertFalse(HardenedHttpsDownloader.hostAllowed("githubusercontent.com.evil.com", allowed))
    }

    @Test
    fun `explicit wildcard policy accepts subdomains only`() {
        val allowed = setOf("*.cisa.gov")
        assertTrue(HardenedHttpsDownloader.hostAllowed("www.cisa.gov", allowed))
        assertTrue(HardenedHttpsDownloader.hostAllowed("cdn.cisa.gov", allowed))
        assertFalse(HardenedHttpsDownloader.hostAllowed("cisa.gov", allowed))
        assertFalse(HardenedHttpsDownloader.hostAllowed("evilcisa.gov", allowed))
    }

    @Test
    fun `throwing input stream becomes typed failure and closes`() {
        val stream = ThrowingReadStream()
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "application/json",
                    body = stream
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            url = "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy = policy(),
            transport = transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("Body read failed"))
        assertTrue(stream.closed)
    }

    @Test
    fun `non-2xx closes body`() {
        val closed = AtomicBoolean(false)
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 500,
                    body = TrackingStream(ByteArrayInputStream(ByteArray(0))) { closed.set(true) }
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy(),
            transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue(closed.get())
    }

    @Test
    fun `redirect closes intermediate response before following`() {
        val closes = AtomicInteger(0)
        val body = "ok".toByteArray()
        val sha = HardenedHttpsDownloader.sha256Hex(body)
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 302,
                    location = "https://raw.githubusercontent.com/org/repo/abc/file2.json",
                    body = TrackingStream(ByteArrayInputStream(ByteArray(0))) { closes.incrementAndGet() }
                ),
                HttpTransport.Response(
                    code = 200,
                    contentType = "application/json",
                    body = TrackingStream(ByteArrayInputStream(body)) { closes.incrementAndGet() }
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy(expectedSha = sha),
            transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Success)
        assertEquals(2, closes.get())
    }

    @Test
    fun `invalid content type closes body`() {
        val closed = AtomicBoolean(false)
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentType = "text/html",
                    body = TrackingStream(ByteArrayInputStream("x".toByteArray())) { closed.set(true) }
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy(contentTypePrefix = "application/json"),
            transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue(closed.get())
    }

    @Test
    fun `oversized content-length closes body`() {
        val closed = AtomicBoolean(false)
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    contentLength = 99999,
                    body = TrackingStream(ByteArrayInputStream(ByteArray(0))) { closed.set(true) }
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy(maxBytes = 10),
            transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue(closed.get())
    }

    @Test
    fun `success path closes body`() {
        val closed = AtomicBoolean(false)
        val body = "hello".toByteArray()
        val transport = ScriptedTransport(
            mutableListOf(
                HttpTransport.Response(
                    code = 200,
                    body = TrackingStream(ByteArrayInputStream(body)) { closed.set(true) }
                )
            )
        )
        val result = HardenedHttpsDownloader.download(
            "https://raw.githubusercontent.com/org/repo/abc/file.json",
            policy(expectedSha = HardenedHttpsDownloader.sha256Hex(body)),
            transport
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Success)
        assertTrue(closed.get())
    }
}
