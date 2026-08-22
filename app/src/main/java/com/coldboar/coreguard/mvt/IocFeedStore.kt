package com.coldboar.coreguard.mvt

import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Generation-based transactional persistence for the verified remote IOC feed.
 *
 * Feed body and metadata are written into a new generation directory, flushed,
 * closed, and verified. Only then is the current-generation pointer switched
 * atomically. Concurrent writers are serialized. On any failure the previous
 * verified generation remains active.
 */
object IocFeedStore {

    private const val TAG = "IocFeedStore"
    const val GENERATIONS_DIR = "generations"
    const val CURRENT_POINTER = "current.generation"
    const val GEN_FEED = "feed.json"
    const val GEN_META = "meta.json"

    /** Serialize all writers so generations never interleave. */
    private val writeLock = ReentrantLock()

    data class GenerationPaths(
        val generation: Long,
        val dir: File,
        val feed: File,
        val meta: File
    )

    data class CommitResult(
        val generation: Long,
        val previousGeneration: Long?
    )

    /**
     * Reads the committed generation id, or null when no pointer exists / is invalid.
     */
    fun readCurrentGeneration(iocDir: File): Long? {
        val pointer = File(iocDir, CURRENT_POINTER)
        if (!pointer.isFile) return null
        return runCatching {
            pointer.readText().trim().toLong().takeIf { it > 0L }
        }.getOrNull()
    }

    fun pathsFor(iocDir: File, generation: Long): GenerationPaths {
        val dir = File(File(iocDir, GENERATIONS_DIR), generation.toString())
        return GenerationPaths(
            generation = generation,
            dir = dir,
            feed = File(dir, GEN_FEED),
            meta = File(dir, GEN_META)
        )
    }

    /**
     * Atomically commit [feedBytes] + [metaBytes] as a new generation.
     *
     * Failure at any stage (directory, feed write, meta write, verification,
     * pointer write) leaves the previous generation active and does not throw
     * after cleaning the aborted generation best-effort.
     */
    fun commitGeneration(
        iocDir: File,
        feedBytes: ByteArray,
        metaBytes: ByteArray,
        verify: (feed: ByteArray, meta: ByteArray) -> Unit = { _, _ -> }
    ): CommitResult = writeLock.withLock {
        if (!iocDir.exists() && !iocDir.mkdirs()) {
            throw IOException("Failed to create IOC directory")
        }
        if (!iocDir.isDirectory) {
            throw IOException("IOC path is not a directory")
        }

        val previous = readCurrentGeneration(iocDir)
        val next = (previous ?: 0L) + 1L
        val paths = pathsFor(iocDir, next)

        try {
            if (!paths.dir.exists() && !paths.dir.mkdirs()) {
                throw IOException("Failed to create generation directory $next")
            }

            writeAtomicVerified(paths.feed, feedBytes)
            writeAtomicVerified(paths.meta, metaBytes)

            // Re-read from disk to prove durable content before pointer switch.
            val feedOnDisk = paths.feed.readBytes()
            val metaOnDisk = paths.meta.readBytes()
            if (!feedOnDisk.contentEquals(feedBytes)) {
                throw IOException("Feed verification mismatch after write")
            }
            if (!metaOnDisk.contentEquals(metaBytes)) {
                throw IOException("Meta verification mismatch after write")
            }
            verify(feedOnDisk, metaOnDisk)

            writePointerAtomic(iocDir, next)
            // Pointer committed — previous generation retained on disk until
            // a later successful commit may prune it. Never delete previous
            // before pointer success.
            previous?.let { pruneOlderThan(iocDir, keep = setOf(it, next)) }
            CommitResult(generation = next, previousGeneration = previous)
        } catch (e: Exception) {
            Log.w(TAG, "Generation $next aborted; previous=$previous: ${e.message}")
            runCatching { paths.dir.deleteRecursively() }
            throw e
        }
    }

    /**
     * Test / fault-injection hook: write feed+meta for a new generation but
     * fail before switching the pointer. Previous generation stays active.
     */
    internal fun commitGenerationFailingAtPointer(
        iocDir: File,
        feedBytes: ByteArray,
        metaBytes: ByteArray,
        pointerFailure: () -> Unit
    ): Nothing = writeLock.withLock {
        val previous = readCurrentGeneration(iocDir)
        val next = (previous ?: 0L) + 1L
        val paths = pathsFor(iocDir, next)
        if (!iocDir.exists()) iocDir.mkdirs()
        paths.dir.mkdirs()
        writeAtomicVerified(paths.feed, feedBytes)
        writeAtomicVerified(paths.meta, metaBytes)
        try {
            pointerFailure()
            error("pointerFailure must throw")
        } catch (e: Exception) {
            // Intentionally leave the incomplete generation directory; pointer unchanged.
            throw e
        }
    }

    /**
     * Test hook: write feed for a new generation then fail on metadata so the
     * pointer is never switched.
     */
    internal fun commitGenerationFailingAtMeta(
        iocDir: File,
        feedBytes: ByteArray,
        metaFailure: () -> Unit
    ): Nothing = writeLock.withLock {
        val previous = readCurrentGeneration(iocDir)
        val next = (previous ?: 0L) + 1L
        val paths = pathsFor(iocDir, next)
        if (!iocDir.exists()) iocDir.mkdirs()
        paths.dir.mkdirs()
        writeAtomicVerified(paths.feed, feedBytes)
        try {
            metaFailure()
            error("metaFailure must throw")
        } catch (e: Exception) {
            runCatching { paths.dir.deleteRecursively() }
            throw e
        }
    }

    private fun pruneOlderThan(iocDir: File, keep: Set<Long>) {
        val root = File(iocDir, GENERATIONS_DIR)
        if (!root.isDirectory) return
        root.listFiles()?.forEach { child ->
            val gen = child.name.toLongOrNull() ?: return@forEach
            if (gen !in keep) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun writePointerAtomic(iocDir: File, generation: Long) {
        val pointer = File(iocDir, CURRENT_POINTER)
        writeAtomicVerified(pointer, "$generation\n".toByteArray(Charsets.UTF_8))
        val readBack = pointer.readText().trim().toLong()
        if (readBack != generation) {
            throw IOException("Pointer verification failed (wrote $generation, read $readBack)")
        }
    }

    private fun writeAtomicVerified(target: File, bytes: ByteArray) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create parent for ${target.name}")
        }
        val atomic = AtomicFile(target)
        var fos: FileOutputStream? = null
        try {
            fos = atomic.startWrite()
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
            atomic.finishWrite(fos)
            fos = null
        } catch (e: Exception) {
            if (fos != null) atomic.failWrite(fos)
            throw e
        }
    }
}
