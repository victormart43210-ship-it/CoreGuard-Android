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
import com.coldboar.coreguard.MainActivity
import com.coldboar.coreguard.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * The "Pegasus blocker": a local [VpnService] that acts as a DNS sinkhole.
 *
 * The tunnel captures only DNS traffic (its virtual DNS server is the only
 * route installed). For each query it extracts the requested domain and:
 *  - if the domain matches a malicious [Indicator], it answers NXDOMAIN so the
 *    lookup fails and the app never learns the spyware C2 address;
 *  - otherwise it forwards the query to a real upstream resolver through a
 *    [protect]ed socket and relays the answer back.
 *
 * This is a userspace, non-root way to stop a compromised app from reaching
 * known mercenary-spyware infrastructure. It cannot block traffic that uses a
 * hardcoded IP (no DNS lookup); that limitation is documented for the user.
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
        startForeground(NOTIF_ID, buildNotification())

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
                Log.w(TAG, "BLOCKED $domain (${hit.malware})")
            } else {
                forward(parsed, upstream, output)
                Log.d(TAG, "ALLOWED $domain")
            }
        }
    }

    /** Forwards an allowed DNS query to the real resolver and relays the reply. */
    private fun forward(query: IpV4Udp.Datagram, upstream: InetAddress, output: FileOutputStream) {
        runCatching {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 4_000
                val out = DatagramPacket(query.payload, query.payload.size, InetSocketAddress(upstream, 53))
                socket.send(out)
                val respBuf = ByteArray(32_767)
                val resp = DatagramPacket(respBuf, respBuf.size)
                socket.receive(resp)
                val answer = respBuf.copyOf(resp.length)
                output.write(IpV4Udp.buildReply(query, answer))
            }
        }.onFailure { Log.d(TAG, "upstream forward failed for query: ${it.message}") }
    }

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
                CHANNEL_ID, "CoreGuard Shield", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Blocks known mercenary-spyware domains" }
            manager.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CoreGuard Shield active")
            .setContentText("Blocking known spyware infrastructure")
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
