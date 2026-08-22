package com.coldboar.coreguard.mvt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.coldboar.coreguard.MainActivity
import com.coldboar.coreguard.R
import com.coldboar.coreguard.quilla.QuillaIocBridge
import com.coldboar.coreguard.quilla.QuillaMemoryModule
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress

/**
 * Privacy Shield: a local [VpnService] DNS sinkhole for known indicator domains.
 *
 * The tunnel captures only DNS traffic (its virtual DNS server is the only
 * route installed). For each query it extracts the requested domain and:
 *  - if the domain matches a known [Indicator], it answers NXDOMAIN so the
 *    lookup fails and the app does not resolve that indicator hostname;
 *  - otherwise it forwards the query to a real upstream resolver through a
 *    [protect]ed socket and relays the answer back.
 *
 * This is a userspace, non-root aid against DNS lookups to listed indicator
 * domains. It is not live IPS, does not remove implants, and cannot block
 * traffic that uses a hardcoded IP (no DNS lookup) — documented for the user.
 */
class GuardVpnService : VpnService() {

    private val virtualAddress = "10.111.222.1"
    private val virtualDns = "10.111.222.2"

    @Volatile private var running = false
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private lateinit var blocker: DomainBlocker

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopShield()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY

        blocker = DomainBlocker(IocRepository.matcher(this))
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val builder = Builder()
            .setSession("CoreGuard Shield")
            .addAddress(virtualAddress, 32)
            .addDnsServer(virtualDns)
            .addRoute(virtualDns, 32)
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) }

        tunnel = try {
            builder.establish()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to establish VPN: ${t.message}")
            stopShield()
            return START_NOT_STICKY
        }

        if (tunnel == null) {
            Log.e(TAG, "VPN establish() returned null — not marking shield active")
            stopShield()
            return START_NOT_STICKY
        }

        running = true
        ShieldState.setActive(true)
        worker = Thread({ pump() }, "coreguard-shield").apply { isDaemon = true; start() }
        Log.i(TAG, "Shield active (DNS sinkhole up)")
        return START_STICKY
    }

    private fun pump() {
        val fd = tunnel?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val upstream = resolveUpstreamDns()
        val buffer = ByteArray(32_767)

        while (running) {
            val read = try {
                input.read(buffer)
            } catch (t: Throwable) {
                if (running) Log.w(TAG, "tun read error: ${t.message}")
                break
            }
            if (read <= 0) continue

            val packet = buffer.copyOf(read)
            val parsed = IpV4Udp.parse(packet) ?: continue
            val domain = DnsMessage.parseQueryName(parsed.payload) ?: continue

            val hit = blocker.blockedBy(domain)
            if (hit != null) {
                val response = DnsMessage.buildNxDomainResponse(parsed.payload)
                val ipReply = IpV4Udp.buildReply(parsed, response)
                runCatching { output.write(ipReply) }
                ShieldState.recordBlocked(domain)
                QuillaMemoryModule.ensureLocalIntel(this)
                QuillaIocBridge.correlateShieldBlock(
                    domain,
                    QuillaMemoryModule.correlationEngine()
                )
                Log.w(TAG, "BLOCKED $domain (${hit.malware})")
            } else {
                when (forward(parsed, upstream, output)) {
                    DnsForwardResult.FORWARDED -> Log.d(TAG, "FORWARDED $domain")
                    DnsForwardResult.REJECTED -> Log.w(TAG, "REJECTED upstream reply for $domain")
                    DnsForwardResult.UNAVAILABLE -> Log.d(TAG, "UNAVAILABLE forward for $domain")
                }
            }
        }
    }

    /**
     * Forwards an allowed DNS query to the real resolver and relays the reply.
     *
     * Uses a connected datagram socket plus [DnsUpstreamValidator] so a forged
     * UDP reply from another address/port or with a mismatched transaction ID
     * is never written into the tunnel. [VpnService.protect] must succeed.
     */
    private fun forward(
        query: IpV4Udp.Datagram,
        upstream: InetAddress,
        output: FileOutputStream
    ): DnsForwardResult {
        return DnsUpstreamForwarder.forward(
            queryPayload = query.payload,
            upstream = upstream,
            protect = { socket -> protect(socket) },
            writeTunnel = { bytes -> output.write(bytes) },
            buildReply = { answer -> IpV4Udp.buildReply(query, answer) },
            exchange = DnsUpstreamForwarder.defaultExchange
        )
    }

    /**
     * Prefer the active network's system **IPv4** DNS (the shield tunnel forwards
     * IPv4 UDP DNS). Private DNS / IPv6 resolution remains an OS concern outside
     * this path. Hardcoded 8.8.8.8 is last-resort only when no system DNS exists.
     */
    private fun resolveUpstreamDns(): InetAddress {
        val system = runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            cm.getLinkProperties(net)?.dnsServers?.firstOrNull { it.address.size == 4 }
        }.getOrNull()
        return system ?: InetAddress.getByName("8.8.8.8")
    }

    private fun stopShield() {
        running = false
        worker?.interrupt()
        worker = null
        runCatching { tunnel?.close() }
        tunnel = null
        ShieldState.setActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Shield stopped")
    }

    override fun onDestroy() {
        stopShield()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopShield()
        super.onRevoke()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Privacy Shield", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DNS sinkhole for known indicator domains (plaintext UDP forward)"
            }
            manager.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Privacy Shield active")
            .setContentText("Blocking listed indicator domains via local DNS sinkhole")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GuardVpnService"
        private const val CHANNEL_ID = "coreguard_shield"
        private const val NOTIF_ID = 0xC0DE
        const val ACTION_STOP = "com.coldboar.coreguard.STOP_SHIELD"
    }
}
