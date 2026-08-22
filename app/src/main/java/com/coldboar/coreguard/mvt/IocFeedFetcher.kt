package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.net.HardenedHttpsDownloader
import com.coldboar.coreguard.net.PublicIntelFeedPins
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a remote IOC JSON feed over HTTPS and persists it via
 * [IocFeedStore] generation transactions, where [IocRepository] will pick it up
 * on the next [IocRepository.acquire] call.
 *
 * Production fetches require an immutable, SHA-256-pinned URL from
 * [PublicIntelFeedPins]. Integrity or persistence failures fail closed and leave
 * any prior verified-good generation untouched. Caches invalidate only after a
 * successful pointer commit.
 */
object IocFeedFetcher {

    private const val TAG = "IocFeedFetcher"

    val DEFAULT_FEED_URL: String = PublicIntelFeedPins.PEGASUS.url

    sealed class FetchResult {
        data class Success(val indicatorsLoaded: Int) : FetchResult()
        data class Failure(val message: String) : FetchResult()
    }

    /**
     * Fetches [url] on [executor] and invokes [onResult] **exactly once**,
     * including when [Executor.execute] rejects the task.
     */
    fun fetchAsync(
        context: Context,
        url: String = DEFAULT_FEED_URL,
        executor: Executor,
        onResult: (FetchResult) -> Unit
    ) {
        val delivered = AtomicBoolean(false)
        fun deliver(result: FetchResult) {
            if (delivered.compareAndSet(false, true)) {
                onResult(result)
            }
        }
        try {
            executor.execute {
                val result = try {
                    fetch(context, url)
                } catch (t: Throwable) {
                    FetchResult.Failure(t.message ?: "Unknown error")
                }
                deliver(result)
            }
        } catch (_: RejectedExecutionException) {
            deliver(FetchResult.Failure("Executor rejected task"))
        } catch (t: Throwable) {
            deliver(FetchResult.Failure(t.message ?: "Executor submit failed"))
        }
    }

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
                persistVerified(context, pin, download.bytes, body)
            }
        }
    }

    private fun persistVerified(
        context: Context,
        pin: PublicIntelFeedPins.Pin,
        bytes: ByteArray,
        body: String
    ): FetchResult = persistVerifiedInternal(context, pin, bytes, body)

    /**
     * Testable persist path: returns without invalidating when commit throws.
     * Production [persistVerified] is the sole caller of invalidate-after-commit.
     */
    internal fun persistVerifiedInternal(
        context: Context,
        pin: PublicIntelFeedPins.Pin,
        bytes: ByteArray,
        body: String,
        commit: (File, ByteArray, ByteArray) -> Unit = { dir, feed, meta ->
            IocFeedStore.commitGeneration(
                iocDir = dir,
                feedBytes = feed,
                metaBytes = meta,
                verify = { feedOnDisk, metaOnDisk ->
                    val parsed = JSONObject(metaOnDisk.toString(Charsets.UTF_8))
                    val sidecar = VerifiedRemoteMeta(
                        name = parsed.getString("name"),
                        url = parsed.getString("url"),
                        sha256Hex = parsed.getString("sha256").lowercase(),
                        commitPin = parsed.optString("commit", ""),
                        verifiedAtMs = parsed.optLong("verifiedAtMs", 0L)
                    )
                    CompiledPinValidator.validateAgainstCompiledPins(sidecar, feedOnDisk)
                        ?: throw IllegalStateException("Compiled pin validation failed after write")
                }
            )
        },
        onInvalidate: () -> Unit = {
            IocRepository.invalidate()
            runCatching {
                com.coldboar.coreguard.quilla.QuillaMemoryModule.invalidateLocalIntel()
            }
        }
    ): FetchResult {
        val dir = try {
            File(context.filesDir, "ioc").also { target ->
                if (!target.exists() && !target.mkdirs()) {
                    return FetchResult.Failure("Failed to create IOC directory")
                }
                if (!target.isDirectory) {
                    return FetchResult.Failure("IOC path is not a directory")
                }
            }
        } catch (e: Exception) {
            return FetchResult.Failure("Directory error: ${e.message}")
        }

        val commitPin = extractCommitPin(pin.url)
        val metaJson = JSONObject()
            .put("name", pin.name)
            .put("url", pin.url)
            .put("sha256", pin.sha256Hex.lowercase())
            .put("commit", commitPin)
            .put("verifiedAtMs", System.currentTimeMillis())
            .toString()
        val metaBytes = metaJson.toByteArray(Charsets.UTF_8)

        return try {
            commit(dir, bytes, metaBytes)
            // Invalidate only after pointer commit succeeded.
            onInvalidate()
            val indicators = IocParser.parse(body)
            Log.i(TAG, "Fetched ${indicators.size} indicators from ${pin.url}")
            FetchResult.Success(indicators.size)
        } catch (e: Exception) {
            Log.w(TAG, "Persist failed; prior verified generation preserved: ${e.message}")
            FetchResult.Failure("Persist failed: ${e.message}")
        }
    }

    /** Best-effort commit id from a raw.githubusercontent.com/.../<sha>/... URL. */
    internal fun extractCommitPin(url: String): String {
        val parts = url.removePrefix("https://raw.githubusercontent.com/").split('/')
        // owner/repo/<ref>/path...
        return parts.getOrNull(2)?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }.orEmpty()
    }
}
