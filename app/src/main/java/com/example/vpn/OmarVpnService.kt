package com.example.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.OmarVpnApplication
import com.example.core.util.SafeLogger
import com.example.domain.model.TunnelProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Concrete OMAR VPN service inheriting from BaseWireGuardVpnService (android.net.VpnService)
 * that synchronizes OS VpnService lifecycle events with OmarVpnManager and WireGuard GoBackend.
 */
class OmarVpnService : BaseWireGuardVpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartWireGuardTunnel(
        tunnelId: Long,
        overrideDns: String?,
        overrideMtu: Int?
    ) {
        val app = applicationContext as? OmarVpnApplication ?: return
        val container = app.container

        serviceScope.launch {
            val settings = container.settingsRepository.settingsFlow.first()
            val tunnels = container.tunnelRepository.getAllTunnels().filter { it.isConfigured }
            val profile = when {
                tunnelId > 0L -> tunnels.find { it.id == tunnelId }
                settings.selectedTunnelId != null -> tunnels.find { it.id == settings.selectedTunnelId }
                else -> tunnels.firstOrNull()
            }

            if (profile == null) {
                SafeLogger.w(TAG, "No configured tunnel found to start")
                stopSelf()
                return@launch
            }

            updateForegroundStatus(profile.serverName, isConnecting = true)
            val connected = container.vpnManager.connect(
                profile = profile,
                settings = settings,
                overrideDns = overrideDns,
                overrideMtu = overrideMtu
            )
            if (connected) {
                updateForegroundStatus(profile.serverName, isConnecting = false)
            } else {
                stopSelf()
            }
        }
    }

    override fun onStopWireGuardTunnel(revokedBySystem: Boolean) {
        val app = applicationContext as? OmarVpnApplication ?: return
        serviceScope.launch {
            if (revokedBySystem) {
                app.container.vpnManager.handleSystemRevoke()
            } else {
                app.container.vpnManager.disconnect()
            }
        }
    }

    override fun onAlwaysOnTriggered() {
        val app = applicationContext as? OmarVpnApplication ?: return
        val container = app.container
        serviceScope.launch {
            val settings = container.settingsRepository.settingsFlow.first()
            val tunnels = container.tunnelRepository.getAllTunnels().filter { it.isConfigured }
            val target = settings.selectedTunnelId?.let { id -> tunnels.find { it.id == id } }
                ?: tunnels.firstOrNull()
            if (target != null && container.vpnManager.prepareVpnIntent() == null) {
                updateForegroundStatus(target.serverName, isConnecting = true)
                container.vpnManager.connect(target, settings)
            } else {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OmarVpnService"

        fun startTunnelIntent(
            context: Context,
            profile: TunnelProfile,
            overrideDns: String? = null,
            overrideMtu: Int? = null
        ): Intent {
            return Intent(context, OmarVpnService::class.java).apply {
                action = ACTION_START_TUNNEL
                putExtra(EXTRA_TUNNEL_ID, profile.id)
                putExtra(EXTRA_SERVER_NAME, profile.serverName)
                if (!overrideDns.isNullOrBlank()) {
                    putExtra(EXTRA_OVERRIDE_DNS, overrideDns)
                }
                if (overrideMtu != null) {
                    putExtra(EXTRA_OVERRIDE_MTU, overrideMtu)
                }
            }
        }

        fun stopTunnelIntent(context: Context): Intent {
            return Intent(context, OmarVpnService::class.java).apply {
                action = ACTION_STOP_TUNNEL
            }
        }

        fun launchStart(
            context: Context,
            profile: TunnelProfile,
            overrideDns: String? = null,
            overrideMtu: Int? = null
        ) {
            val intent = startTunnelIntent(context, profile, overrideDns, overrideMtu)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
