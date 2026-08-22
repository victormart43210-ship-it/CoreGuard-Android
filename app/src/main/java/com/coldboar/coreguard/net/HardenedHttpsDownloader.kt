package com.coldboar.coreguard.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest

/**
 * Shared HTTPS downloader for public intelligence feeds.
 *
 * Enforces: HTTPS only, exact (case-insensitive) host allowlist with optional
 * explicit wildcard entries (`*.example.com`), manual redirect validation,
 * bounded incremental reads, optional content-type prefix, and optional SHA-256
 * digest verification. Integrity failures fail closed — callers must not consume
 * or persist bytes.
 *
 * The complete response lifecycle is enclosed in exception handling; the body
 * stream (and underlying connection) is closed on every success and failure path.
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

    /**
     * Exact case-insensitive host match, or an explicit wildcard policy entry
     * of the form `*.example.com` (matches `a.example.com`, not `example.com`).
     * Implicit subdomain acceptance without a wildcard entry is not allowed.
     */
    fun hostAllowed(host: String, allowedHosts: Set<String>): Boolean {
        val h = host.lowercase()
        return allowedHosts.any { allowed ->
            val a = allowed.lowercase()
            when {
                a.startsWith("*.") -> {
                    val suffix = a.removePrefix("*.")
                    suffix.isNotEmpty() && h != suffix && h.endsWith(".$suffix")
                }
                else -> h == a
            }
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

    fun download(
        url: String,
        policy: Policy,
        transport: HttpTransport = UrlConnectionHttpTransport
    ): Result {
        if (!url.startsWith("https://")) {
            return Result.Failure("Only HTTPS URLs are allowed")
        }
        return downloadHop(url, policy, transport, redirectsLeft = policy.maxRedirects)
    }

    private fun downloadHop(
        url: String,
        policy: Policy,
        transport: HttpTransport,
        redirectsLeft: Int
    ): Result {
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

        val response = try {
            transport.get(
                url = url,
                connectTimeoutMs = policy.connectTimeoutMs,
                readTimeoutMs = policy.readTimeoutMs,
                headers = mapOf(
                    "Accept" to policy.acceptHeader,
                    "User-Agent" to policy.userAgent
                )
            )
        } catch (e: Exception) {
            return Result.Failure(e.message ?: "Network error")
        }

        var result: Result = Result.Failure("Response handling failed")
        var redirectTo: String? = null
        try {
            val status = response.code
            if (status in 301..308) {
                if (redirectsLeft <= 0) {
                    result = Result.Failure("Too many redirects")
                } else {
                    val location = response.location
                    if (location == null) {
                        result = Result.Failure("Redirect without Location")
                    } else {
                        val next = when {
                            location.startsWith("https://") -> location
                            location.startsWith("/") -> "https://$host$location"
                            else -> null
                        }
                        if (next == null) {
                            result = Result.Failure("Redirect to non-HTTPS URL rejected")
                        } else {
                            val nextHost = try {
                                URL(next).host
                            } catch (_: Exception) {
                                null
                            }
                            when {
                                nextHost == null ->
                                    result = Result.Failure("Malformed redirect URL")
                                !hostAllowed(nextHost, policy.allowedHosts) ->
                                    result = Result.Failure("Redirect host not on allowlist: $nextHost")
                                else -> {
                                    redirectTo = next
                                    result = Result.Failure("redirect-pending")
                                }
                            }
                        }
                    }
                }
            } else if (status !in 200..299) {
                result = Result.Failure("HTTP $status from server")
            } else if (response.contentLength > policy.maxBytes) {
                result = Result.Failure("Response too large (${response.contentLength} bytes)")
            } else {
                val requiredPrefix = policy.requireContentTypePrefix
                if (requiredPrefix != null &&
                    (response.contentType == null ||
                        !response.contentType.startsWith(requiredPrefix, ignoreCase = true))
                ) {
                    result = Result.Failure("Unexpected Content-Type: ${response.contentType}")
                } else {
                    try {
                        val bytes = readBounded(response.body, policy.maxBytes)
                        if (bytes == null) {
                            result = Result.Failure("Response too large (>${policy.maxBytes} bytes)")
                        } else {
                            val expected = policy.expectedSha256Hex?.lowercase()
                            result = if (expected != null && sha256Hex(bytes) != expected) {
                                Result.Failure("SHA-256 mismatch (integrity failure)")
                            } else {
                                Result.Success(bytes = bytes, finalUrl = url)
                            }
                        }
                    } catch (e: Exception) {
                        result = Result.Failure("Body read failed: ${e.message ?: "I/O error"}")
                    }
                }
            }
        } catch (e: Exception) {
            result = Result.Failure(e.message ?: "Response handling failed")
            redirectTo = null
        } finally {
            try {
                response.body.close()
            } catch (e: Exception) {
                val closeReason = "Body close failed: ${e.message ?: "I/O error"}"
                if (result is Result.Success) {
                    result = Result.Failure(closeReason)
                } else if (redirectTo != null) {
                    redirectTo = null
                    result = Result.Failure(closeReason)
                }
            }
        }

        val next = redirectTo
        return if (next != null) {
            downloadHop(next, policy, transport, redirectsLeft - 1)
        } else {
            result
        }
    }
}
