package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import com.example.R
import com.example.core.datastore.AppLanguage
import com.example.core.datastore.AppSettings
import com.example.core.util.Formatters
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.TunnelProfile
import com.example.domain.model.VpnConnectionState
import com.example.ui.components.NeonConnectButton
import com.example.ui.components.OmarCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.TelemetryStatTile
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vpnState: VpnConnectionState,
    settings: AppSettings,
    tunnels: List<TunnelProfile>,
    networkSnapshot: ActiveNetworkSnapshot,
    isAutoProbing: Boolean,
    onConnectClick: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenEgyptNetworks: () -> Unit,
    onOpenHomeBoost: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val configuredTunnels = remember(tunnels) { tunnels.filter { it.isConfigured } }
    val selectedTunnel = remember(tunnels, settings.selectedTunnelId) {
        settings.selectedTunnelId?.let { id -> tunnels.find { it.id == id } }
            ?: configuredTunnels.firstOrNull()
    }

    var nowElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(vpnState) {
        while (vpnState is VpnConnectionState.Connected) {
            nowElapsedMs = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_omar_vpn_logo),
                        contentDescription = strings.appTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (networkSnapshot.isConnected) {
                                "${networkSnapshot.transport.name} • ${networkSnapshot.carrierDisplayName.ifBlank { "Online" }}"
                            } else {
                                "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.testTag("language_toggle_button")
                    ) {
                        Text(
                            text = if (strings.isArabic) "EN" else "AR",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonEmerald,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = strings.settingsTitle,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Real Connection Status Badge
            val (statusText, statusColor) = when (vpnState) {
                is VpnConnectionState.Connected -> strings.statusConnected to NeonEmerald
                is VpnConnectionState.Connecting -> strings.statusConnecting to WarningAmber
                is VpnConnectionState.Reconnecting -> strings.statusReconnecting to WarningAmber
                is VpnConnectionState.Error -> strings.statusError to ErrorCoral
                is VpnConnectionState.Disconnected -> strings.statusDisconnected to TextMuted
            }

            StatusBadge(
                text = statusText,
                color = statusColor,
                modifier = Modifier.testTag("vpn_status_badge")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Central Connect Ring
            NeonConnectButton(
                vpnState = vpnState,
                onClick = onConnectClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            val subStatusText = when (vpnState) {
                is VpnConnectionState.Connected -> {
                    if (vpnState.handshakeVerified) strings.handshakeVerified else strings.handshakePending
                }
                is VpnConnectionState.Connecting -> vpnState.stage.replace('_', ' ')
                is VpnConnectionState.Reconnecting -> "${vpnState.reason} (#${vpnState.attempt})"
                is VpnConnectionState.Error -> vpnState.detail
                is VpnConnectionState.Disconnected -> {
                    if (isAutoProbing) strings.runningDiagnostics else strings.tapToConnect
                }
            }

            Text(
                text = subStatusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (vpnState is VpnConnectionState.Error) ErrorCoral else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (vpnState is VpnConnectionState.Error) {
                TextButton(onClick = onDismissError) {
                    Text(text = strings.cancel, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Server / AUTO Selector Card
            OmarCard(
                onClick = onOpenServers,
                borderColor = if (vpnState is VpnConnectionState.Connected) {
                    NeonEmerald.copy(alpha = 0.55f)
                } else {
                    ObsidianBorder
                },
                modifier = Modifier.testTag("server_selector_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (settings.autoServerMode && vpnState !is VpnConnectionState.Connected) {
                                    Icons.Outlined.AutoAwesome
                                } else {
                                    Icons.Outlined.Public
                                },
                                contentDescription = null,
                                tint = NeonEmerald
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            val title = when {
                                vpnState is VpnConnectionState.Connected -> {
                                    "${vpnState.countryCode} • ${vpnState.serverName}"
                                }
                                settings.autoServerMode -> strings.autoServerLabel
                                selectedTunnel != null -> {
                                    "${selectedTunnel.countryCode} • ${selectedTunnel.serverName}"
                                }
                                else -> strings.noServersConfiguredTitle
                            }
                            val subtitle = when {
                                vpnState is VpnConnectionState.Connected -> {
                                    "${vpnState.countryName} • ${vpnState.endpoint}"
                                }
                                settings.autoServerMode -> {
                                    if (configuredTunnels.isEmpty()) {
                                        strings.noServersConfiguredBody
                                    } else {
                                        "${strings.autoServerSubtitle} (${configuredTunnels.size})"
                                    }
                                }
                                selectedTunnel != null -> {
                                    "${selectedTunnel.countryName} • ${selectedTunnel.endpointDisplay}"
                                }
                                else -> strings.addFirstServer
                            }

                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayPing = when (vpnState) {
                            is VpnConnectionState.Connected -> vpnState.livePingMs
                            else -> if (!settings.autoServerMode) selectedTunnel?.lastLatencyMs else null
                        }
                        if (displayPing != null) {
                            StatusBadge(
                                text = Formatters.formatLatency(displayPing),
                                color = NeonEmerald,
                                showDot = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            }

            // Live Connected Telemetry Grid (Duration, Ping, Download, Upload)
            AnimatedVisibility(visible = vpnState is VpnConnectionState.Connected) {
                if (vpnState is VpnConnectionState.Connected) {
                    val elapsed = (nowElapsedMs - vpnState.connectedAtElapsedMs).coerceAtLeast(0L)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                label = strings.durationLabel,
                                value = Formatters.formatDuration(elapsed),
                                icon = Icons.Rounded.Timer,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                label = strings.pingLabel,
                                value = Formatters.formatLatency(vpnState.livePingMs),
                                icon = Icons.Rounded.NetworkPing,
                                accent = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                label = strings.downloadLabel,
                                value = Formatters.formatBytes(vpnState.rxBytes),
                                icon = Icons.Rounded.ArrowDownward,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                label = strings.uploadLabel,
                                value = Formatters.formatBytes(vpnState.txBytes),
                                icon = Icons.Rounded.ArrowUpward,
                                accent = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Access Section: Egypt Mobile Networks & Home Internet Boost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OmarCard(
                    onClick = onOpenEgyptNetworks,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_egypt_card")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CellTower,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.egyptSectionTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.egyptSectionSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OmarCard(
                    onClick = onOpenHomeBoost,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_boost_card")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.homeBoostTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.homeBoostSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
