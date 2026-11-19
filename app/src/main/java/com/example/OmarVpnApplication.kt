package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.example.core.util.SafeLogger
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.VpnConnectionState
import com.example.vpn.BaseWireGuardVpnService
import com.example.vpn.OmarVpnForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OmarVpnApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        OmarVpnForegroundService.ensureNotificationChannel(this)
        BaseWireGuardVpnService.ensureChannel(this)
        container.networkMonitor.startMonitoring()
        registerAlwaysOnVpnHandler()
        observeAutoConnectTriggers()
    }

    private fun registerAlwaysOnVpnHandler() {
        container.vpnManager.registerAlwaysOnCallback {
            SafeLogger.d("App", "Android Always-on VPN triggered")
            val settings = container.settingsRepository.settingsFlow.first()
            val tunnels = container.tunnelRepository.getAllTunnels().filter { it.isConfigured }
            if (tunnels.isEmpty()) return@registerAlwaysOnCallback

            val chosen = if (settings.autoServerMode) {
                container.autoServerSelector.evaluateAndSelect(tunnels).bestCandidate?.tunnel
                    ?: tunnels.firstOrNull()
            } else {
                settings.selectedTunnelId?.let { id -> tunnels.find { it.id == id } }
                    ?: tunnels.firstOrNull()
            }

            if (chosen != null && container.vpnManager.prepareVpnIntent() == null) {
                container.vpnManager.connect(chosen, settings)
            }
        }
    }

    private fun observeAutoConnectTriggers() {
        appScope.launch {
            combine(
                container.networkMonitor.snapshot,
                container.settingsRepository.settingsFlow
            ) { snapshot, settings ->
                snapshot to settings
            }
                .distinctUntilChanged()
                .collect { (snapshot, settings) ->
                    val currentVpn = container.vpnManager.connectionState.value
                    if (currentVpn !is VpnConnectionState.Disconnected) return@collect
                    if (!snapshot.isConnected || snapshot.isVpnActive) return@collect
                    if (container.vpnManager.prepareVpnIntent() != null) return@collect

                    val shouldAutoConnect = when (snapshot.transport) {
                        NetworkTransportType.WIFI -> settings.autoConnectOnUnsecuredWifi
                        NetworkTransportType.CELLULAR -> settings.autoConnectOnMobileData
                        else -> false
                    }
                    if (!shouldAutoConnect) return@collect

                    val tunnels = container.tunnelRepository.getAllTunnels().filter { it.isConfigured }
                    if (tunnels.isEmpty()) return@collect

                    val chosen = if (settings.autoServerMode) {
                        container.autoServerSelector.evaluateAndSelect(tunnels).bestCandidate?.tunnel
                    } else {
                        settings.selectedTunnelId?.let { id -> tunnels.find { it.id == id } }
                            ?: tunnels.firstOrNull()
                    }

                    if (chosen != null) {
                        container.vpnManager.connect(chosen, settings)
                    }
                }
        }
    }
}
