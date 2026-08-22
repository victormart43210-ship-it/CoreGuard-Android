package com.coldboar.coreguard.mvt

import java.net.InetAddress

/**
 * Pure DNS upstream-response checks for Privacy Shield forwarding.
 *
 * A connected [java.net.DatagramSocket] is the primary source filter; these
 * checks add defense-in-depth for transaction ID, QR/rcode sanity, and explicit
 * sender address/port equality so a forged UDP reply cannot be relayed.
 */
object DnsUpstreamValidator {

    private const val HEADER_LEN = 12
    private const val DNS_PORT = 53

    /** DNS transaction ID (first two bytes), or null if the buffer is too short. */
    fun transactionId(message: ByteArray): Int? {
        if (message.size < 2) return null
        return ((message[0].toInt() and 0xFF) shl 8) or (message[1].toInt() and 0xFF)
    }

    /** True when the QR bit is set (message is a response). */
    fun isDnsResponse(message: ByteArray): Boolean {
        if (message.size < 3) return false
        return (message[2].toInt() and 0x80) != 0
    }

    /** RCODE from flags byte 3, or null if truncated. */
    fun rcode(message: ByteArray): Int? {
        if (message.size < 4) return null
        return message[3].toInt() and 0x0F
    }

    /**
     * Accept NOERROR through REFUSED (0–5). Reject FORMERR-adjacent reserved /
     * unassigned codes that indicate a nonsensical or attack packet.
     */
    fun isAcceptableRcode(rcode: Int): Boolean = rcode in 0..5

    /**
     * Returns true only when [response] is a plausible answer to [query] from
     * exactly [expectedUpstream]:[DNS_PORT].
     */
    fun accept(
        query: ByteArray,
        response: ByteArray,
        expectedUpstream: InetAddress,
        packetAddress: InetAddress?,
        packetPort: Int
    ): Boolean {
        if (response.size < HEADER_LEN) return false
        if (packetAddress == null) return false
        if (packetPort != DNS_PORT) return false
        if (packetAddress != expectedUpstream) return false

        val queryId = transactionId(query) ?: return false
        val responseId = transactionId(response) ?: return false
        if (queryId != responseId) return false

        if (!isDnsResponse(response)) return false
        val code = rcode(response) ?: return false
        if (!isAcceptableRcode(code)) return false

        return true
    }
}
