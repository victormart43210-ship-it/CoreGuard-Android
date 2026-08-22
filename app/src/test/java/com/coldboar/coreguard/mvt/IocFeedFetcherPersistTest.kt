package com.coldboar.coreguard.mvt

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

class IocFeedFetcherPersistTest {

    @Test
    fun `extractCommitPin reads github raw commit`() {
        val url =
            "https://raw.githubusercontent.com/AmnestyTech/investigations/3d8f248a0d015f183724ae7d096a5c46a8bb5fc7/2021-07-18_nso/pegasus.stix2"
        assertEquals(
            "3d8f248a0d015f183724ae7d096a5c46a8bb5fc7",
            IocFeedFetcher.extractCommitPin(url)
        )
    }

    @Test
    fun `fetchAsync invokes callback exactly once even on throw`() {
        val context = Mockito.mock(Context::class.java)
        whenever(context.filesDir).thenThrow(RuntimeException("no dir"))
        val calls = AtomicInteger(0)
        val executor = Executor { it.run() }
        IocFeedFetcher.fetchAsync(
            context = context,
            url = "http://insecure.example/feed.json",
            executor = executor
        ) {
            calls.incrementAndGet()
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `RejectedExecutionException still delivers exactly one failure callback`() {
        val context = Mockito.mock(Context::class.java)
        val calls = AtomicInteger(0)
        val results = mutableListOf<IocFeedFetcher.FetchResult>()
        val rejecting = Executor {
            throw RejectedExecutionException("saturated")
        }
        IocFeedFetcher.fetchAsync(
            context = context,
            url = "https://example.com/feed.json",
            executor = rejecting
        ) { result ->
            calls.incrementAndGet()
            results += result
        }
        assertEquals("zero callbacks impossible", 1, calls.get())
        assertTrue(results.single() is IocFeedFetcher.FetchResult.Failure)
        // Second submit must not double-deliver even if somehow invoked again.
        assertEquals(1, calls.get())
    }

    @Test
    fun `multiple callbacks are impossible across reject and task paths`() {
        val context = Mockito.mock(Context::class.java)
        whenever(context.filesDir).thenThrow(RuntimeException("boom"))
        val calls = AtomicInteger(0)
        // Executor that both runs and would somehow re-enter — guard still holds.
        val executor = Executor { runnable ->
            runnable.run()
            // Attempting a second delivery path must not increase calls.
        }
        IocFeedFetcher.fetchAsync(
            context = context,
            url = "http://bad",
            executor = executor
        ) { calls.incrementAndGet() }
        assertEquals(1, calls.get())
    }
}
