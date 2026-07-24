package com.coldboar.coreguard.mvt

/**
 * Tiny IPv4 + UDP packet reader/writer for the DNS sinkhole tunnel.
 *
 * Only the subset needed to read a DNS query datagram off the tun device and
 * write a reply back is implemented. Pure byte math, no Android dependencies,
 * so it can be unit-tested directly.
 */
object IpV4Udp {

    /** A parsed IPv4/UDP datagram: addresses, ports and the UDP payload. */
    data class Datagram(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    )

    /** Parses an IPv4/UDP packet, or returns null if it is not IPv4 UDP. */
    fun parse(packet: ByteArray): Datagram? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() and 0xF0) ushr 4
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || packet.size < ihl + 8) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        val udpLen = u16(packet, ihl + 4)
        val payloadLen = (udpLen - 8).coerceAtLeast(0)
        val payloadStart = ihl + 8
        val payloadEnd = (payloadStart + payloadLen).coerceAtMost(packet.size)
        val payload = packet.copyOfRange(payloadStart, payloadEnd)
        return Datagram(srcIp, dstIp, srcPort, dstPort, payload)
    }

    /**
     * Builds an IPv4/UDP reply to [query] carrying [responsePayload], swapping
     * source/destination addresses and ports so it returns to the sender.
     */
    fun buildReply(query: Datagram, responsePayload: ByteArray): ByteArray {
        val ihl = 20
        val udpLen = 8 + responsePayload.size
        val totalLen = ihl + udpLen
        val out = ByteArray(totalLen)

        // ---- IPv4 header ----
        out[0] = 0x45.toByte()                 // version 4, IHL 5
        out[1] = 0                             // DSCP/ECN
        putU16(out, 2, totalLen)               // total length
        putU16(out, 4, 0)                      // identification
        putU16(out, 6, 0x4000)                 // flags: don't fragment
        out[8] = 64                            // TTL
        out[9] = 17                            // protocol UDP
        // checksum (10..11) computed below
        System.arraycopy(query.dstIp, 0, out, 12, 4) // src = original dst
        System.arraycopy(query.srcIp, 0, out, 16, 4) // dst = original src
        val ipChecksum = checksum(out, 0, ihl)
        putU16(out, 10, ipChecksum)

        // ---- UDP header ----
        putU16(out, ihl, query.dstPort)        // src port = original dst (53)
        putU16(out, ihl + 2, query.srcPort)    // dst port = original src
        putU16(out, ihl + 4, udpLen)           // UDP length
        putU16(out, ihl + 6, 0)                // UDP checksum 0 (optional in IPv4)
        System.arraycopy(responsePayload, 0, out, ihl + 8, responsePayload.size)
        return out
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun putU16(b: ByteArray, off: Int, value: Int) {
        b[off] = ((value ushr 8) and 0xFF).toByte()
        b[off + 1] = (value and 0xFF).toByte()
    }

    /** Standard one's-complement Internet checksum over [len] bytes from [off]. */
    private fun checksum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        val end = off + len
        while (i + 1 < end) {
            sum += u16(data, i).toLong()
            i += 2
        }
        if (i < end) sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
