package com.example.vpn

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.OmarVpnApplication
import com.example.R
import com.example.domain.model.VpnConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class OmarVpnTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        syncTileWithState()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? OmarVpnApplication ?: return
        val container = app.container
        val currentState = container.vpnManager.connectionState.value

        val toggleAction = Runnable {
            scope.launch {
                when (currentState) {
                    is VpnConnectionState.Connected,
                    is VpnConnectionState.Connecting,
                    is VpnConnectionState.Reconnecting -> {
                        container.vpnManager.disconnect()
                        syncTileWithState()
                    }
                    else -> {
                        val settings = container.settingsRepository.settingsFlow.first()
                        val tunnels = container.tunnelRepository.getAllTunnels().filter { it.isConfigured }
                        val candidate = settings.selectedTunnelId?.let { id -> tunnels.find { it.id == id } }
                            ?: tunnels.firstOrNull()

                        if (candidate == null || container.vpnManager.prepareVpnIntent() != null) {
                            launchMainApp()
                        } else {
                            container.vpnManager.connect(candidate, settings)
                            syncTileWithState()
                        }
                    }
                }
            }
        }

        if (isLocked && isSecure) {
            unlockAndRun(toggleAction)
        } else {
            toggleAction.run()
        }
    }

    private fun launchMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this,
                200,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun syncTileWithState() {
        val tile = qsTile ?: return
        val app = applicationContext as? OmarVpnApplication
        val state = app?.container?.vpnManager?.connectionState?.value ?: VpnConnectionState.Disconnected

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        when (state) {
            is VpnConnectionState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = state.serverName.ifBlank { getString(R.string.qs_tile_label) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = state.countryCode
                }
            }
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Reconnecting -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.qs_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "..."
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.qs_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = null
                }
            }
        }
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}
