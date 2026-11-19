package com.example.core.di

import android.content.Context
import com.example.core.crypto.AndroidKeyStoreCipher
import com.example.core.crypto.KeyCipher
import com.example.core.datastore.SettingsRepository
import com.example.data.local.AppDatabase
import com.example.data.repository.CarrierRepository
import com.example.data.repository.DiagnosticsRepository
import com.example.data.repository.TunnelRepository
import com.example.domain.usecase.AutoServerSelector
import com.example.domain.usecase.ConnectionOptimizer
import com.example.network.ActiveNetworkMonitor
import com.example.network.NetworkProbeEngine
import com.example.vpn.OmarVpnManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val keyCipher: KeyCipher = AndroidKeyStoreCipher()
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val tunnelRepository: TunnelRepository = TunnelRepository(
        tunnelDao = database.tunnelDao(),
        keyCipher = keyCipher
    )
    val carrierRepository: CarrierRepository = CarrierRepository(
        carrierConfigDao = database.carrierConfigDao()
    )
    val diagnosticsRepository: DiagnosticsRepository = DiagnosticsRepository(
        diagnosticDao = database.diagnosticDao()
    )

    val networkMonitor: ActiveNetworkMonitor = ActiveNetworkMonitor(appContext)
    val probeEngine: NetworkProbeEngine = NetworkProbeEngine()
    val autoServerSelector: AutoServerSelector = AutoServerSelector(probeEngine)
    val connectionOptimizer: ConnectionOptimizer = ConnectionOptimizer(probeEngine, autoServerSelector)

    val vpnManager: OmarVpnManager = OmarVpnManager(
        context = appContext,
        networkMonitor = networkMonitor,
        prober = probeEngine
    )
}
