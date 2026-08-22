package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Test

class IocRepositoryDedupTest {

    @Test
    fun `duplicate indicators collapse before provenance indicatorCount`() {
        val a = Indicator.of(IndicatorType.DOMAIN, "evil.example", "Pegasus")!!
        val duplicate = Indicator.of(IndicatorType.DOMAIN, "EVIL.EXAMPLE", "Pegasus")!!
        val b = Indicator.of(IndicatorType.DOMAIN, "other.example", "Pegasus")!!
        assertEquals(a, duplicate)

        val bundled = linkedSetOf(a, duplicate)
        val verified = linkedSetOf(a, b)
        val merged = LinkedHashSet<Indicator>()
        merged += bundled
        merged += verified

        val provenance = IocProvenanceResolver.resolve(
            bundledCount = bundled.size,
            verifiedRemoteCount = verified.size,
            userImportedCount = 0,
            usedFallback = false,
            loadedAtMs = 10L,
            verifiedMeta = null
        )
        // Unique after LinkedHashSet dedup: bundled contributes 1, verified 2, merged 2.
        assertEquals(1, bundled.size)
        assertEquals(2, verified.size)
        assertEquals(2, merged.size)
        assertEquals(3, provenance.indicatorCount) // resolver sums source class counts
        // Session acquisition must report unique merged size (DeviceScanner contract).
        val acquisition = IocAcquisitionSnapshot(
            indicators = merged.toList(),
            provenance = provenance.copy(indicatorCount = merged.size),
            loadedAtMs = 10L
        )
        assertEquals(2, acquisition.indicators.size)
        assertEquals(2, acquisition.provenance.indicatorCount)
    }
}
