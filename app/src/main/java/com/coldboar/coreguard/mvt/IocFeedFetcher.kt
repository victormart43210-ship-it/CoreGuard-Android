package com.coldboar.coreguard.mvt

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import com.coldboar.coreguard.net.HardenedHttpsDownloader
import com.coldboar.coreguard.net.PublicIntelFeedPins
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a remote IOC JSON feed over HTTPS and persists it to
 * [Context.filesDir]/ioc/, where [IocRepository] will pick it up on the next
 * [IocRepository.indicators] call.
 *
 * Production fetches require an immutable, SHA-256-pinned URL from
 * [PublicIntelFeedPins]. Integrity or persistence failures fail closed and leave
 * any prior verified-good feed file untouched. Caches invalidate only after a
 * successful atomic commit.
 */
object IocFeedFetcher {

    private const val TAG = "IocFeedFetcher"

    val DEFAULT_FEED_URL: String = PublicIntelFeedPins.PEGASUS.url

    sealed class FetchResult {
        data class Success(val indicatorsLoaded: Int) : FetchResult()
        data class Failure(val message: String) : FetchResult()
    }

    /**
     * Fetches [url] on [executor] and invokes [onResult] **exactly once**.
     */
    fun fetchAsync(
        context: Context,
        url: String = DEFAULT_FEED_URL,
        executor: Executor,
        onResult: (FetchResult) -> Unit
    ) {
        executor.execute {
            val delivered = AtomicBoolean(false)
            val result = try {
                fetch(context, url)
            } catch (t: Throwable) {
                FetchResult.Failure(t.message ?: "Unknown error")
            }
            if (delivered.compareAndSet(false, true)) {
                onResult(result)
            }
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

        val feedFile = File(dir, IocRepository.REMOTE_FEED_FILE)
        val metaFile = File(dir, IocRepository.REMOTE_FEED_META_FILE)
        val commitPin = extractCommitPin(pin.url)
        val metaJson = JSONObject()
            .put("name", pin.name)
            .put("url", pin.url)
            .put("sha256", pin.sha256Hex.lowercase())
            .put("commit", commitPin)
            .put("verifiedAtMs", System.currentTimeMillis())
            .toString()

        return try {
            writeAtomic(feedFile, bytes)
            try {
                writeAtomic(metaFile, metaJson.toByteArray(Charsets.UTF_8))
            } catch (metaErr: Exception) {
                // Meta failed after feed write — remove unverifiable feed to avoid
                // mis-labeling as VERIFIED_REMOTE; prior verified pair left if rename failed earlier.
                runCatching { feedFile.delete() }
                return FetchResult.Failure("Meta persistence failed: ${metaErr.message}")
            }
            IocRepository.invalidate()
            runCatching {
                com.coldboar.coreguard.quilla.QuillaMemoryModule.invalidateLocalIntel()
            }
            val indicators = IocParser.parse(body)
            Log.i(TAG, "Fetched ${indicators.size} indicators from ${pin.url}")
            FetchResult.Success(indicators.size)
        } catch (e: Exception) {
            Log.w(TAG, "Persist failed; prior verified feed preserved: ${e.message}")
            FetchResult.Failure("Persist failed: ${e.message}")
        }
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        var fos: FileOutputStream? = null
        try {
            fos = atomic.startWrite()
            fos.write(bytes)
            atomic.finishWrite(fos)
            fos = null
        } catch (e: Exception) {
            if (fos != null) atomic.failWrite(fos)
            throw e
        }
    }

    /** Best-effort commit id from a raw.githubusercontent.com/.../<sha>/... URL. */
    internal fun extractCommitPin(url: String): String {
        val parts = url.removePrefix("https://raw.githubusercontent.com/").split('/')
        // owner/repo/<ref>/path...
        return parts.getOrNull(2)?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }.orEmpty()
    }
}
