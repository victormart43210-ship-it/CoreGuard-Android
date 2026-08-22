package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DnsForwardResultTest {

    @Test
    fun `forward outcomes are distinct and never upgrade reject to forwarded`() {
        assertNotEquals(DnsForwardResult.FORWARDED, DnsForwardResult.REJECTED)
        assertNotEquals(DnsForwardResult.FORWARDED, DnsForwardResult.UNAVAILABLE)
        assertEquals("FORWARDED", DnsForwardResult.FORWARDED.name)
        assertEquals("REJECTED", DnsForwardResult.REJECTED.name)
        assertEquals("UNAVAILABLE", DnsForwardResult.UNAVAILABLE.name)
    }
}
