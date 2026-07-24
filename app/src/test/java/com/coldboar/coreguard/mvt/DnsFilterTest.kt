package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterTest {

    /** Builds a minimal DNS query message for [domain] (A record, IN class). */
    private fun query(domain: String, id: Int = 0x1234): ByteArray {
        val header = byteArrayOf(
            (id ushr 8).toByte(), (id and 0xFF).toByte(),
            0x01, 0x00,             // flags: standard query, RD=1
            0x00, 0x01,             // qdcount = 1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val qname = buildList {
            domain.split('.').forEach { label ->
                add(label.length.toByte())
                label.forEach { add(it.code.toByte()) }
            }
            add(0.toByte())
        }.toByteArray()
        val qtail = byteArrayOf(0x00, 0x01, 0x00, 0x01) // QTYPE A, QCLASS IN
        return header + qname + qtail
    }

    @Test
    fun `parses query name`() {
        assertEquals("c2.evil.com", DnsMessage.parseQueryName(query("c2.evil.com")))
        assertEquals("free247downloads.com", DnsMessage.parseQueryName(query("Free247Downloads.com")))
    }

    @Test
    fun `parse returns null for junk`() {
        assertNull(DnsMessage.parseQueryName(ByteArray(4)))
    }

    @Test
    fun `nxdomain response echoes id and sets response + rcode3`() {
        val q = query("evil.com", id = 0xABCD)
        val resp = DnsMessage.buildNxDomainResponse(q)
        // Transaction id preserved.
        assertEquals(0xAB.toByte(), resp[0])
        assertEquals(0xCD.toByte(), resp[1])
        // QR bit set.
        assertTrue(resp[2].toInt() and 0x80 != 0)
        // RCODE == 3 (NXDOMAIN).
        assertEquals(3, resp[3].toInt() and 0x0F)
        // No answers.
        assertEquals(0, resp[6].toInt())
        assertEquals(0, resp[7].toInt())
    }

    @Test
    fun `nxdomain response preserves opcode from query`() {
        // Construct a query with a non-zero OPCODE (e.g. OPCODE=1, inverse query).
        // Standard query flag byte: QR=0, OPCODE=0001, AA=0, TC=0, RD=1 → 0b00001001 = 0x09
        val q = query("example.com", id = 0x0001).also { it[2] = 0x09.toByte() }
        val resp = DnsMessage.buildNxDomainResponse(q)
        // QR=1, OPCODE=0001 preserved, RD=1 → 0b10001001 = 0x89
        assertEquals(0x89.toByte(), resp[2])
        // RA=1, RCODE=3 → 0x83
        assertEquals(0x83.toByte(), resp[3])
    }

    @Test
    fun `domain blocker uses matcher`() {
        val blocker = DomainBlocker(
            IocMatcher(listOf(Indicator(IndicatorType.DOMAIN, "evil.com", "Pegasus")))
        )
        assertTrue(blocker.isBlocked("c2.evil.com"))
        assertFalse(blocker.isBlocked("good.com"))
        assertEquals("Pegasus", blocker.blockedBy("evil.com")?.malware)
    }
}
