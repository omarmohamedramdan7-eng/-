package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmarVpnApplication
import com.example.core.datastore.AppLanguage
import com.example.core.datastore.AppSettings
import com.example.core.datastore.DnsPreset
import com.example.core.datastore.SplitTunnelMode
import com.example.core.util.InstalledAppsProvider
import com.example.data.local.DiagnosticHistoryEntity
import com.example.data.repository.CarrierRepository
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.CarrierDiagnosticState
import com.example.domain.model.EgyptCarrierId
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.OptimizationReport
import com.example.domain.model.OptimizationStep
import com.example.domain.model.ServerProbeResult
import com.example.domain.model.TunnelProfile
import com.example.domain.model.VpnConnectionState
import com.example.domain.model.VpnErrorCode
import com.example.domain.usecase.AutoServerEvaluation
import com.example.domain.wireguard.ConfigParseResult
import com.example.domain.wireguard.WgConfigParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutoServerUiState(
    val isRunning: Boolean = false,
    val evaluation: AutoServerEvaluation? = null,
    val statusNote: String = ""
)

data class BoostUiState(
    val isRunning: Boolean = false,
    val steps: List<OptimizationStep> = emptyList(),
    val report: OptimizationReport? = null,
    val error: String? = null
)

class OmarVpnViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OmarVpnApplication).container

    val vpnState: StateFlow<VpnConnectionState> = container.vpnManager.connectionState

    val settings: StateFlow<AppSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val tunnels: StateFlow<List<TunnelProfile>> = container.tunnelRepository.tunnelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkSnapshot: StateFlow<ActiveNetworkSnapshot> = container.networkMonitor.snapshot

    val diagnosticHistory: StateFlow<List<DiagnosticHistoryEntity>> =
        container.diagnosticsRepository.recentHistory
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val testingCarriers = MutableStateFlow<Set<EgyptCarrierId>>(emptySet())

    val carrierStates: StateFlow<List<CarrierDiagnosticState>> = combine(
        container.carrierRepository.observeCarrierStates(networkSnapshot),
        networkSnapshot,
        testingCarriers
    ) { savedStates, snapshot, testingSet ->
        savedStates.map { state ->
            state.copy(
                isDetectedOnSim = CarrierRepository.matchesCarrier(state.carrier, snapshot),
                isTesting = state.carrier in testingSet
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        EgyptCarrierId.entries.map { CarrierDiagnosticState(carrier = it) }
    )

    private val _tunnelsLoaded = MutableStateFlow(false)
    val tunnelsLoaded: StateFlow<Boolean> = _tunnelsLoaded.asStateFlow()

    private val _autoServerState = MutableStateFlow(AutoServerUiState())
    val autoServerState: StateFlow<AutoServerUiState> = _autoServerState.asStateFlow()

    private val _boostState = MutableStateFlow(BoostUiState())
    val boostState: StateFlow<BoostUiState> = _boostState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _probingTunnelIds = MutableStateFlow<Set<Long>>(emptySet())
    val probingTunnelIds: StateFlow<Set<Long>> = _probingTunnelIds.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private var pendingConnectTunnel: TunnelProfile? = null
    private var pendingOverrideDns: String? = null
    private var pendingOverrideMtu: Int? = null
    private var autoJob: Job? = null

    init {
        viewModelScope.launch {
            container.tunnelRepository.tunnelsFlow.collect {
                _tunnelsLoaded.value = true
            }
        }
    }

    fun consumeUiMessage() {
        _uiMessage.value = null
    }

    fun clearVpnError() {
        container.vpnManager.clearError()
    }

    fun prepareVpnPermission(): Intent? = container.vpnManager.prepareVpnIntent()

    /**
     * Primary Connect / Disconnect action from Home or Quick Actions.
     * Returns an Intent if Android VPN user consent dialog must be launched first.
     */
    fun onPrimaryConnectClick(onNeedVpnPermission: (Intent) -> Unit) {
        val current = vpnState.value
        if (current is VpnConnectionState.Connected ||
            current is VpnConnectionState.Connecting ||
            current is VpnConnectionState.Reconnecting
        ) {
            viewModelScope.launch {
                container.vpnManager.disconnect()
            }
            return
        }

        val currentSettings = settings.value
        if (currentSettings.autoServerMode) {
            runAutoServerAndConnect(onNeedVpnPermission)
        } else {
            val all = tunnels.value
            val chosen = currentSettings.selectedTunnelId?.let { id -> all.find { it.id == id } }
                ?: all.firstOrNull { it.isConfigured }
            if (chosen == null || !chosen.isConfigured) {
                _uiMessage.value = "NO_CONFIGURED_TUNNEL"
                return
            }
            connectToTunnel(chosen, onNeedVpnPermission = onNeedVpnPermission)
        }
    }

    fun connectToTunnel(
        tunnel: TunnelProfile,
        overrideDns: String? = null,
        overrideMtu: Int? = null,
        onNeedVpnPermission: (Intent) -> Unit
    ) {
        if (!tunnel.isConfigured) {
            _uiMessage.value = "INCOMPLETE_TUNNEL_CONFIG"
            return
        }
        val permIntent = container.vpnManager.prepareVpnIntent()
        if (permIntent != null) {
            pendingConnectTunnel = tunnel
            pendingOverrideDns = overrideDns
            pendingOverrideMtu = overrideMtu
            onNeedVpnPermission(permIntent)
            return
        }
        viewModelScope.launch {
            container.settingsRepository.setSelectedTunnelId(tunnel.id)
            container.vpnManager.connect(
                profile = tunnel,
                settings = settings.value,
                overrideDns = overrideDns,
                overrideMtu = overrideMtu
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val pending = pendingConnectTunnel
        val dns = pendingOverrideDns
        val mtu = pendingOverrideMtu
        pendingConnectTunnel = null
        pendingOverrideDns = null
        pendingOverrideMtu = null

        if (!granted) {
            _uiMessage.value = "VPN_PERMISSION_DENIED"
            return
        }
        if (pending != null) {
            viewModelScope.launch {
                container.settingsRepository.setSelectedTunnelId(pending.id)
                container.vpnManager.connect(pending, settings.value, dns, mtu)
            }
        }
    }

    fun runAutoServerAndConnect(
        onNeedVpnPermission: (Intent) -> Unit,
        connectBestAfterProbe: Boolean = true
    ) {
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            _autoServerState.value = AutoServerUiState(isRunning = true)
            val evaluation = container.autoServerSelector.evaluateAndSelect(tunnels.value)

            evaluation.rankedResults.forEach { result ->
                container.tunnelRepository.updateProbeMetrics(
                    id = result.tunnel.id,
                    latencyMs = result.latencyMs,
                    jitterMs = result.jitterMs,
                    packetLossPct = result.packetLossPct,
                    reachable = result.reachable
                )
            }

            _autoServerState.value = AutoServerUiState(
                isRunning = false,
                evaluation = evaluation
            )

            val best = evaluation.bestCandidate
            if (best == null) {
                _uiMessage.value = evaluation.failureReason ?: "NO_REACHABLE_SERVER"
                return@launch
            }

            if (connectBestAfterProbe) {
                connectToTunnel(best.tunnel, onNeedVpnPermission = onNeedVpnPermission)
            }
        }
    }

    fun probeSingleTunnel(tunnel: TunnelProfile) {
        if (tunnel.endpointHost.isBlank()) return
        viewModelScope.launch {
            _probingTunnelIds.value = _probingTunnelIds.value + tunnel.id
            val probePort = tunnel.probePort ?: 443
            val result = container.probeEngine.probeHostLatency(tunnel.endpointHost, probePort, samples = 3, timeoutMs = 2000)
            container.tunnelRepository.updateProbeMetrics(
                id = tunnel.id,
                latencyMs = result.avgLatencyMs,
                jitterMs = result.jitterMs,
                packetLossPct = result.packetLossPct,
                reachable = result.reachable
            )
            _probingTunnelIds.value = _probingTunnelIds.value - tunnel.id
        }
    }

    fun importWireGuardConfigText(
        rawConfig: String,
        customName: String,
        serverName: String,
        countryCode: String,
        countryName: String,
        carrierId: String? = null
    ): ConfigParseResult {
        val parsed = WgConfigParser.parse(rawConfig)
        if (parsed is ConfigParseResult.Success) {
            val cfg = parsed.config
            val resolvedName = customName.trim().ifEmpty { "WG-${cfg.endpointHost}" }
            val profile = TunnelProfile(
                name = resolvedName,
                serverName = serverName.trim().ifEmpty { resolvedName },
                countryCode = countryCode.trim().uppercase().ifEmpty { "UN" },
                countryName = countryName.trim().ifEmpty { "Custom Server" },
                interfaceAddresses = cfg.interfaceAddresses,
                dnsServers = cfg.dnsServers,
                mtu = cfg.mtu,
                listenPort = cfg.listenPort,
                privateKey = cfg.privateKey,
                derivedPublicKey = cfg.derivedPublicKey,
                peerPublicKey = cfg.peerPublicKey,
                presharedKey = cfg.presharedKey,
                endpointHost = cfg.endpointHost,
                endpointPort = cfg.endpointPort,
                allowedIps = cfg.allowedIps,
                persistentKeepalive = cfg.persistentKeepalive,
                carrierId = carrierId
            )
            viewModelScope.launch {
                val id = container.tunnelRepository.saveTunnel(profile)
                container.settingsRepository.setSelectedTunnelId(id)
                _uiMessage.value = "TUNNEL_SAVED"
            }
        }
        return parsed
    }

    fun saveTunnelProfile(profile: TunnelProfile) {
        viewModelScope.launch {
            val savedId = container.tunnelRepository.saveTunnel(profile)
            if (settings.value.selectedTunnelId == null) {
                container.settingsRepository.setSelectedTunnelId(savedId)
            }
            _uiMessage.value = "TUNNEL_SAVED"
        }
    }

    fun deleteTunnel(tunnelId: Long) {
        viewModelScope.launch {
            val current = vpnState.value
            if (current is VpnConnectionState.Connected && current.tunnelId == tunnelId) {
                container.vpnManager.disconnect()
            }
            container.tunnelRepository.deleteTunnel(tunnelId)
            if (settings.value.selectedTunnelId == tunnelId) {
                container.settingsRepository.setSelectedTunnelId(null)
            }
        }
    }

    fun runHomeInternetOptimize() {
        viewModelScope.launch {
            _boostState.value = BoostUiState(isRunning = true)
            val snapshot = container.networkMonitor.refresh()
            val currentSettings = settings.value

            val report = container.connectionOptimizer.runOptimization(
                snapshot = snapshot,
                configuredTunnels = tunnels.value,
                settings = currentSettings,
                onStepProgress = { steps ->
                    _boostState.value = _boostState.value.copy(steps = steps)
                }
            )

            // If VPN is NOT active during this diagnostic, update baseline for future Before/After VPN comparisons
            if (!snapshot.isVpnActive && report.pingResult?.reachable == true) {
                container.settingsRepository.saveBaselineMetrics(
                    pingMs = report.pingResult.avgLatencyMs,
                    jitterMs = report.pingResult.jitterMs,
                    downloadMbps = report.speedTestResult?.downloadMbps,
                    uploadMbps = report.speedTestResult?.uploadMbps,
                    packetLossPct = report.pingResult.packetLossPct
                )
            }

            container.diagnosticsRepository.recordReport(report)
            _boostState.value = BoostUiState(
                isRunning = false,
                steps = _boostState.value.steps,
                report = report
            )
        }
    }

    fun runCarrierDiagnostics(carrier: EgyptCarrierId) {
        viewModelScope.launch {
            testingCarriers.value = testingCarriers.value + carrier
            val snapshot = container.networkMonitor.refresh()
            val carrierConfig = carrierStates.value.firstOrNull { it.carrier == carrier }
            val assignedTunnel = carrierConfig?.assignedTunnelId?.let { id ->
                tunnels.value.find { it.id == id }
            }

            val targetHost = assignedTunnel?.endpointHost ?: "1.1.1.1"
            val targetPort = assignedTunnel?.probePort ?: 443

            val ping = container.probeEngine.probeHostLatency(targetHost, targetPort, samples = 3, timeoutMs = 2000)
            val dnsList = container.probeEngine.runDnsDiagnostics("cloudflare.com", includeDoh = true)
            val dnsOk = dnsList.any { it.success }

            val summary = buildString {
                if (ping.reachable) {
                    append("RTT ${ping.avgLatencyMs?.toInt()} ms • Loss ${ping.packetLossPct.toInt()}%")
                } else {
                    append("Probe unreachable ($targetHost:$targetPort)")
                }
                append(" • DNS ${if (dnsOk) "OK" else "Failed"}")
                if (!snapshot.isConnected) append(" • Offline")
            }

            container.carrierRepository.saveCarrierProbeResult(
                carrier = carrier,
                latencyMs = ping.avgLatencyMs,
                reachable = ping.reachable,
                summary = summary
            )
            testingCarriers.value = testingCarriers.value - carrier
        }
    }

    fun saveCarrierProfile(
        carrier: EgyptCarrierId,
        assignedTunnelId: Long?,
        preferredDns: String,
        customMtu: Int
    ) {
        viewModelScope.launch {
            container.carrierRepository.saveCarrierSettings(
                carrier = carrier,
                assignedTunnelId = assignedTunnelId,
                preferredDns = preferredDns,
                customMtu = customMtu
            )
            _uiMessage.value = "CARRIER_SAVED"
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.settingsRepository.setLanguage(language) }
    }

    fun setShowCyberSilhouette(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setShowCyberSilhouette(enabled) }
    }

    fun setAutoServerMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAutoServerMode(enabled) }
    }

    fun setSelectedTunnel(tunnelId: Long) {
        viewModelScope.launch {
            container.settingsRepository.setAutoServerMode(false)
            container.settingsRepository.setSelectedTunnelId(tunnelId)
        }
    }

    fun setDnsConfig(preset: DnsPreset, customDns: String) {
        viewModelScope.launch { container.settingsRepository.setDnsConfig(preset, customDns) }
    }

    fun setEnableDohDiagnostics(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setEnableDohDiagnostics(enabled) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAutoReconnect(enabled) }
    }

    fun setAutoConnectRules(onWifi: Boolean, onCellular: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAutoConnectRules(onWifi, onCellular) }
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty() || _appsLoading.value) return
        viewModelScope.launch {
            _appsLoading.value = true
            _installedApps.value = InstalledAppsProvider.getLaunchableApps(getApplication())
            _appsLoading.value = false
        }
    }

    fun updateSplitTunneling(mode: SplitTunnelMode, packages: Set<String>) {
        viewModelScope.launch {
            container.settingsRepository.setSplitTunneling(mode, packages)
        }
    }
}
