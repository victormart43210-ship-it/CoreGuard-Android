package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Control-flow tests for DNS upstream forwarding outcomes.
 * Replaces enum-name-only coverage with protect/timeout/validator/tunnel proofs.
 */
class DnsForwardResultTest {

    private val upstream: InetAddress = InetAddress.getByName("8.8.8.8")

    private fun query(id: Int = 0x1234): ByteArray {
        val header = byteArrayOf(
            (id ushr 8).toByte(), (id and 0xFF).toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val qname = byteArrayOf(7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0)
        return header + qname + byteArrayOf(0x00, 0x01, 0x00, 0x01)
    }

    private fun responseFor(q: ByteArray, rcode: Int = 0, idOverride: Int? = null): ByteArray {
        val out = q.copyOf(q.size.coerceAtLeast(12))
        if (idOverride != null) {
            out[0] = (idOverride ushr 8).toByte()
            out[1] = (idOverride and 0xFF).toByte()
        }
        out[2] = (0x80 or (q[2].toInt() and 0x7F)).toByte()
        out[3] = (0x80 or (rcode and 0x0F)).toByte()
        return out
    }

    private fun runForward(
        protectOk: Boolean = true,
        reply: DnsUpstreamForwarder.UpstreamReply? = null,
        exchangeError: Throwable? = null,
        writeTunnel: (ByteArray) -> Unit = {}
    ): DnsForwardResult {
        val q = query()
        val exchange = DnsUpstreamForwarder.UpstreamExchange { _, _, _, protect ->
            val sock = DatagramSocket()
            try {
                if (!protect(sock)) throw DnsUpstreamForwarder.ProtectFailedException()
                if (exchangeError != null) throw exchangeError
                reply ?: error("no reply")
            } finally {
                sock.close()
            }
        }
        return DnsUpstreamForwarder.forward(
            queryPayload = q,
            upstream = upstream,
            protect = { protectOk },
            writeTunnel = writeTunnel,
            buildReply = { answer -> answer },
            exchange = exchange
        )
    }

    @Test
    fun `protect false returns UNAVAILABLE`() {
        assertEquals(
            DnsForwardResult.UNAVAILABLE,
            runForward(protectOk = false, reply = DnsUpstreamForwarder.UpstreamReply(
                responseFor(query()), upstream, 53
            ))
        )
    }

    @Test
    fun `timeout returns UNAVAILABLE`() {
        assertEquals(
            DnsForwardResult.UNAVAILABLE,
            runForward(exchangeError = SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `wrong sender returns REJECTED`() {
        val q = query()
        val attacker = InetAddress.getByName("1.2.3.4")
        assertEquals(
            DnsForwardResult.REJECTED,
            runForward(
                reply = DnsUpstreamForwarder.UpstreamReply(responseFor(q), attacker, 53)
            )
        )
    }

    @Test
    fun `wrong sender port returns REJECTED`() {
        val q = query()
        assertEquals(
            DnsForwardResult.REJECTED,
            runForward(
                reply = DnsUpstreamForwarder.UpstreamReply(responseFor(q), upstream, 5353)
            )
        )
    }

    @Test
    fun `wrong TXID returns REJECTED`() {
        val q = query(0x1111)
        assertEquals(
            DnsForwardResult.REJECTED,
            DnsUpstreamForwarder.forward(
                queryPayload = q,
                upstream = upstream,
                protect = { true },
                writeTunnel = {},
                buildReply = { it },
                exchange = DnsUpstreamForwarder.UpstreamExchange { _, _, _, protect ->
                    DatagramSocket().use { s ->
                        if (!protect(s)) throw DnsUpstreamForwarder.ProtectFailedException()
                        DnsUpstreamForwarder.UpstreamReply(responseFor(q, idOverride = 0x2222), upstream, 53)
                    }
                }
            )
        )
    }

    @Test
    fun `QR clear returns REJECTED`() {
        val q = query()
        assertEquals(
            DnsForwardResult.REJECTED,
            runForward(reply = DnsUpstreamForwarder.UpstreamReply(q.copyOf(), upstream, 53))
        )
    }

    @Test
    fun `invalid rcode returns REJECTED`() {
        val q = query()
        assertEquals(
            DnsForwardResult.REJECTED,
            runForward(
                reply = DnsUpstreamForwarder.UpstreamReply(responseFor(q, rcode = 0x0F), upstream, 53)
            )
        )
    }

    @Test
    fun `tunnel write failure cannot return FORWARDED`() {
        val q = query()
        val result = runForward(
            reply = DnsUpstreamForwarder.UpstreamReply(responseFor(q), upstream, 53),
            writeTunnel = { throw IOException("tun down") }
        )
        assertEquals(DnsForwardResult.UNAVAILABLE, result)
        assertNotEquals(DnsForwardResult.FORWARDED, result)
    }

    @Test
    fun `only validated response written to tunnel returns FORWARDED`() {
        val q = query()
        val written = AtomicBoolean(false)
        val writes = AtomicInteger(0)
        val result = runForward(
            reply = DnsUpstreamForwarder.UpstreamReply(responseFor(q), upstream, 53),
            writeTunnel = {
                written.set(true)
                writes.incrementAndGet()
            }
        )
        assertEquals(DnsForwardResult.FORWARDED, result)
        assertTrue(written.get())
        assertEquals(1, writes.get())
    }
}
