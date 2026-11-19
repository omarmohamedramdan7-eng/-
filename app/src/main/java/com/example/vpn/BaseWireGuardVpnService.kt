package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.datastore.AppSettings
import com.example.core.datastore.DnsPreset
import com.example.core.datastore.SplitTunnelMode
import com.example.core.util.SafeLogger
import com.example.domain.model.TunnelProfile
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket

object VpnCidrParser {
    fun parseCidr(rawCidr: String): Pair<InetAddress, Int>? {
        val trimmed = rawCidr.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split("/")
        if (parts.size > 2) return null
        val address = runCatching { InetAddress.getByName(parts[0].trim()) }.getOrNull() ?: return null
        val maxPrefix = when (address) {
            is Inet4Address -> 32
            is Inet6Address -> 128
            else -> return null
        }
        val prefix = if (parts.size == 2) {
            parts[1].trim().toIntOrNull()?.takeIf { it in 0..maxPrefix } ?: return null
        } else {
            maxPrefix
        }
        return address to prefix
    }
}

/**
 * Base Android VpnService class managing TUN interface lifecycle, traffic routing,
 * CIDR/DNS/MTU configuration, split tunneling, socket protection, foreground notification,
 * Always-On restarts, and system revocation.
 */
abstract class BaseWireGuardVpnService : VpnService() {

    @Volatile
    protected var tunInterface: ParcelFileDescriptor? = null
        private set

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        SafeLogger.d(TAG, "BaseWireGuardVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System restarted the service (e.g. Always-on VPN or START_STICKY recovery)
            SafeLogger.d(TAG, "Service restarted by system (Always-On)")
            onAlwaysOnTriggered()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START_TUNNEL -> {
                val tunnelId = intent.getLongExtra(EXTRA_TUNNEL_ID, -1L)
                val overrideDns = intent.getStringExtra(EXTRA_OVERRIDE_DNS)
                val overrideMtu = intent.getIntExtra(EXTRA_OVERRIDE_MTU, -1).takeIf { it in 1280..1500 }
                val serverLabel = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty()
                startForeground(NOTIFICATION_ID, buildStatusNotification(serverLabel, isConnecting = true))
                onStartWireGuardTunnel(tunnelId, overrideDns, overrideMtu)
            }
            ACTION_STOP_TUNNEL -> {
                onStopWireGuardTunnel(revokedBySystem = false)
                closeTunInterface()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                onAlwaysOnTriggered()
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        SafeLogger.w(TAG, "VPN permission revoked by system or another VPN app")
        onStopWireGuardTunnel(revokedBySystem = true)
        closeTunInterface()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunInterface()
        super.onDestroy()
        SafeLogger.d(TAG, "BaseWireGuardVpnService destroyed")
    }

    /**
     * Builds and establishes an Android TUN interface configured for WireGuard traffic routing.
     */
    protected fun establishTunInterface(
        profile: TunnelProfile,
        settings: AppSettings,
        overrideDns: String? = null,
        overrideMtu: Int? = null
    ): ParcelFileDescriptor? {
        closeTunInterface()
        val builder = buildVpnInterface(profile, settings, overrideDns, overrideMtu)
        return try {
            builder.establish().also { pfd ->
                tunInterface = pfd
                if (pfd == null) {
                    SafeLogger.e(TAG, "VpnService.Builder.establish() returned null (consent missing)")
                } else {
                    SafeLogger.d(TAG, "TUN interface established (fd=${pfd.fd})")
                }
            }
        } catch (e: Exception) {
            SafeLogger.e(TAG, "Failed to establish TUN interface: ${e.message}")
            null
        }
    }

    protected fun buildVpnInterface(
        profile: TunnelProfile,
        settings: AppSettings,
        overrideDns: String? = null,
        overrideMtu: Int? = null
    ): Builder {
        val builder = Builder()
            .setSession(profile.name.ifBlank { getString(R.string.app_name) })
            .setMtu((overrideMtu ?: profile.mtu).coerceIn(1280, 1500))
            .setBlocking(false)
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .allowBypass()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Interface addresses
        profile.interfaceAddresses.split(",")
            .mapNotNull { VpnCidrParser.parseCidr(it) }
            .forEach { (addr, prefix) ->
                builder.addAddress(addr, prefix)
            }

        // Traffic routes (AllowedIPs)
        profile.allowedIps.split(",")
            .mapNotNull { VpnCidrParser.parseCidr(it) }
            .forEach { (addr, prefix) ->
                builder.addRoute(addr, prefix)
            }

        // DNS configuration
        val resolvedDns = when {
            !overrideDns.isNullOrBlank() -> overrideDns
            settings.dnsPreset == DnsPreset.TUNNEL_DEFAULT -> profile.dnsServers
            settings.dnsPreset == DnsPreset.CUSTOM -> settings.customDnsServers.ifBlank { profile.dnsServers }
            else -> settings.dnsPreset.servers.joinToString(", ")
        }
        resolvedDns.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { dnsHost ->
                runCatching { InetAddress.getByName(dnsHost) }
                    .getOrNull()
                    ?.let { builder.addDnsServer(it) }
            }

        // Per-app Split Tunneling
        applySplitTunneling(builder, settings)

        // Tap notification returns to MainActivity
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val configureIntent = PendingIntent.getActivity(
            this,
            10,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setConfigureIntent(configureIntent)

        return builder
    }

    private fun applySplitTunneling(builder: Builder, settings: AppSettings) {
        when (settings.splitTunnelMode) {
            SplitTunnelMode.ALL_APPS -> {
                // Exclude self to guarantee diagnostic socket probes & backend control channels stay loop-free
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            SplitTunnelMode.EXCLUDE_SELECTED -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                settings.splitTunnelPackages.forEach { pkg ->
                    if (pkg.isNotBlank() && pkg != packageName) {
                        try {
                            builder.addDisallowedApplication(pkg)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                }
            }
            SplitTunnelMode.INCLUDE_SELECTED -> {
                settings.splitTunnelPackages.forEach { pkg ->
                    if (pkg.isNotBlank() && pkg != packageName) {
                        try {
                            builder.addAllowedApplication(pkg)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                }
            }
        }
    }

    protected fun closeTunInterface() {
        runCatching {
            tunInterface?.close()
        }.onFailure {
            SafeLogger.w(TAG, "Error closing TUN interface: ${it.message}")
        }
        tunInterface = null
    }

    fun protectTunnelSocket(socket: Socket): Boolean = protect(socket)

    fun protectTunnelDatagramSocket(socket: DatagramSocket): Boolean = protect(socket)

    protected fun updateForegroundStatus(serverName: String, isConnecting: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(this)
        manager.notify(NOTIFICATION_ID, buildStatusNotification(serverName, isConnecting))
    }

    private fun buildStatusNotification(serverName: String, isConnecting: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            11,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, this::class.java).apply { action = ACTION_STOP_TUNNEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnecting) {
            getString(R.string.vpn_notification_title_connecting)
        } else {
            getString(R.string.vpn_notification_title_connected)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setContentTitle(title)
            .setContentText(serverName.ifBlank { getString(R.string.app_name) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.vpn_notification_action_disconnect), stopIntent)
            .build()
    }

    protected abstract fun onStartWireGuardTunnel(
        tunnelId: Long,
        overrideDns: String?,
        overrideMtu: Int?
    )

    protected abstract fun onStopWireGuardTunnel(revokedBySystem: Boolean)

    protected abstract fun onAlwaysOnTriggered()

    companion object {
        private const val TAG = "BaseWgVpnService"
        const val ACTION_START_TUNNEL = "com.omarvpn.action.BASE_START_TUNNEL"
        const val ACTION_STOP_TUNNEL = "com.omarvpn.action.BASE_STOP_TUNNEL"
        const val EXTRA_TUNNEL_ID = "extra_tunnel_id"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_OVERRIDE_DNS = "extra_override_dns"
        const val EXTRA_OVERRIDE_MTU = "extra_override_mtu"
        private const val CHANNEL_ID = "omar_vpn_tunnel_channel"
        private const val NOTIFICATION_ID = 7002

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_vpn_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notification_channel_vpn_desc)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}
