package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executor

/**
 * Downloads a remote IOC JSON feed over HTTPS and persists it to
 * [Context.filesDir]/ioc/, where [IocRepository] will pick it up on the next
 * [IocRepository.indicators] call.
 *
 * Uses [HttpURLConnection] – no third-party network library needed. A 2 MB cap
 * prevents runaway memory use on malformed feeds. After a successful save the
 * [IocRepository] cache is invalidated so the new indicators are loaded on the
 * next scan.
 */
object IocFeedFetcher {

    private const val TAG = "IocFeedFetcher"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB sanity cap
    private const val OUTPUT_FILE = "remote_feed.json"

    /**
     * Default Premium Nemesis signature refresh feed (STIX2 JSON).
     * Amnesty Tech NSO/Pegasus public indicators, pinned to an immutable
     * upstream revision. The body digest below is checked before the feed is
     * parsed or persisted, so a compromised delivery path cannot silently
     * replace local threat intelligence.
     */
    const val DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2021-07-18_nso/pegasus.stix2"
    private const val DEFAULT_FEED_SHA256 =
        "df1bcaa78abc7b85781b1ebc2daa3cc225371e2024d9ef96e84f80f927256586"

    sealed class FetchResult {
        /** Feed downloaded and saved; [IocRepository] cache has been invalidated. */
        data class Success(val indicatorsLoaded: Int) : FetchResult()

        /** The request or file I/O failed. */
        data class Failure(val message: String) : FetchResult()
    }

    /**
     * Fetches [url] on [executor] and delivers [onResult] back on the same
     * executor thread. Callers that need to update the UI must post back to the
     * main thread themselves (e.g. via a [android.os.Handler]).
     *
     * Prefer [fetch] from a coroutine context when possible; this overload is
     * retained for legacy call sites.
     */
    fun fetchAsync(
        context: Context,
        url: String = DEFAULT_FEED_URL,
        executor: Executor,
        onResult: (FetchResult) -> Unit
    ) {
        executor.execute { onResult(fetch(context, url)) }
    }

    /**
     * Synchronously downloads and saves a remote IOC feed. Intended to be called
     * from a coroutine on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun fetch(context: Context, url: String = DEFAULT_FEED_URL): FetchResult {
        // The production scanner accepts only the reviewed, immutable feed above.
        // Supporting arbitrary URL input here would bypass the digest trust boundary.
        if (url != DEFAULT_FEED_URL) {
            return FetchResult.Failure("Unverified threat feed URL rejected")
        }
        // HttpURLConnection on Android uses the system SSL context, which enforces
        // hostname verification and certificate chain validation by default.
        // Redirects are disabled: we validate each hop ourselves to ensure we never
        // follow a redirect to a non-HTTPS URL.
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, */*")
            instanceFollowRedirects = false
        }
        return try {
            connection.connect()

            val status = connection.responseCode
            // A verified immutable feed must not redirect; accepting a redirect
            // would allow the verified URL to become an unverified trust boundary.
            if (status in 301..308) {
                return FetchResult.Failure("Threat feed redirect rejected")
            }
            if (status !in 200..299) {
                return FetchResult.Failure("HTTP $status from server")
            }

            // Check Content-Length first to avoid streaming a large feed unnecessarily.
            val contentLength = connection.contentLength
            if (contentLength > MAX_BYTES) {
                return FetchResult.Failure("Feed too large ($contentLength bytes)")
            }

            // Stream into a bounded buffer so we never allocate more than MAX_BYTES
            // before validating size. readBytes() buffers the full response before any
            // size check is possible, risking OOM on a malformed or malicious server.
            val buffer = ByteArray(8_192)
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (out.size() + read > MAX_BYTES) {
                        return FetchResult.Failure("Feed too large (>${MAX_BYTES} bytes)")
                    }
                    out.write(buffer, 0, read)
                }
            }
            val bodyBytes = out.toByteArray()
            if (sha256Hex(bodyBytes) != DEFAULT_FEED_SHA256) {
                return FetchResult.Failure("Threat feed integrity check failed")
            }
            val body = bodyBytes.toString(Charsets.UTF_8)

            // Validate before saving – reject feeds we can't parse at all.
            val indicators = IocParser.parse(body)
            if (indicators.isEmpty()) {
                return FetchResult.Failure("Feed contained no recognisable indicators")
            }

            // Persist the verified feed atomically so an interrupted update does
            // not overwrite the last verified-good local intelligence set.
            val dir = File(context.filesDir, "ioc").also { it.mkdirs() }
            val target = File(dir, OUTPUT_FILE)
            val temporary = File.createTempFile("remote_feed_", ".tmp", dir)
            try {
                temporary.writeBytes(bodyBytes)
                if (!temporary.renameTo(target)) {
                    return FetchResult.Failure("Could not atomically save verified threat feed")
                }
            } finally {
                temporary.delete()
            }

            IocRepository.invalidate()
            // Quilla correlator should re-read the refreshed on-device inventory.
            runCatching {
                com.coldboar.coreguard.quilla.QuillaMemoryModule.invalidateLocalIntel()
            }
            Log.i(TAG, "Fetched ${indicators.size} indicators from $url")
            FetchResult.Success(indicators.size)
        } catch (e: IOException) {
            Log.w(TAG, "Feed fetch failed (I/O): ${e.message}")
            FetchResult.Failure(e.message ?: "Network error")
        } catch (e: Exception) {
            Log.w(TAG, "Feed fetch failed: ${e.message}")
            FetchResult.Failure(e.message ?: "Unknown error")
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
}
