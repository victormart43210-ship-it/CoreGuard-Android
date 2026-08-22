package com.coldboar.coreguard.mvt

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Testable DNS upstream forward control-flow used by [GuardVpnService].
 *
 * Outcomes:
 * - [DnsForwardResult.UNAVAILABLE] when protect fails, timeout, or I/O fails
 * - [DnsForwardResult.REJECTED] when the validator rejects the upstream reply
 * - [DnsForwardResult.FORWARDED] only when a validated response is written to the tunnel
 */
object DnsUpstreamForwarder {

    data class UpstreamReply(
        val answer: ByteArray,
        val address: InetAddress?,
        val port: Int
    )

    /**
     * Performs the protected UDP exchange with the upstream resolver.
     * Implementations may throw [SocketTimeoutException] or other I/O errors.
     */
    fun interface UpstreamExchange {
        fun exchange(
            queryPayload: ByteArray,
            upstream: InetAddress,
            soTimeoutMs: Int,
            protect: (DatagramSocket) -> Boolean
        ): UpstreamReply
    }

    val defaultExchange = UpstreamExchange { queryPayload, upstream, soTimeoutMs, protect ->
        DatagramSocket().use { socket ->
            if (!protect(socket)) {
                throw ProtectFailedException()
            }
            socket.soTimeout = soTimeoutMs
            socket.connect(InetSocketAddress(upstream, 53))
            socket.send(DatagramPacket(queryPayload, queryPayload.size))
            val respBuf = ByteArray(32_767)
            val resp = DatagramPacket(respBuf, respBuf.size)
            socket.receive(resp)
            UpstreamReply(
                answer = respBuf.copyOf(resp.length),
                address = resp.address,
                port = resp.port
            )
        }
    }

    class ProtectFailedException : IOException("protect(socket) failed")

    /**
     * @param protect must mirror [android.net.VpnService.protect]
     * @param writeTunnel writes the IP/UDP reply into the VPN tunnel
     * @param buildReply builds the tunnel packet from the DNS answer bytes
     */
    fun forward(
        queryPayload: ByteArray,
        upstream: InetAddress,
        protect: (DatagramSocket) -> Boolean,
        writeTunnel: (ByteArray) -> Unit,
        buildReply: (ByteArray) -> ByteArray,
        exchange: UpstreamExchange = defaultExchange,
        soTimeoutMs: Int = 4_000
    ): DnsForwardResult {
        return try {
            val reply = exchange.exchange(queryPayload, upstream, soTimeoutMs, protect)
            if (!DnsUpstreamValidator.accept(
                    query = queryPayload,
                    response = reply.answer,
                    expectedUpstream = upstream,
                    packetAddress = reply.address,
                    packetPort = reply.port
                )
            ) {
                return DnsForwardResult.REJECTED
            }
            val tunnelPacket = buildReply(reply.answer)
            try {
                writeTunnel(tunnelPacket)
            } catch (_: IOException) {
                return DnsForwardResult.UNAVAILABLE
            } catch (_: RuntimeException) {
                return DnsForwardResult.UNAVAILABLE
            }
            DnsForwardResult.FORWARDED
        } catch (_: ProtectFailedException) {
            DnsForwardResult.UNAVAILABLE
        } catch (_: SocketTimeoutException) {
            DnsForwardResult.UNAVAILABLE
        } catch (_: Throwable) {
            DnsForwardResult.UNAVAILABLE
        }
    }
}
