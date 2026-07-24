package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
     * The official MVT / Amnesty Tech Pegasus indicators (STIX2 JSON).
     * Sourced from https://github.com/mvt-project/mvt-indicators
     */
    const val DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators/pegasus.stix2"

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
     */
    fun fetchAsync(
        context: Context,
        url: String = DEFAULT_FEED_URL,
        executor: Executor,
        onResult: (FetchResult) -> Unit
    ) {
        executor.execute { onResult(fetch(context, url)) }
    }

    private fun fetch(context: Context, url: String): FetchResult {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json, */*")
                connect()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                return FetchResult.Failure("HTTP $status from server")
            }

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) {
                return FetchResult.Failure("Feed too large (${bytes.size} bytes)")
            }
            val body = bytes.toString(Charsets.UTF_8)

            // Validate before saving – reject feeds we can't parse at all.
            val indicators = IocParser.parse(body)
            if (indicators.isEmpty()) {
                return FetchResult.Failure("Feed contained no recognisable indicators")
            }

            // Persist to filesDir/ioc/remote_feed.json
            val dir = File(context.filesDir, "ioc").also { it.mkdirs() }
            File(dir, OUTPUT_FILE).writeText(body)

            IocRepository.invalidate()
            Log.i(TAG, "Fetched ${indicators.size} indicators from $url")
            FetchResult.Success(indicators.size)
        } catch (e: Exception) {
            Log.w(TAG, "Feed fetch failed: ${e.message}")
            FetchResult.Failure(e.message ?: "Unknown error")
        }
    }
}
