package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.util.Formatters
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.CarrierDiagnosticState
import com.example.domain.model.EgyptCarrierId
import com.example.domain.model.TunnelProfile
import com.example.ui.components.OmarCard
import com.example.ui.components.StatusBadge
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun EgyptNetworksScreen(
    carrierStates: List<CarrierDiagnosticState>,
    tunnels: List<TunnelProfile>,
    networkSnapshot: ActiveNetworkSnapshot,
    onRunCarrierDiagnostics: (EgyptCarrierId) -> Unit,
    onSaveCarrierProfile: (EgyptCarrierId, Long?, String, Int) -> Unit,
    onConnectAuthorizedTunnel: (TunnelProfile, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var editingCarrier by remember { mutableStateOf<CarrierDiagnosticState?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 650.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.egyptSectionTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.egyptSectionSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Compliance & Transparency Notice
            item {
                OmarCard(borderColor = CyberCyan.copy(alpha = 0.45f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = strings.egyptComplianceNotice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${networkSnapshot.transport.name} • ${networkSnapshot.carrierDisplayName.ifBlank { "No Cellular Operator Detected" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald
                            )
                        }
                    }
                }
            }

            items(carrierStates, key = { it.carrier.id }) { state ->
                val linkedTunnel = state.assignedTunnelId?.let { id ->
                    tunnels.find { it.id == id }
                }
                val accentColor = when (state.carrier) {
                    EgyptCarrierId.VODAFONE -> ErrorCoral
                    EgyptCarrierId.ORANGE -> com.example.ui.theme.WarningAmber
                    EgyptCarrierId.ETISALAT -> NeonEmerald
                    EgyptCarrierId.WE -> CyberCyan
                }

                OmarCard(
                    borderColor = if (state.isDetectedOnSim) NeonEmerald.copy(alpha = 0.65f) else ObsidianBorder
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
                            Icon(
                                imageVector = Icons.Outlined.CellTower,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = if (strings.isArabic) state.carrier.displayNameAr else state.carrier.displayNameEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "DNS: ${state.preferredDns} • MTU: ${state.customMtu}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (state.isDetectedOnSim) {
                            StatusBadge(text = strings.simDetectedBadge, color = NeonEmerald)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real Diagnostic Result
                    Text(
                        text = if (state.lastTestSummary.isNotBlank()) {
                            "${state.lastTestSummary} (${Formatters.formatLatency(state.lastTestLatencyMs)})"
                        } else {
                            if (strings.isArabic) "لم يتم تشغيل فحص التشخيص بعد" else "No diagnostics run yet"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (state.lastTestReachable) {
                            true -> NeonEmerald
                            false -> ErrorCoral
                            null -> TextMuted
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (linkedTunnel != null) {
                            "WireGuard: ${linkedTunnel.name} (${linkedTunnel.endpointDisplay})"
                        } else {
                            strings.noAssignedCarrierConfig
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRunCarrierDiagnostics(state.carrier) },
                            enabled = !state.isTesting,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.runCarrierTest, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { editingCarrier = state },
                            border = BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.carrierSettingsBtn)
                        }
                    }

                    if (linkedTunnel != null && linkedTunnel.isConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onConnectAuthorizedTunnel(linkedTunnel, state.preferredDns, state.customMtu)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = ObsidianBlack
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.connectAuthorizedConfig, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    editingCarrier?.let { state ->
        CarrierProfileDialog(
            state = state,
            availableTunnels = tunnels.filter { it.isConfigured },
            onDismiss = { editingCarrier = null },
            onSave = { tunnelId, dns, mtu ->
                onSaveCarrierProfile(state.carrier, tunnelId, dns, mtu)
                editingCarrier = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierProfileDialog(
    state: CarrierDiagnosticState,
    availableTunnels: List<TunnelProfile>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, Int) -> Unit
) {
    val strings = LocalStrings.current
    var selectedTunnelId by remember { mutableStateOf(state.assignedTunnelId) }
    var preferredDns by remember { mutableStateOf(state.preferredDns) }
    var mtuText by remember { mutableStateOf(state.customMtu.toString()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedTunnelName = availableTunnels.find { it.id == selectedTunnelId }?.name
        ?: strings.noAssignedCarrierConfig

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (strings.isArabic) state.carrier.displayNameAr else state.carrier.displayNameEn,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTunnelName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (strings.isArabic) "نفق WireGuard المصرح به" else "Authorized WireGuard Tunnel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.noAssignedCarrierConfig) },
                            onClick = {
                                selectedTunnelId = null
                                dropdownExpanded = false
                            }
                        )
                        availableTunnels.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.name} (${t.endpointDisplay})") },
                                onClick = {
                                    selectedTunnelId = t.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = preferredDns,
                    onValueChange = { preferredDns = it },
                    label = { Text("Preferred DNS (e.g. 1.1.1.1, 9.9.9.9)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mtuText,
                    onValueChange = { mtuText = it },
                    label = { Text("MTU (1280 - 1500)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mtu = mtuText.toIntOrNull()?.coerceIn(1280, 1500) ?: 1380
                    onSave(selectedTunnelId, preferredDns, mtu)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = ObsidianBlack
                )
            ) {
                Text(strings.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = TextSecondary)
            }
        }
    )
}
