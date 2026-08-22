package com.coldboar.coreguard.mvt

/**
 * Outcome of Privacy Shield upstream DNS forwarding.
 *
 * Only [FORWARDED] may be logged as a successful allow/forward path.
 */
enum class DnsForwardResult {
    /** Validated upstream reply successfully written into the tunnel. */
    FORWARDED,
    /** Packet rejected by sender/TXID/QR/rcode validation. */
    REJECTED,
    /** Timeout, protect() failure, I/O error, or other non-success. */
    UNAVAILABLE
}
