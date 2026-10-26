package com.focuslock.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.focuslock.FocusLockApp
import com.focuslock.R
import com.focuslock.core.FocusLog
import com.focuslock.ui.FocusIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Website allowlist, implemented as a local DNS resolver VPN.
 *
 * Only the DNS server address is routed through the tunnel, and only permitted browser apps
 * are in scope (`addAllowedApplication`). For each lookup the service checks the allowlist and
 * either forwards the query to the real resolver or answers NXDOMAIN locally. Ordinary traffic
 * never enters the tunnel, so throughput of permitted apps is unaffected.
 *
 * Known limits, also documented in docs/LIMITATIONS.md:
 *  - Private DNS / DoH, IP-literal URLs, cached answers and non-browser apps bypass DNS rules.
 *  - Only one VPN can be active; another VPN or a system-level private DNS setting wins.
 *  - The filter is per-session and stops when the session ends.
 */
class WebsiteFilterService : VpnService() {

    private val container get() = (application as FocusLockApp).container

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var upstreamSocket: DatagramSocket? = null
    private var upstreamAddress: InetAddress? = null
    private var allowedDomains: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tun != null) return START_STICKY

        val session = container.repository.activeSession()
        if (session == null) {
            FocusLog.d("No active session; website filter stops")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!WebsiteFilterRule.isApplicable(session.websiteFilterEnabled, session.allowedDomains)) {
            FocusLog.d("Website filter not applicable to this session")
            stopSelf()
            return START_NOT_STICKY
        }
        val browsers = container.catalog.browserPackages().intersect(session.allowedPackages.toSet())
        if (browsers.isEmpty()) {
            FocusLog.d("No permitted browser app; website filter stays off")
            stopSelf()
            return START_NOT_STICKY
        }
        if (VpnService.prepare(this) != null) {
            FocusLog.w("VPN consent missing; website filter cannot start")
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(session.allowedDomains.size)
        allowedDomains = session.allowedDomains

        val descriptor = try {
            val builder = Builder()
                .setSession("Focus Lock website allowlist")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setMtu(1500)
                .setBlocking(true)
            browsers.forEach { packageName ->
                runCatching { builder.addAllowedApplication(packageName) }
                    .onFailure { FocusLog.w("Cannot scope VPN to $packageName", it) }
            }
            builder.establish()
        } catch (error: Exception) {
            FocusLog.e("Could not establish the website filter tunnel", error)
            null
        }

        if (descriptor == null) {
            FocusLog.e("VPN establishment returned no descriptor")
            stopSelf()
            return START_NOT_STICKY
        }

        tun = descriptor
        upstreamAddress = resolveUpstreamAddress()
        upstreamSocket = openUpstreamSocket()

        worker = scope.launch { runTunnel(descriptor) }
        FocusLog.d("Website allowlist active for ${allowedDomains.size} domain(s), browsers=$browsers")
        return START_STICKY
    }

    override fun onRevoke() {
        FocusLog.w("VPN permission revoked; stopping website filter")
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        runCatching { tun?.close() }
        runCatching { upstreamSocket?.close() }
        tun = null
        upstreamSocket = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        FocusLog.d("Website filter stopped")
        super.onDestroy()
    }

    private fun runTunnel(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)
        try {
            while (scope.isActive) {
                val length = try {
                    input.read(buffer)
                } catch (error: IOException) {
                    break
                }
                if (length <= 0) continue

                val datagram = IpUdpPacket.parse(buffer, length) ?: continue
                if (datagram.destinationPort != DNS_PORT) continue
                val query = DnsPacket.parseQuery(datagram.payload) ?: continue

                val answer = if (WebsiteFilterRule.isAllowed(query.name, allowedDomains)) {
                    forward(query)
                } else {
                    FocusLog.d("DNS blocked: ${query.name}")
                    DnsPacket.nxDomain(query)
                } ?: continue

                val response = IpUdpPacket.build(
                    sourceAddress = datagram.destinationAddress,
                    destinationAddress = datagram.sourceAddress,
                    sourcePort = DNS_PORT,
                    destinationPort = datagram.sourcePort,
                    payload = answer,
                )
                try {
                    output.write(response)
                } catch (error: IOException) {
                    break
                }
            }
        } catch (error: Exception) {
            FocusLog.e("Website filter tunnel stopped unexpectedly", error)
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    private fun forward(query: DnsPacket.Query): ByteArray? {
        val socket = upstreamSocket ?: return null
        val address = upstreamAddress ?: return null
        return try {
            socket.send(DatagramPacket(query.bytes, query.bytes.size, address, DNS_PORT))
            val responseBuffer = ByteArray(MAX_PACKET_SIZE)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            socket.receive(response)
            responseBuffer.copyOfRange(response.offset, response.offset + response.length)
        } catch (error: IOException) {
            // Returning null drops the query; the client retries rather than receiving a lie.
            FocusLog.w("Upstream DNS lookup failed for ${query.name}", error)
            null
        }
    }

    private fun openUpstreamSocket(): DatagramSocket? = try {
        DatagramSocket().also { socket ->
            // protect() keeps the query on the real network instead of looping into our tun.
            if (!protect(socket)) FocusLog.w("protect() returned false for the DNS socket")
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
        }
    } catch (error: Exception) {
        FocusLog.e("Could not open the upstream DNS socket", error)
        null
    }

    private fun resolveUpstreamAddress(): InetAddress = try {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val dnsServers = network?.let { connectivity.getLinkProperties(it)?.dnsServers }
        dnsServers?.firstOrNull { it is Inet4Address }
            ?: InetAddress.getByName(FALLBACK_DNS)
    } catch (error: Exception) {
        FocusLog.w("Falling back to $FALLBACK_DNS", error)
        InetAddress.getByName(FALLBACK_DNS)
    }

    private fun startInForeground(domainCount: Int) {
        val notification = buildNotification(domainCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(domainCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            FocusIntents.focus(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Website allowlist active")
            .setContentText("$domainCount site${if (domainCount == 1) "" else "s"} allowed while this session runs")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Website allowlist",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Focus Lock filters browser DNS during a session."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val DNS_PORT = 53
        const val NOTIFICATION_ID = 41
        const val CHANNEL_ID = "focus_lock_web_filter"
        const val MAX_PACKET_SIZE = 32_767
        const val UPSTREAM_TIMEOUT_MS = 2_500

        /** Virtual addresses inside the VPN; they never leave the device. */
        const val TUN_ADDRESS = "10.111.222.1"
        const val DNS_ADDRESS = "10.111.222.2"
        const val FALLBACK_DNS = "1.1.1.1"
    }
}
