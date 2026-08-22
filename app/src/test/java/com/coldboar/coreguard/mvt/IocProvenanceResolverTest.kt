package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IocProvenanceResolverTest {

    @Test
    fun `verified remote includes commit and sha256 in feedVersion`() {
        val meta = VerifiedRemoteMeta(
            name = "Amnesty Pegasus (NSO)",
            url = "https://example/pegasus.stix2",
            sha256Hex = "abc123",
            commitPin = "3d8f248a0d015f183724ae7d096a5c46a8bb5fc7",
            verifiedAtMs = 1L
        )
        val snap = IocProvenanceResolver.resolve(
            bundledCount = 0,
            verifiedRemoteCount = 10,
            userImportedCount = 0,
            usedFallback = false,
            loadedAtMs = 99L,
            verifiedMeta = meta
        )
        assertEquals(IocProvenanceClass.VERIFIED_REMOTE, snap.provenanceClass)
        assertTrue(snap.feedVersion!!.contains("commit=3d8f248a"))
        assertTrue(snap.feedVersion!!.contains("sha256=abc123"))
        assertTrue(snap.feedAuthenticity.startsWith("VERIFIED_REMOTE"))
    }

    @Test
    fun `bundled never inherits remote verification label`() {
        val snap = IocProvenanceResolver.resolve(5, 0, 0, false, 1L, null)
        assertEquals(IocProvenanceClass.BUNDLED, snap.provenanceClass)
        assertNull(snap.feedVersion)
        assertFalse(snap.feedAuthenticity.contains("VERIFIED_REMOTE"))
    }

    @Test
    fun `user imported is not cryptographically verified`() {
        val snap = IocProvenanceResolver.resolve(0, 0, 3, false, 1L, null)
        assertEquals(IocProvenanceClass.USER_IMPORTED, snap.provenanceClass)
        assertTrue(snap.feedAuthenticity.contains("USER_IMPORTED"))
        assertNull(snap.feedVersion)
    }

    @Test
    fun `mixed sources do not claim verified remote`() {
        val snap = IocProvenanceResolver.resolve(2, 4, 1, false, 1L, null)
        assertEquals(IocProvenanceClass.MIXED, snap.provenanceClass)
        assertNull(snap.feedVersion)
        assertTrue(snap.feedAuthenticity.contains("MIXED"))
        assertFalse(snap.feedAuthenticity.startsWith("VERIFIED_REMOTE"))
    }

    @Test
    fun `fallback when no other IOCs`() {
        val snap = IocProvenanceResolver.resolve(0, 0, 0, true, 1L, null)
        assertEquals(IocProvenanceClass.FALLBACK, snap.provenanceClass)
        assertTrue(snap.feedAuthenticity.contains("FALLBACK"))
    }

    @Test
    fun `unavailable when empty and no fallback`() {
        val snap = IocProvenanceResolver.resolve(0, 0, 0, false, 0L, null)
        assertEquals(IocProvenanceClass.UNAVAILABLE, snap.provenanceClass)
    }
}
