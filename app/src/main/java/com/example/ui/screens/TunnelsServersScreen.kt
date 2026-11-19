package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.datastore.AppSettings
import com.example.core.util.Formatters
import com.example.core.util.QrConfigDecoder
import com.example.domain.model.ServerAvailability
import com.example.domain.model.TunnelProfile
import com.example.domain.model.VpnConnectionState
import com.example.domain.wireguard.ConfigParseResult
import com.example.ui.components.EmptyServersCard
import com.example.ui.components.OmarCard
import com.example.ui.components.SkeletonCardList
import com.example.ui.components.StatusBadge
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AutoServerUiState

@Composable
fun TunnelsServersScreen(
    tunnels: List<TunnelProfile>,
    tunnelsLoaded: Boolean,
    settings: AppSettings,
    vpnState: VpnConnectionState,
    autoServerState: AutoServerUiState,
    probingTunnelIds: Set<Long>,
    onSelectAutoMode: (Boolean) -> Unit,
    onSelectTunnel: (Long) -> Unit,
    onRunAutoServer: () -> Unit,
    onConnectTunnel: (TunnelProfile) -> Unit,
    onDisconnect: () -> Unit,
    onProbeTunnel: (TunnelProfile) -> Unit,
    onSaveTunnel: (TunnelProfile) -> Unit,
    onDeleteTunnel: (Long) -> Unit,
    onImportRawConfig: (String, String, String, String, String) -> ConfigParseResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastePrefill by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }
    var editingTunnel by remember { mutableStateOf<TunnelProfile?>(null) }
    var previewingTunnel by remember { mutableStateOf<TunnelProfile?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    // SAF .conf file importer (zero storage permission needed)
    val confFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content.isNullOrBlank()) {
                importError = "Could not read selected .conf file."
            } else {
                val res = onImportRawConfig(content, "", "", "UN", "Imported")
                if (res is ConfigParseResult.Error) {
                    pastePrefill = content
                    showPasteDialog = true
                    importError = res.reason
                } else {
                    importError = null
                }
            }
        }
    }

    // Photo Picker QR image importer (zero storage permission needed)
    val qrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val decoded = QrConfigDecoder.decodeFromUri(context, uri)
            if (decoded.isNullOrBlank()) {
                importError = "No valid WireGuard QR code found in selected image."
            } else {
                pastePrefill = decoded
                showPasteDialog = true
                importError = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 650.dp)
                .fillMaxSize()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianSurface,
                contentColor = NeonEmerald,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NeonEmerald
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab_servers"),
                    text = { Text(strings.serversTab, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_tunnels"),
                    text = { Text(strings.tunnelsTab, fontWeight = FontWeight.SemiBold) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                // Action toolbar for importing & creating tunnels/servers
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                editingTunnel = null
                                showManualDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = ObsidianBlack
                            ),
                            modifier = Modifier.testTag("add_manual_tunnel_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.addManualTunnel, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                confFileLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                            },
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.testTag("import_conf_button")
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.importConfFile)
                        }

                        OutlinedButton(
                            onClick = {
                                pastePrefill = ""
                                showPasteDialog = true
                            },
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.testTag("paste_conf_button")
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.pasteConfText)
                        }

                        OutlinedButton(
                            onClick = { showQrScanner = true },
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.scanQrCamera)
                        }

                        OutlinedButton(
                            onClick = {
                                qrImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Icon(Icons.Outlined.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.importQrImage)
                        }
                    }
                }

                if (importError != null) {
                    item {
                        OmarCard(borderColor = ErrorCoral.copy(alpha = 0.6f)) {
                            Text(
                                text = importError!!,
                                color = ErrorCoral,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // AUTO SERVER Card on Servers Tab
                if (selectedTab == 0) {
                    item {
                        OmarCard(
                            borderColor = if (settings.autoServerMode) NeonEmerald.copy(alpha = 0.6f) else ObsidianBorder
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = NeonEmerald)
                                    Column {
                                        Text(
                                            text = strings.autoServerLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = strings.autoServerSubtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                FilterChip(
                                    selected = settings.autoServerMode,
                                    onClick = { onSelectAutoMode(!settings.autoServerMode) },
                                    label = {
                                        Text(if (settings.autoServerMode) "AUTO ON" else "MANUAL")
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonEmerald
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onRunAutoServer,
                                    enabled = !autoServerState.isRunning && tunnels.any { it.isConfigured },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonEmerald,
                                        contentColor = ObsidianBlack
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("run_auto_server_button")
                                ) {
                                    if (autoServerState.isRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ObsidianBlack
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(strings.runAutoServer, fontWeight = FontWeight.Bold)
                                }
                            }

                            autoServerState.evaluation?.let { eval ->
                                Spacer(modifier = Modifier.height(10.dp))
                                val summary = if (eval.bestCandidate != null) {
                                    "Tested ${eval.candidatesTested} • Reachable ${eval.reachableCount} • Best: ${eval.bestCandidate.tunnel.serverName} (${Formatters.formatLatency(eval.bestCandidate.latencyMs)})"
                                } else {
                                    "Tested ${eval.candidatesTested} • Reachable 0 (${eval.failureReason ?: "No server reachable"})"
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (eval.bestCandidate != null) NeonEmerald else WarningAmber
                                )
                            }
                        }
                    }
                }

                // Skeleton / Empty / List
                if (!tunnelsLoaded) {
                    item { SkeletonCardList(count = 3) }
                } else if (tunnels.isEmpty()) {
                    item {
                        EmptyServersCard(
                            title = strings.noServersConfiguredTitle,
                            body = strings.noServersConfiguredBody,
                            actionLabel = strings.addFirstServer,
                            onActionClick = {
                                editingTunnel = null
                                showManualDialog = true
                            }
                        )
                    }
                } else {
                    items(tunnels, key = { it.id }) { profile ->
                        val isConnectedToThis =
                            vpnState is VpnConnectionState.Connected && vpnState.tunnelId == profile.id
                        val isSelected = settings.selectedTunnelId == profile.id && !settings.autoServerMode
                        val isProbing = profile.id in probingTunnelIds

                        TunnelServerItemCard(
                            profile = profile,
                            isServerTab = selectedTab == 0,
                            isSelected = isSelected,
                            isConnected = isConnectedToThis,
                            isProbing = isProbing,
                            onSelect = { onSelectTunnel(profile.id) },
                            onConnectOrDisconnect = {
                                if (isConnectedToThis) onDisconnect() else onConnectTunnel(profile)
                            },
                            onProbe = { onProbeTunnel(profile) },
                            onPreviewConfig = { previewingTunnel = profile },
                            onEdit = {
                                editingTunnel = profile
                                showManualDialog = true
                            },
                            onDelete = { onDeleteTunnel(profile.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showPasteDialog) {
        PasteConfigDialog(
            initialRawText = pastePrefill,
            onDismiss = { showPasteDialog = false },
            onImport = onImportRawConfig
        )
    }

    if (showManualDialog) {
        ManualTunnelEditorDialog(
            existing = editingTunnel,
            onDismiss = { showManualDialog = false },
            onSave = onSaveTunnel
        )
    }

    previewingTunnel?.let { tunnel ->
        SafeConfigPreviewDialog(
            profile = tunnel,
            onDismiss = { previewingTunnel = null }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onQrDecoded = { decoded ->
                showQrScanner = false
                pastePrefill = decoded
                showPasteDialog = true
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
private fun TunnelServerItemCard(
    profile: TunnelProfile,
    isServerTab: Boolean,
    isSelected: Boolean,
    isConnected: Boolean,
    isProbing: Boolean,
    onSelect: () -> Unit,
    onConnectOrDisconnect: () -> Unit,
    onProbe: () -> Unit,
    onPreviewConfig: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val (availText, availColor) = when (profile.availability) {
        ServerAvailability.REACHABLE -> strings.reachableBadge to NeonEmerald
        ServerAvailability.UNREACHABLE -> strings.unreachableBadge to ErrorCoral
        ServerAvailability.CONFIGURED_UNTESTED -> strings.configuredBadge to CyberCyan
        ServerAvailability.UNCONFIGURED -> strings.unconfiguredBadge to WarningAmber
    }

    OmarCard(
        onClick = onSelect,
        borderColor = when {
            isConnected -> NeonEmerald
            isSelected -> NeonEmerald.copy(alpha = 0.5f)
            else -> ObsidianBorder
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianElevated,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Text(
                        text = profile.countryCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonEmerald,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isServerTab) profile.serverName else profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.countryName} • ${profile.protocol} • ${profile.endpointDisplay}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            StatusBadge(text = availText, color = availColor)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Measured probe telemetry row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${strings.pingLabel}: ${Formatters.formatLatency(profile.lastLatencyMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (profile.lastLatencyMs != null) NeonEmerald else TextMuted
            )
            Text(
                text = "${strings.jitterLabel}: ${Formatters.formatLatency(profile.lastJitterMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = "${strings.packetLossLabel}: ${Formatters.formatPercent(profile.lastPacketLossPct)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnectOrDisconnect,
                    enabled = profile.isConfigured,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) ErrorCoral else NeonEmerald,
                        contentColor = ObsidianBlack
                    )
                ) {
                    Text(
                        text = if (isConnected) strings.disconnect else strings.connect,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onProbe,
                    enabled = !isProbing && profile.endpointHost.isNotBlank(),
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.testServer)
                }
            }

            Row {
                IconButton(onClick = onPreviewConfig) {
                    Icon(Icons.Outlined.Visibility, contentDescription = "View config", tint = TextSecondary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = strings.edit, tint = TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = strings.delete, tint = ErrorCoral)
                }
            }
        }
    }
}
