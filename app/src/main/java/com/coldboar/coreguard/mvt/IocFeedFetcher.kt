package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.net.HardenedHttpsDownloader
import com.coldboar.coreguard.net.PublicIntelFeedPins
import java.io.File
import java.util.concurrent.Executor

/**
 * Downloads a remote IOC JSON feed over HTTPS and persists it to
 * [Context.filesDir]/ioc/, where [IocRepository] will pick it up on the next
 * [IocRepository.indicators] call.
 *
 * Production fetches require an immutable, SHA-256-pinned URL from
 * [PublicIntelFeedPins]. Integrity failures fail closed and leave any prior
 * verified-good feed file untouched.
 */
object IocFeedFetcher {

    private const val TAG = "IocFeedFetcher"
    private const val OUTPUT_FILE = "remote_feed.json"

    /**
     * Default Premium Nemesis signature refresh feed (STIX2 JSON).
     * Pinned to an immutable Amnesty Tech commit + SHA-256 digest.
     */
    val DEFAULT_FEED_URL: String = PublicIntelFeedPins.PEGASUS.url

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
        if (PublicIntelFeedPins.isFloatingBranchUrl(url)) {
            return FetchResult.Failure("Floating branch feed URLs are rejected")
        }
        val pin = PublicIntelFeedPins.pinFor(url)
            ?: return FetchResult.Failure("Unpinned feed URL rejected (integrity required)")

        val download = HardenedHttpsDownloader.download(
            url = pin.url,
            policy = HardenedHttpsDownloader.Policy(
                allowedHosts = PublicIntelFeedPins.ALLOWED_HOSTS,
                maxBytes = pin.maxBytes,
                expectedSha256Hex = pin.sha256Hex,
                acceptHeader = "application/json, */*",
                userAgent = "CoreGuard-IocFeedFetcher/1.0"
            )
        )
        return when (download) {
            is HardenedHttpsDownloader.Result.Failure -> {
                Log.w(TAG, "Feed fetch failed closed: ${download.reason}")
                FetchResult.Failure(download.reason)
            }
            is HardenedHttpsDownloader.Result.Success -> {
                val body = download.bytes.toString(Charsets.UTF_8)
                val indicators = IocParser.parse(body)
                if (indicators.isEmpty()) {
                    return FetchResult.Failure("Feed contained no recognisable indicators")
                }
                val dir = File(context.filesDir, "ioc").also { it.mkdirs() }
                File(dir, OUTPUT_FILE).writeText(body)
                IocRepository.invalidate()
                runCatching {
                    com.coldboar.coreguard.quilla.QuillaMemoryModule.invalidateLocalIntel()
                }
                Log.i(TAG, "Fetched ${indicators.size} indicators from ${pin.url}")
                FetchResult.Success(indicators.size)
            }
        }
    }
}
