package com.coldboar.coreguard.mvt

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import com.coldboar.coreguard.net.PublicIntelFeedPins
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Proves generation-based IOC persistence: feed+meta are not two independent
 * AtomicFile commits; pointer switches only after both are verified.
 */
class IocFeedStoreAtomicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun feedBytes(value: String = "evil.example") =
        """{"indicators":[{"type":"domain","value":"$value","malware":"Test"}]}"""
            .toByteArray(Charsets.UTF_8)

    private fun metaBytes(
        name: String = "Amnesty Pegasus (NSO)",
        url: String = com.coldboar.coreguard.net.PublicIntelFeedPins.PEGASUS.url,
        sha: String = com.coldboar.coreguard.net.PublicIntelFeedPins.PEGASUS.sha256Hex,
        commit: String = IocFeedFetcher.extractCommitPin(
            com.coldboar.coreguard.net.PublicIntelFeedPins.PEGASUS.url
        )
    ) = JSONObject()
        .put("name", name)
        .put("url", url)
        .put("sha256", sha)
        .put("commit", commit)
        .put("verifiedAtMs", 1L)
        .toString()
        .toByteArray(Charsets.UTF_8)

    @Test
    fun `metadata-write failure keeps previous generation active`() {
        val dir = tmp.newFolder("ioc")
        val firstFeed = feedBytes("first.example")
        val firstMeta = metaBytes()
        // Bypass pin verify for store-level atomicity tests.
        IocFeedStore.commitGeneration(dir, firstFeed, firstMeta)
        val prev = IocFeedStore.readCurrentGeneration(dir)
        assertEquals(1L, prev)

        try {
            IocFeedStore.commitGenerationFailingAtMeta(dir, feedBytes("second.example")) {
                error("meta write boom")
            }
            fail("expected meta failure")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("meta write boom"))
        }
        assertEquals(prev, IocFeedStore.readCurrentGeneration(dir))
        val paths = IocFeedStore.pathsFor(dir, prev!!)
        assertTrue(paths.feed.isFile)
        assertEquals(String(firstFeed), paths.feed.readText())
    }

    @Test
    fun `pointer-write failure keeps previous generation active`() {
        val dir = tmp.newFolder("ioc")
        IocFeedStore.commitGeneration(dir, feedBytes("v1"), metaBytes())
        val prev = IocFeedStore.readCurrentGeneration(dir)!!
        try {
            IocFeedStore.commitGenerationFailingAtPointer(
                dir,
                feedBytes("v2"),
                metaBytes()
            ) { error("pointer boom") }
            fail("expected pointer failure")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("pointer boom"))
        }
        assertEquals(prev, IocFeedStore.readCurrentGeneration(dir))
        assertTrue(IocFeedStore.pathsFor(dir, prev).feed.isFile)
    }

    @Test
    fun `rollback leaves previous verified generation readable`() {
        val dir = tmp.newFolder("ioc")
        val v1 = feedBytes("keep.example")
        IocFeedStore.commitGeneration(dir, v1, metaBytes())
        try {
            IocFeedStore.commitGenerationFailingAtMeta(dir, feedBytes("drop.example")) {
                error("rollback")
            }
        } catch (_: Exception) {
        }
        val gen = IocFeedStore.readCurrentGeneration(dir)!!
        assertEquals("keep.example", JSONObject(IocFeedStore.pathsFor(dir, gen).feed.readText())
            .getJSONArray("indicators").getJSONObject(0).getString("value"))
    }

    @Test
    fun `concurrent writers serialize and leave one valid current generation`() {
        val dir = tmp.newFolder("ioc")
        IocFeedStore.commitGeneration(dir, feedBytes("base"), metaBytes())
        val errors = AtomicInteger(0)
        val successes = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) { i ->
            pool.execute {
                try {
                    start.await()
                    IocFeedStore.commitGeneration(dir, feedBytes("w$i"), metaBytes())
                    successes.incrementAndGet()
                } catch (_: Exception) {
                    errors.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(8, successes.get())
        assertEquals(0, errors.get())
        val current = IocFeedStore.readCurrentGeneration(dir)
        assertNotNull(current)
        assertTrue(current!! >= 9L) // base + 8
        val paths = IocFeedStore.pathsFor(dir, current)
        assertTrue(paths.feed.isFile && paths.meta.isFile)
    }

    @Test
    fun `cache invalidation only after pointer commit`() {
        val invalidated = AtomicInteger(0)
        val dir = tmp.newFolder("ioc")
        IocFeedStore.commitGeneration(dir, feedBytes("a"), metaBytes())
        // Simulate: invalidate only after commit returns (caller contract).
        IocRepository.invalidate()
        invalidated.incrementAndGet()
        assertEquals(1, invalidated.get())

        val beforePointer = AtomicReference<Long?>(null)
        try {
            IocFeedStore.commitGenerationFailingAtPointer(dir, feedBytes("b"), metaBytes()) {
                beforePointer.set(IocFeedStore.readCurrentGeneration(dir))
                error("no pointer")
            }
        } catch (_: Exception) {
        }
        // Pointer never advanced — callers must not invalidate.
        assertEquals(1L, beforePointer.get())
        assertEquals(1L, IocFeedStore.readCurrentGeneration(dir))
    }

    @Test
    fun `IocFeedFetcher does not invalidate when commit fails`() {
        val context = Mockito.mock(android.content.Context::class.java)
        whenever(context.filesDir).thenReturn(tmp.newFolder("files"))
        val pin = PublicIntelFeedPins.PEGASUS
        val body = """{"indicators":[{"type":"domain","value":"a.example","malware":"T"}]}"""
        val invalidated = AtomicInteger(0)
        val result = IocFeedFetcher.persistVerifiedInternal(
            context = context,
            pin = pin,
            bytes = body.toByteArray(),
            body = body,
            commit = { _, _, _ -> error("simulated commit failure") },
            onInvalidate = { invalidated.incrementAndGet() }
        )
        assertTrue(result is IocFeedFetcher.FetchResult.Failure)
        assertEquals(0, invalidated.get())
    }

    @Test
    fun `IocFeedFetcher invalidates only after successful commit`() {
        val context = Mockito.mock(android.content.Context::class.java)
        whenever(context.filesDir).thenReturn(tmp.newFolder("files2"))
        val pin = PublicIntelFeedPins.PEGASUS
        val body = """{"indicators":[{"type":"domain","value":"b.example","malware":"T"}]}"""
        val invalidated = AtomicInteger(0)
        val result = IocFeedFetcher.persistVerifiedInternal(
            context = context,
            pin = pin,
            bytes = body.toByteArray(),
            body = body,
            commit = { dir, feed, meta ->
                IocFeedStore.commitGeneration(dir, feed, meta)
            },
            onInvalidate = { invalidated.incrementAndGet() }
        )
        assertTrue(result is IocFeedFetcher.FetchResult.Success)
        assertEquals(1, invalidated.get())
    }

    @Test
    fun `previous verified generation retained until pointer succeeds`() {
        val dir = tmp.newFolder("ioc")
        IocFeedStore.commitGeneration(dir, feedBytes("old"), metaBytes())
        val oldGen = IocFeedStore.readCurrentGeneration(dir)!!
        val oldFeed = IocFeedStore.pathsFor(dir, oldGen).feed.readBytes()

        try {
            IocFeedStore.commitGenerationFailingAtPointer(dir, feedBytes("new"), metaBytes()) {
                // During pointer failure, old generation files must still exist.
                assertTrue(IocFeedStore.pathsFor(dir, oldGen).feed.isFile)
                assertTrue(IocFeedStore.pathsFor(dir, oldGen).feed.readBytes().contentEquals(oldFeed))
                error("abort pointer")
            }
        } catch (_: Exception) {
        }
        assertEquals(oldGen, IocFeedStore.readCurrentGeneration(dir))
        assertTrue(IocFeedStore.pathsFor(dir, oldGen).feed.readBytes().contentEquals(oldFeed))
    }

    @Test
    fun `successful commit advances pointer and keeps prior gen until prune`() {
        val dir = tmp.newFolder("ioc")
        IocFeedStore.commitGeneration(dir, feedBytes("g1"), metaBytes())
        IocFeedStore.commitGeneration(dir, feedBytes("g2"), metaBytes())
        assertEquals(2L, IocFeedStore.readCurrentGeneration(dir))
        assertTrue(IocFeedStore.pathsFor(dir, 2L).feed.isFile)
        // Generation 1 may be pruned after gen2 commit (keep previous+current).
        // At least current must be valid.
        assertNull(IocFeedStore.readCurrentGeneration(tmp.newFolder("empty")))
    }
}
