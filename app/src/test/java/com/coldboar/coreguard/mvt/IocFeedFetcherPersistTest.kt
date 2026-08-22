package com.coldboar.coreguard.mvt

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IocFeedFetcherPersistTest {

    @get:Rule
    val tmp = TemporaryFolder()

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
        val executor = Executors.newSingleThreadExecutor()
        IocFeedFetcher.fetchAsync(
            context = context,
            url = "http://insecure.example/feed.json",
            executor = executor
        ) {
            calls.incrementAndGet()
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, calls.get())
    }
}
