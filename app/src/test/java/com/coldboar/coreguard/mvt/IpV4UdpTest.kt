package com.coldboar.coreguard.mvt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpV4UdpTest {

    /** Constructs an IPv4/UDP packet from 1.2.3.4:5300 to 10.0.0.2:53. */
    private fun udpPacket(payload: ByteArray): ByteArray {
        val ihl = 20
        val udpLen = 8 + payload.size
        val total = ihl + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = ((total ushr 8) and 0xFF).toByte(); p[3] = (total and 0xFF).toByte()
        p[8] = 64; p[9] = 17
        // src 1.2.3.4
        p[12] = 1; p[13] = 2; p[14] = 3; p[15] = 4
        // dst 10.0.0.2
        p[16] = 10; p[17] = 0; p[18] = 0; p[19] = 2
        // UDP src port 5300, dst 53
        p[20] = (5300 ushr 8).toByte(); p[21] = (5300 and 0xFF).toByte()
        p[22] = 0; p[23] = 53
        p[24] = (udpLen ushr 8).toByte(); p[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    @Test
    fun `parses ipv4 udp datagram`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val dg = IpV4Udp.parse(udpPacket(payload))!!
        assertEquals(5300, dg.srcPort)
        assertEquals(53, dg.dstPort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), dg.srcIp)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), dg.dstIp)
        assertArrayEquals(payload, dg.payload)
    }

    @Test
    fun `rejects non ipv4 and non udp`() {
        assertNull(IpV4Udp.parse(ByteArray(10)))
        val tcp = udpPacket(byteArrayOf(9)).also { it[9] = 6 } // protocol TCP
        assertNull(IpV4Udp.parse(tcp))
    }

    @Test
    fun `reply swaps addresses and ports and carries payload`() {
        val dg = IpV4Udp.parse(udpPacket(byteArrayOf(7, 7)))!!
        val response = byteArrayOf(8, 8, 8)
        val reply = IpV4Udp.buildReply(dg, response)
        val parsed = IpV4Udp.parse(reply)!!
        // src/dst swapped
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), parsed.srcIp)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parsed.dstIp)
        assertEquals(53, parsed.srcPort)
        assertEquals(5300, parsed.dstPort)
        assertArrayEquals(response, parsed.payload)
    }
}
