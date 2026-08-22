package com.coldboar.coreguard.net

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Injectable HTTP transport for [HardenedHttpsDownloader] unit tests.
 * Production uses [UrlConnectionHttpTransport] (streamed; no pre-cap allocation).
 */
interface HttpTransport {
    data class Response(
        val code: Int,
        val location: String? = null,
        val contentType: String? = null,
        val contentLength: Int = -1,
        val body: InputStream = ByteArrayInputStream(ByteArray(0))
    )

    fun get(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        headers: Map<String, String>
    ): Response
}

object UrlConnectionHttpTransport : HttpTransport {
    override fun get(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        headers: Map<String, String>
    ): HttpTransport.Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        connection.connect()
        val code = connection.responseCode
        val raw = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val body = object : InputStream() {
            private val delegate: InputStream = raw ?: ByteArrayInputStream(ByteArray(0))
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
            override fun close() {
                runCatching { delegate.close() }
                connection.disconnect()
            }
        }
        return HttpTransport.Response(
            code = code,
            location = connection.getHeaderField("Location"),
            contentType = connection.contentType,
            contentLength = connection.contentLength,
            body = body
        )
    }
}
