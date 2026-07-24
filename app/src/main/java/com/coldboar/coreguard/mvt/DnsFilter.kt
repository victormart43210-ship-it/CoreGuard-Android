package com.coldboar.coreguard.mvt

/**
 * Minimal, framework-free DNS message helpers used by the domain blocker.
 *
 * Only what the sinkhole needs: read the queried name from a DNS query, and
 * synthesise an NXDOMAIN response for a blocked query. Kept pure so it can be
 * unit-tested on the JVM without any Android/VPN plumbing.
 */
object DnsMessage {

    /** DNS header is 12 bytes; the question section follows. */
    private const val HEADER_LEN = 12

    /**
     * Parses the first question's QNAME from a DNS message [payload] and returns
     * it as a dotted domain (lower-cased), or null if it cannot be parsed.
     */
    fun parseQueryName(payload: ByteArray): String? {
        if (payload.size <= HEADER_LEN) return null
        val qdcount = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        if (qdcount < 1) return null

        val sb = StringBuilder()
        var pos = HEADER_LEN
        while (pos < payload.size) {
            val len = payload[pos].toInt() and 0xFF
            if (len == 0) break
            // Compression pointers should not appear in a question QNAME.
            if (len and 0xC0 != 0) return null
            pos++
            if (pos + len > payload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append((payload[pos + i].toInt() and 0xFF).toChar())
            }
            pos += len
        }
        return sb.toString().lowercase().ifEmpty { null }
    }

    /**
     * Builds an NXDOMAIN (name does not exist) response for the given [query],
     * echoing the transaction id and question section. This is what the blocker
     * returns for a domain on the IOC list, so the lookup fails cleanly.
     */
    fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val questionEnd = findQuestionEnd(query)
        val len = questionEnd.coerceAtMost(query.size)
        val out = query.copyOf(len)
        // Flags: QR=1 (response), echo OPCODE + RD from request, RA=1, RCODE=3 (NXDOMAIN).
        val byte2 = query.getOrElse(2) { 0 }.toInt()
        val opcode = byte2 and 0x78      // bits 6..3 of byte 2 carry the 4-bit OPCODE
        val rd     = byte2 and 0x01      // bit 0 is the RD (recursion desired) flag
        out[2] = (0x80 or opcode or rd).toByte()   // QR=1, OPCODE and RD preserved
        out[3] = (0x80 or 0x03).toByte()           // RA=1, RCODE=3 (NXDOMAIN)
        // Answer / authority / additional counts = 0; qdcount unchanged.
        out[6] = 0; out[7] = 0
        out[8] = 0; out[9] = 0
        out[10] = 0; out[11] = 0
        return out
    }

    /** Returns the offset just past the first question (QNAME + QTYPE + QCLASS). */
    private fun findQuestionEnd(payload: ByteArray): Int {
        var pos = HEADER_LEN
        while (pos < payload.size) {
            val len = payload[pos].toInt() and 0xFF
            if (len == 0) {
                pos++            // end of QNAME
                pos += 4         // QTYPE (2) + QCLASS (2)
                return pos
            }
            if (len and 0xC0 != 0) return payload.size
            pos += 1 + len
        }
        return payload.size
    }
}

/**
 * The block/allow decision for the DNS sinkhole, separated from packet handling
 * so it can be tested directly.
 */
class DomainBlocker(private val matcher: IocMatcher) {

    /** The indicator a domain matches, or null if it should be allowed. */
    fun blockedBy(domain: String): Indicator? = matcher.matchDomain(domain)

    fun isBlocked(domain: String): Boolean = blockedBy(domain) != null
}
