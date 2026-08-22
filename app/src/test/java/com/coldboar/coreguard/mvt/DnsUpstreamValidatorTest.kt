package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsUpstreamValidatorTest {

    private fun query(domain: String, id: Int = 0x1234): ByteArray {
        val header = byteArrayOf(
            (id ushr 8).toByte(), (id and 0xFF).toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val qname = buildList {
            domain.split('.').forEach { label ->
                add(label.length.toByte())
                label.forEach { add(it.code.toByte()) }
            }
            add(0.toByte())
        }.toByteArray()
        val qtail = byteArrayOf(0x00, 0x01, 0x00, 0x01)
        return header + qname + qtail
    }

    private fun responseFor(query: ByteArray, rcode: Int = 0): ByteArray {
        val out = query.copyOf(query.size.coerceAtLeast(12))
        out[2] = (0x80 or (query[2].toInt() and 0x7F)).toByte()
        out[3] = (0x80 or (rcode and 0x0F)).toByte()
        return out
    }

    @Test
    fun `rejects packet from unexpected address`() {
        val q = query("example.com", 0xABCD)
        val r = responseFor(q)
        val upstream = InetAddress.getByName("8.8.8.8")
        val attacker = InetAddress.getByName("1.2.3.4")
        assertFalse(
            DnsUpstreamValidator.accept(q, r, upstream, attacker, 53)
        )
    }

    @Test
    fun `rejects packet from unexpected port`() {
        val q = query("example.com", 0xABCD)
        val r = responseFor(q)
        val upstream = InetAddress.getByName("8.8.8.8")
        assertFalse(
            DnsUpstreamValidator.accept(q, r, upstream, upstream, 5353)
        )
    }

    @Test
    fun `rejects mismatched DNS transaction id`() {
        val q = query("example.com", 0x1111)
        val r = responseFor(query("example.com", 0x2222))
        val upstream = InetAddress.getByName("8.8.8.8")
        assertFalse(
            DnsUpstreamValidator.accept(q, r, upstream, upstream, 53)
        )
    }

    @Test
    fun `rejects non-response QR bit clear`() {
        val q = query("example.com", 0x1234)
        val upstream = InetAddress.getByName("8.8.8.8")
        // Same bytes as query → QR=0
        assertFalse(
            DnsUpstreamValidator.accept(q, q.copyOf(), upstream, upstream, 53)
        )
    }

    @Test
    fun `rejects unacceptable rcode`() {
        val q = query("example.com", 0x1234)
        val r = responseFor(q, rcode = 0x0F)
        val upstream = InetAddress.getByName("8.8.8.8")
        assertFalse(
            DnsUpstreamValidator.accept(q, r, upstream, upstream, 53)
        )
    }

    @Test
    fun `accepts valid response from expected upstream`() {
        val q = query("example.com", 0xABCD)
        val r = responseFor(q, rcode = 0)
        val upstream = InetAddress.getByName("8.8.8.8")
        assertTrue(
            DnsUpstreamValidator.accept(q, r, upstream, upstream, 53)
        )
        assertEquals(0xABCD, DnsUpstreamValidator.transactionId(r))
        assertTrue(DnsUpstreamValidator.isDnsResponse(r))
    }
}
