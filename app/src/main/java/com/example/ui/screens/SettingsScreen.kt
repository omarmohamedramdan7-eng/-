package com.example.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.datastore.AppLanguage
import com.example.core.datastore.AppSettings
import com.example.core.datastore.DnsPreset
import com.example.ui.components.OmarCard
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.vpn.OmarVpnTileService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetCyberSilhouette: (Boolean) -> Unit,
    onSetDnsConfig: (DnsPreset, String) -> Unit,
    onSetDohDiagnostics: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onSetAutoConnectRules: (Boolean, Boolean) -> Unit,
    onOpenSplitTunneling: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    var showLicensesDialog by remember { mutableStateOf(false) }
    var customDnsInput by remember(settings.customDnsServers) { mutableStateOf(settings.customDnsServers) }
    var tileFeedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 650.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Split Tunneling Entry
                item {
                    OmarCard(
                        onClick = onOpenSplitTunneling,
                        borderColor = ObsidianBorder,
                        modifier = Modifier.testTag("open_split_tunneling")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Apps, contentDescription = null, tint = NeonEmerald)
                                Column {
                                    Text(
                                        text = strings.splitTunnelingTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${settings.splitTunnelMode.name} (${settings.splitTunnelPackages.size})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }

                // DNS Configuration
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = NeonEmerald)
                            Text(
                                text = strings.dnsConfigTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DnsPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = settings.dnsPreset == preset,
                                    onClick = { onSetDnsConfig(preset, customDnsInput) },
                                    label = { Text(preset.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonEmerald
                                    )
                                )
                            }
                        }

                        if (settings.dnsPreset == DnsPreset.CUSTOM) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customDnsInput,
                                onValueChange = { customDnsInput = it },
                                label = { Text("Custom DNS (e.g. 1.1.1.1, 9.9.9.9)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onSetDnsConfig(DnsPreset.CUSTOM, customDnsInput) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonEmerald,
                                    contentColor = ObsidianBlack
                                )
                            ) {
                                Text(strings.save, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            label = "DNS-over-HTTPS (DoH) Diagnostics",
                            checked = settings.enableDohDiagnostics,
                            onCheckedChange = onSetDohDiagnostics
                        )
                    }
                }

                // Automation, Always-on VPN & Quick Settings Tile
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = NeonEmerald)
                            Text(
                                text = strings.automationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingSwitchRow(
                            label = strings.autoReconnectLabel,
                            checked = settings.autoReconnect,
                            onCheckedChange = onSetAutoReconnect
                        )
                        SettingSwitchRow(
                            label = strings.autoConnectWifiLabel,
                            checked = settings.autoConnectOnUnsecuredWifi,
                            onCheckedChange = { onSetAutoConnectRules(it, settings.autoConnectOnMobileData) }
                        )
                        SettingSwitchRow(
                            label = strings.autoConnectCellularLabel,
                            checked = settings.autoConnectOnMobileData,
                            onCheckedChange = { onSetAutoConnectRules(settings.autoConnectOnUnsecuredWifi, it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                                    }
                                },
                                border = BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.alwaysOnVpnAction, maxLines = 1)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(
                                    onClick = {
                                        val sbm = context.getSystemService(StatusBarManager::class.java)
                                        sbm?.requestAddTileService(
                                            ComponentName(context, OmarVpnTileService::class.java),
                                            context.getString(R.string.qs_tile_label),
                                            Icon.createWithResource(context, R.drawable.ic_qs_tile),
                                            ContextCompat.getMainExecutor(context)
                                        ) { status ->
                                            tileFeedback = if (status == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                                status == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                            ) {
                                                "Quick Settings Tile ready."
                                            } else {
                                                "Tile request status: $status"
                                            }
                                        }
                                    },
                                    border = BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Text(strings.quickSettingsTileAction)
                                }
                            }
                        }
                        if (tileFeedback != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tileFeedback!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald
                            )
                        }
                    }
                }

                // Appearance & Language
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Translate, contentDescription = null, tint = NeonEmerald)
                            Text(
                                text = strings.appearanceLanguageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                val label = when (lang) {
                                    AppLanguage.SYSTEM -> "System"
                                    AppLanguage.ARABIC -> "العربية"
                                    AppLanguage.ENGLISH -> "English"
                                }
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { onSetLanguage(lang) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                                        selectedLabelColor = NeonEmerald
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            label = strings.cyberSilhouetteToggle,
                            checked = settings.showCyberSilhouette,
                            onCheckedChange = onSetCyberSilhouette
                        )
                    }
                }

                // Privacy & Security
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = NeonEmerald)
                            Text(
                                text = strings.privacySecurityTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.privacyBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Open Source Attribution
                item {
                    OmarCard(onClick = { showLicensesDialog = true }, borderColor = ObsidianBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = NeonEmerald)
                                Text(
                                    text = strings.openSourceLicensesTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = ObsidianSurface,
            title = { Text(strings.openSourceLicensesTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• WireGuard Android Tunnel Library (com.wireguard.android:tunnel) — Apache License 2.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Inspired by open-source WireGuard Android engineering patterns (including WG Tunnel, MIT/GPLv3 reference concepts; all OMAR VPN code written independently).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "• ZXing Core — Apache License 2.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• OkHttp, Jetpack Compose, Room, DataStore, CameraX — Apache License 2.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text(strings.cancel, color = NeonEmerald)
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = NeonEmerald
            )
        )
    }
}
