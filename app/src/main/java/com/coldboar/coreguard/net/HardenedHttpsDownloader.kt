package com.coldboar.coreguard.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Shared HTTPS downloader for public intelligence feeds.
 *
 * Enforces: HTTPS only, exact host allowlist, manual redirect validation,
 * bounded incremental reads (never [InputStream.readBytes] before the cap),
 * optional content-type prefix, and optional SHA-256 digest verification.
 * Integrity failures fail closed — callers must not consume or persist bytes.
 */
object HardenedHttpsDownloader {

    const val DEFAULT_MAX_REDIRECTS = 3

    data class Policy(
        val allowedHosts: Set<String>,
        val maxBytes: Int,
        val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
        val connectTimeoutMs: Int = 12_000,
        val readTimeoutMs: Int = 30_000,
        val acceptHeader: String = "application/json, */*",
        val userAgent: String = "CoreGuard-HardenedIntel/1.0",
        /** When non-null, response body must match this lowercase hex digest. */
        val expectedSha256Hex: String? = null,
        /** When non-null, Content-Type must start with this prefix (ignore case). */
        val requireContentTypePrefix: String? = null
    )

    sealed class Result {
        data class Success(val bytes: ByteArray, val finalUrl: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun hostAllowed(host: String, allowedHosts: Set<String>): Boolean {
        val h = host.lowercase()
        return allowedHosts.any { allowed ->
            val a = allowed.lowercase()
            h == a || h.endsWith(".$a")
        }
    }

    fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * Reads at most [maxBytes] from [input]. Returns null if the stream would
     * exceed the cap (caller must treat as failure — no partial consume).
     */
    fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8_192)
        val out = ByteArrayOutputStream()
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (out.size() + read > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    fun download(url: String, policy: Policy): Result {
        if (!url.startsWith("https://")) {
            return Result.Failure("Only HTTPS URLs are allowed")
        }
        return downloadHop(url, policy, redirectsLeft = policy.maxRedirects)
    }

    private fun downloadHop(url: String, policy: Policy, redirectsLeft: Int): Result {
        val parsed = try {
            URL(url)
        } catch (_: Exception) {
            return Result.Failure("Malformed URL")
        }
        if (parsed.protocol != "https") {
            return Result.Failure("Only HTTPS URLs are allowed")
        }
        val host = parsed.host ?: return Result.Failure("Missing host")
        if (!hostAllowed(host, policy.allowedHosts)) {
            return Result.Failure("Host not on allowlist: $host")
        }

        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = policy.connectTimeoutMs
            readTimeout = policy.readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", policy.acceptHeader)
            setRequestProperty("User-Agent", policy.userAgent)
        }
        return try {
            connection.connect()
            val status = connection.responseCode
            if (status in 301..308) {
                if (redirectsLeft <= 0) {
                    return Result.Failure("Too many redirects")
                }
                val location = connection.getHeaderField("Location")
                    ?: return Result.Failure("Redirect without Location")
                val next = when {
                    location.startsWith("https://") -> location
                    location.startsWith("/") -> "https://$host$location"
                    else -> return Result.Failure("Redirect to non-HTTPS URL rejected")
                }
                connection.disconnect()
                return downloadHop(next, policy, redirectsLeft - 1)
            }
            if (status !in 200..299) {
                return Result.Failure("HTTP $status from server")
            }

            val contentLength = connection.contentLength
            if (contentLength > policy.maxBytes) {
                return Result.Failure("Response too large ($contentLength bytes)")
            }

            val contentType = connection.contentType
            val requiredPrefix = policy.requireContentTypePrefix
            if (requiredPrefix != null &&
                (contentType == null || !contentType.startsWith(requiredPrefix, ignoreCase = true))
            ) {
                return Result.Failure("Unexpected Content-Type: $contentType")
            }

            val bytes = connection.inputStream.use { input ->
                readBounded(input, policy.maxBytes)
            } ?: return Result.Failure("Response too large (>${policy.maxBytes} bytes)")

            val expected = policy.expectedSha256Hex?.lowercase()
            if (expected != null) {
                val actual = sha256Hex(bytes)
                if (actual != expected) {
                    return Result.Failure("SHA-256 mismatch (integrity failure)")
                }
            }

            Result.Success(bytes = bytes, finalUrl = url)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }
}
