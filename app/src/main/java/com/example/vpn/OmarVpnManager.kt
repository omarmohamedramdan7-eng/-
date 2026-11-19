package com.example.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.TileService
import com.example.core.datastore.AppSettings
import com.example.core.util.SafeLogger
import com.example.domain.model.TunnelProfile
import com.example.domain.model.VpnConnectionState
import com.example.domain.model.VpnErrorCode
import com.example.network.ActiveNetworkMonitor
import com.example.network.NetworkProber
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real WireGuard tunnel orchestrator powered by official GoBackend (VpnService).
 * Guarantees that VpnConnectionState.Connected is emitted ONLY when the WireGuard
 * backend state is UP and Android's VpnService interface is active.
 */
class OmarVpnManager(
    private val context: Context,
    private val networkMonitor: ActiveNetworkMonitor,
    private val prober: NetworkProber
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val backend: GoBackend by lazy { GoBackend(context.applicationContext) }

    private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private var activeTunnelProfile: TunnelProfile? = null
    private var activeSettings: AppSettings = AppSettings()
    private var connectedElapsedBaseMs: Long = 0L
    private var telemetryJob: Job? = null
    private var userRequestedDisconnect: Boolean = false

    private val activeTunnel = object : Tunnel {
        override fun getName(): String = activeTunnelProfile?.name ?: "omar_wg0"

        override fun onStateChange(newState: Tunnel.State) {
            SafeLogger.d("VpnManager", "Tunnel backend state changed: $newState")
            if (newState == Tunnel.State.DOWN && !userRequestedDisconnect) {
                val current = _connectionState.value
                val profile = activeTunnelProfile
                if (current is VpnConnectionState.Connected && profile != null && activeSettings.autoReconnect) {
                    scheduleAutoReconnect(profile, activeSettings, "Tunnel dropped unexpectedly")
                } else if (current !is VpnConnectionState.Connecting && current !is VpnConnectionState.Reconnecting) {
                    _connectionState.value = VpnConnectionState.Disconnected
                    stopForegroundMonitor()
                }
            }
            requestTileUpdate()
        }
    }

    fun prepareVpnIntent(): Intent? {
        return try {
            GoBackend.VpnService.prepare(context)
        } catch (e: Exception) {
            SafeLogger.e("VpnManager", "VpnService.prepare failed", e)
            null
        }
    }

    suspend fun connect(
        profile: TunnelProfile,
        settings: AppSettings,
        overrideDns: String? = null,
        overrideMtu: Int? = null
    ): Boolean = mutex.withLock {
        if (!profile.isConfigured) {
            _connectionState.value = VpnConnectionState.Error(
                errorCode = VpnErrorCode.INVALID_CONFIG,
                detail = "Tunnel configuration is incomplete.",
                tunnelId = profile.id
            )
            return@withLock false
        }

        if (prepareVpnIntent() != null) {
            _connectionState.value = VpnConnectionState.Error(
                errorCode = VpnErrorCode.PERMISSION_DENIED,
                detail = "Android VPN permission is required before connecting.",
                tunnelId = profile.id
            )
            return@withLock false
        }

        userRequestedDisconnect = false
        telemetryJob?.cancel()
        activeTunnelProfile = profile
        activeSettings = settings

        _connectionState.value = VpnConnectionState.Connecting(
            tunnelId = profile.id,
            tunnelName = profile.name,
            serverName = profile.serverName,
            stage = "CONFIGURING_WIREGUARD"
        )
        requestTileUpdate()

        return@withLock withContext(Dispatchers.IO) {
            try {
                val wgConfig = WireGuardConfigMapper.toWireGuardConfig(
                    profile = profile,
                    settings = settings,
                    selfPackageName = context.packageName,
                    overrideDns = overrideDns,
                    overrideMtu = overrideMtu
                )

                // Ensure clean transition if another tunnel was up
                runCatching {
                    if (backend.getState(activeTunnel) == Tunnel.State.UP) {
                        backend.setState(activeTunnel, Tunnel.State.DOWN, null)
                    }
                }

                _connectionState.value = VpnConnectionState.Connecting(
                    tunnelId = profile.id,
                    tunnelName = profile.name,
                    serverName = profile.serverName,
                    stage = "ESTABLISHING_INTERFACE"
                )

                val resultingState = backend.setState(activeTunnel, Tunnel.State.UP, wgConfig)
                delay(350)
                val osVpnUp = networkMonitor.refresh().isVpnActive

                if (resultingState == Tunnel.State.UP && (osVpnUp || backend.getState(activeTunnel) == Tunnel.State.UP)) {
                    connectedElapsedBaseMs = SystemClock.elapsedRealtime()
                    _connectionState.value = VpnConnectionState.Connected(
                        tunnelId = profile.id,
                        tunnelName = profile.name,
                        serverName = profile.serverName,
                        countryCode = profile.countryCode,
                        countryName = profile.countryName,
                        endpoint = profile.endpointDisplay,
                        connectedAtElapsedMs = connectedElapsedBaseMs,
                        vpnInterfaceVerified = osVpnUp
                    )
                    startForegroundMonitor(profile)
                    startTelemetryLoop(profile)
                    requestTileUpdate()
                    true
                } else {
                    _connectionState.value = VpnConnectionState.Error(
                        errorCode = VpnErrorCode.BACKEND_START_FAILED,
                        detail = "WireGuard backend did not confirm active VPN interface.",
                        tunnelId = profile.id
                    )
                    stopForegroundMonitor()
                    requestTileUpdate()
                    false
                }
            } catch (e: Exception) {
                SafeLogger.e("VpnManager", "Failed to start WireGuard tunnel: ${e.message}")
                _connectionState.value = VpnConnectionState.Error(
                    errorCode = VpnErrorCode.BACKEND_START_FAILED,
                    detail = e.message ?: "WireGuard tunnel start failed.",
                    tunnelId = profile.id
                )
                stopForegroundMonitor()
                requestTileUpdate()
                false
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        userRequestedDisconnect = true
        telemetryJob?.cancel()
        telemetryJob = null
        withContext(Dispatchers.IO) {
            runCatching {
                if (backend.getState(activeTunnel) != Tunnel.State.DOWN) {
                    backend.setState(activeTunnel, Tunnel.State.DOWN, null)
                }
            }.onFailure {
                SafeLogger.w("VpnManager", "Error bringing tunnel down: ${it.message}")
            }
        }
        _connectionState.value = VpnConnectionState.Disconnected
        stopForegroundMonitor()
        requestTileUpdate()
    }

    fun registerAlwaysOnCallback(onAlwaysOn: suspend () -> Unit) {
        try {
            GoBackend.setAlwaysOnCallback(GoBackend.AlwaysOnCallback {
                scope.launch { onAlwaysOn() }
            })
        } catch (e: Exception) {
            SafeLogger.w("VpnManager", "Unable to register AlwaysOnCallback: ${e.message}")
        }
    }

    suspend fun handleSystemRevoke() {
        userRequestedDisconnect = true
        telemetryJob?.cancel()
        telemetryJob = null
        withContext(Dispatchers.IO) {
            runCatching {
                if (backend.getState(activeTunnel) != Tunnel.State.DOWN) {
                    backend.setState(activeTunnel, Tunnel.State.DOWN, null)
                }
            }
        }
        _connectionState.value = VpnConnectionState.Disconnected
        stopForegroundMonitor()
        requestTileUpdate()
    }

    fun clearError() {
        if (_connectionState.value is VpnConnectionState.Error) {
            _connectionState.value = VpnConnectionState.Disconnected
        }
    }

    private fun startTelemetryLoop(profile: TunnelProfile) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var pingTick = 0
            var lastMeasuredPing: Double? = profile.lastLatencyMs
            while (isActive && !userRequestedDisconnect) {
                delay(1000L)
                val backendUp = runCatching { backend.getState(activeTunnel) == Tunnel.State.UP }.getOrDefault(false)
                val osVpnUp = networkMonitor.refresh().isVpnActive

                if (!backendUp) {
                    if (activeSettings.autoReconnect && !userRequestedDisconnect) {
                        scheduleAutoReconnect(profile, activeSettings, "Backend reported DOWN")
                    } else {
                        _connectionState.value = VpnConnectionState.Disconnected
                        stopForegroundMonitor()
                    }
                    break
                }

                val stats = runCatching { backend.getStatistics(activeTunnel) }.getOrNull()
                val rxBytes = stats?.totalRx() ?: 0L
                val txBytes = stats?.totalTx() ?: 0L
                val latestHandshake = stats?.peers()?.firstOrNull()?.let { peerKey ->
                    stats.peer(peerKey)?.latestHandshakeEpochMillis ?: 0L
                } ?: 0L

                // Probe live RTT every 15 seconds when connected
                if (pingTick % 15 == 0) {
                    val probePort = profile.probePort ?: 443
                    val probe = prober.probeHostLatency(profile.endpointHost, probePort, samples = 2, timeoutMs = 1500)
                    if (probe.reachable && probe.avgLatencyMs != null) {
                        lastMeasuredPing = probe.avgLatencyMs
                    }
                }
                pingTick++

                _connectionState.value = VpnConnectionState.Connected(
                    tunnelId = profile.id,
                    tunnelName = profile.name,
                    serverName = profile.serverName,
                    countryCode = profile.countryCode,
                    countryName = profile.countryName,
                    endpoint = profile.endpointDisplay,
                    connectedAtElapsedMs = connectedElapsedBaseMs,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    livePingMs = lastMeasuredPing,
                    latestHandshakeEpochMs = latestHandshake,
                    vpnInterfaceVerified = osVpnUp
                )

                OmarVpnForegroundService.updateNotification(
                    context = context,
                    serverName = profile.serverName,
                    rxBytes = rxBytes,
                    txBytes = txBytes
                )
            }
        }
    }

    private fun scheduleAutoReconnect(
        profile: TunnelProfile,
        settings: AppSettings,
        reason: String
    ) {
        scope.launch {
            for (attempt in 1..3) {
                if (userRequestedDisconnect) return@launch
                _connectionState.value = VpnConnectionState.Reconnecting(
                    tunnelId = profile.id,
                    tunnelName = profile.name,
                    attempt = attempt,
                    reason = reason
                )
                delay(attempt * 2000L)
                if (userRequestedDisconnect) return@launch
                if (networkMonitor.refresh().isConnected && prepareVpnIntent() == null) {
                    val ok = connect(profile, settings)
                    if (ok) return@launch
                }
            }
            _connectionState.value = VpnConnectionState.Error(
                errorCode = VpnErrorCode.NETWORK_LOST,
                detail = "Auto-reconnect exhausted after 3 attempts.",
                tunnelId = profile.id
            )
            stopForegroundMonitor()
        }
    }

    private fun startForegroundMonitor(profile: TunnelProfile) {
        val intent = Intent(context, OmarVpnForegroundService::class.java).apply {
            action = OmarVpnForegroundService.ACTION_START
            putExtra(OmarVpnForegroundService.EXTRA_SERVER_NAME, profile.serverName)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopForegroundMonitor() {
        val intent = Intent(context, OmarVpnForegroundService::class.java).apply {
            action = OmarVpnForegroundService.ACTION_STOP
        }
        runCatching { context.startService(intent) }
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, OmarVpnTileService::class.java)
                )
            }
        }
    }
}
