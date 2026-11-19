package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.TunnelProfile
import com.example.domain.wireguard.ConfigParseResult
import com.example.domain.wireguard.WgConfigParser
import com.example.domain.wireguard.WgKeyGenerator
import com.example.domain.wireguard.WgKeyValidator
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

@Composable
fun PasteConfigDialog(
    initialRawText: String = "",
    onDismiss: () -> Unit,
    onImport: (rawConfig: String, name: String, serverName: String, countryCode: String, countryName: String) -> ConfigParseResult
) {
    val strings = LocalStrings.current
    var rawText by remember(initialRawText) { mutableStateOf(initialRawText) }
    var name by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("EG") }
    var countryName by remember { mutableStateOf("Custom") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(strings.pasteConfText, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (strings.isArabic) "اسم النفق" else "Tunnel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(3).uppercase() },
                        label = { Text(if (strings.isArabic) "رمز الدولة" else "Code") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f)
                    )
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it },
                        label = { Text(if (strings.isArabic) "الدولة" else "Country") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text(if (strings.isArabic) "اسم السيرفر" else "Server Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        errorMsg = null
                    },
                    label = { Text("[Interface] ... [Peer] ...") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = onImport(rawText, name, serverName, countryCode, countryName)
                    if (res is ConfigParseResult.Error) {
                        errorMsg = res.reason
                    } else {
                        onDismiss()
                    }
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

@Composable
fun ManualTunnelEditorDialog(
    existing: TunnelProfile?,
    onDismiss: () -> Unit,
    onSave: (TunnelProfile) -> Unit
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var serverName by remember { mutableStateOf(existing?.serverName.orEmpty()) }
    var countryCode by remember { mutableStateOf(existing?.countryCode ?: "DE") }
    var countryName by remember { mutableStateOf(existing?.countryName ?: "Germany") }
    var privateKey by remember { mutableStateOf(existing?.privateKey.orEmpty()) }
    var derivedPublicKey by remember { mutableStateOf(existing?.derivedPublicKey.orEmpty()) }
    var addresses by remember { mutableStateOf(existing?.interfaceAddresses ?: "10.0.0.2/32") }
    var dns by remember { mutableStateOf(existing?.dnsServers ?: "1.1.1.1") }
    var mtu by remember { mutableStateOf((existing?.mtu ?: 1420).toString()) }
    var peerPublicKey by remember { mutableStateOf(existing?.peerPublicKey.orEmpty()) }
    var presharedKey by remember { mutableStateOf(existing?.presharedKey.orEmpty()) }
    var endpointHost by remember { mutableStateOf(existing?.endpointHost.orEmpty()) }
    var endpointPort by remember { mutableStateOf((existing?.endpointPort ?: 51820).toString()) }
    var probePort by remember { mutableStateOf(existing?.probePort?.toString().orEmpty()) }
    var allowedIps by remember { mutableStateOf(existing?.allowedIps ?: "0.0.0.0/0, ::/0") }
    var keepalive by remember { mutableStateOf((existing?.persistentKeepalive ?: 25).toString()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (existing == null) strings.addManualTunnel else strings.edit,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (strings.isArabic) "اسم النفق / السيرفر" else "Tunnel / Server Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(3).uppercase() },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f)
                    )
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it },
                        label = { Text(if (strings.isArabic) "الدولة" else "Country") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }

                // Private Key + Generate Real Curve25519 KeyPair
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = {
                        privateKey = it
                        derivedPublicKey = WgKeyGenerator.derivePublicKey(it)
                    },
                    label = { Text("Interface PrivateKey (Base64)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val (priv, pub) = WgKeyGenerator.generateKeyPair()
                            privateKey = priv
                            derivedPublicKey = pub
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.generateKeys, style = MaterialTheme.typography.labelSmall)
                    }
                    if (derivedPublicKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(derivedPublicKey)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.copyPublicKey, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (derivedPublicKey.isNotBlank()) {
                    Text(
                        text = "Pub: $derivedPublicKey",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                OutlinedTextField(
                    value = addresses,
                    onValueChange = { addresses = it },
                    label = { Text("Address (CIDR, e.g. 10.0.0.2/32)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = { Text("DNS") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it },
                        label = { Text("MTU") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f)
                    )
                }

                OutlinedTextField(
                    value = peerPublicKey,
                    onValueChange = { peerPublicKey = it },
                    label = { Text("Peer PublicKey (Base64)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = presharedKey,
                    onValueChange = { presharedKey = it },
                    label = { Text("PresharedKey (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endpointHost,
                        onValueChange = { endpointHost = it },
                        label = { Text("Endpoint Host / IP") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                    OutlinedTextField(
                        value = endpointPort,
                        onValueChange = { endpointPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = probePort,
                        onValueChange = { probePort = it },
                        label = { Text("TCP Probe Port (443)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.5f)
                    )
                    OutlinedTextField(
                        value = keepalive,
                        onValueChange = { keepalive = it },
                        label = { Text("Keepalive (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.5f)
                    )
                }
                OutlinedTextField(
                    value = allowedIps,
                    onValueChange = { allowedIps = it },
                    label = { Text("AllowedIPs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portNum = endpointPort.toIntOrNull()
                    when {
                        name.isBlank() -> validationError = "Enter a tunnel name."
                        !WgKeyValidator.isValidWireGuardKey(privateKey) ->
                            validationError = "Invalid Interface PrivateKey (32-byte Base64)."
                        !WgKeyValidator.isValidWireGuardKey(peerPublicKey) ->
                            validationError = "Invalid Peer PublicKey (32-byte Base64)."
                        presharedKey.isNotBlank() && !WgKeyValidator.isValidWireGuardKey(presharedKey) ->
                            validationError = "Invalid PresharedKey (32-byte Base64)."
                        endpointHost.isBlank() || portNum == null || portNum !in 1..65535 ->
                            validationError = "Valid Endpoint host and port (1..65535) required."
                        addresses.split(",").any { !WgConfigParser.isValidCidrOrIp(it) } ->
                            validationError = "Invalid Interface Address CIDR."
                        allowedIps.split(",").any { !WgConfigParser.isValidCidrOrIp(it) } ->
                            validationError = "Invalid AllowedIPs CIDR."
                        else -> {
                            onSave(
                                TunnelProfile(
                                    id = existing?.id ?: 0L,
                                    name = name.trim(),
                                    serverName = serverName.trim().ifEmpty { name.trim() },
                                    countryCode = countryCode.trim().uppercase().ifEmpty { "UN" },
                                    countryName = countryName.trim().ifEmpty { "Custom" },
                                    interfaceAddresses = addresses.trim(),
                                    dnsServers = dns.trim(),
                                    mtu = mtu.toIntOrNull()?.coerceIn(1280, 1500) ?: 1420,
                                    privateKey = privateKey.trim(),
                                    derivedPublicKey = derivedPublicKey.ifBlank { WgKeyGenerator.derivePublicKey(privateKey) },
                                    peerPublicKey = peerPublicKey.trim(),
                                    presharedKey = presharedKey.trim(),
                                    endpointHost = endpointHost.trim(),
                                    endpointPort = portNum,
                                    allowedIps = allowedIps.trim(),
                                    persistentKeepalive = keepalive.toIntOrNull()?.coerceIn(0, 65535) ?: 25,
                                    probePort = probePort.toIntOrNull()?.takeIf { it in 1..65535 },
                                    carrierId = existing?.carrierId,
                                    isAutoEligible = existing?.isAutoEligible ?: true,
                                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis()
                                )
                            )
                            onDismiss()
                        }
                    }
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

@Composable
fun SafeConfigPreviewDialog(
    profile: TunnelProfile,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val preview = remember(profile) {
        WgConfigParser.toSafePreviewString(profile, includePrivateKey = false)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(profile.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (profile.derivedPublicKey.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Client Public Key: ${profile.derivedPublicKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonEmerald
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PrivateKey & PresharedKey are encrypted in AndroidKeyStore and masked here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.derivedPublicKey.isNotBlank()) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(profile.derivedPublicKey)) }
                    ) {
                        Text(strings.copyPublicKey, color = NeonEmerald)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.cancel, color = TextSecondary)
                }
            }
        }
    )
}
