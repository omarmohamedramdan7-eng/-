package com.example.domain.wireguard

import com.example.domain.model.TunnelProfile
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.util.Base64

data class ParsedWireGuardConfig(
    val privateKey: String,
    val derivedPublicKey: String,
    val interfaceAddresses: String,
    val dnsServers: String,
    val mtu: Int,
    val listenPort: Int?,
    val peerPublicKey: String,
    val presharedKey: String,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: String,
    val persistentKeepalive: Int
)

sealed class ConfigParseResult {
    data class Success(val config: ParsedWireGuardConfig) : ConfigParseResult()
    data class Error(val reason: String) : ConfigParseResult()
}

object WgKeyValidator {
    fun isValidWireGuardKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.length != 44 || !trimmed.endsWith("=")) return false
        return try {
            val decoded = Base64.getDecoder().decode(trimmed)
            decoded.size == 32
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

object WgKeyGenerator {
    fun generateKeyPair(): Pair<String, String> {
        val keyPair = KeyPair()
        return keyPair.privateKey.toBase64() to keyPair.publicKey.toBase64()
    }

    fun derivePublicKey(privateKeyBase64: String): String {
        return try {
            val privKey = Key.fromBase64(privateKeyBase64.trim())
            KeyPair(privKey).publicKey.toBase64()
        } catch (_: Exception) {
            ""
        }
    }
}

/**
 * Pure Kotlin parser, validator, and exporter for standard WireGuard .conf files.
 * Never logs key material.
 */
object WgConfigParser {

    fun parse(rawText: String): ConfigParseResult {
        val normalized = rawText.trim()
        if (normalized.isEmpty()) {
            return ConfigParseResult.Error("Configuration text is empty.")
        }

        // Support QR URI payloads like wireguard://... or raw INI text
        val iniText = if (normalized.startsWith("wireguard://", ignoreCase = true)) {
            return ConfigParseResult.Error("Unsupported URI format; provide standard WireGuard [Interface]/[Peer] config.")
        } else {
            normalized
        }

        var currentSection = ""
        val interfaceMap = mutableMapOf<String, MutableList<String>>()
        val peerMap = mutableMapOf<String, MutableList<String>>()
        var peerSectionCount = 0

        iniText.lineSequence().forEach { rawLine ->
            val withoutComment = stripComment(rawLine).trim()
            if (withoutComment.isEmpty()) return@forEach

            if (withoutComment.startsWith("[") && withoutComment.endsWith("]")) {
                val section = withoutComment.substring(1, withoutComment.length - 1).trim().lowercase()
                currentSection = section
                if (section == "peer") {
                    peerSectionCount++
                }
                return@forEach
            }

            val eqIdx = withoutComment.indexOf('=')
            if (eqIdx <= 0) {
                return@forEach
            }
            val key = withoutComment.substring(0, eqIdx).trim().lowercase()
            val value = withoutComment.substring(eqIdx + 1).trim()

            when (currentSection) {
                "interface" -> interfaceMap.getOrPut(key) { mutableListOf() }.add(value)
                "peer" -> if (peerSectionCount == 1) {
                    peerMap.getOrPut(key) { mutableListOf() }.add(value)
                }
            }
        }

        if (interfaceMap.isEmpty()) {
            return ConfigParseResult.Error("Missing [Interface] section.")
        }
        if (peerMap.isEmpty()) {
            return ConfigParseResult.Error("Missing [Peer] section.")
        }

        val privateKey = interfaceMap["privatekey"]?.firstOrNull()?.trim().orEmpty()
        if (!WgKeyValidator.isValidWireGuardKey(privateKey)) {
            return ConfigParseResult.Error("Invalid or missing [Interface] PrivateKey (must be 32-byte Base64).")
        }

        val addressesRaw = interfaceMap["address"]?.joinToString(", ") { it.trim() }.orEmpty()
        val addresses = addressesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (addresses.isEmpty() || addresses.any { !isValidCidrOrIp(it) }) {
            return ConfigParseResult.Error("Invalid or missing [Interface] Address (e.g., 10.0.0.2/32).")
        }

        val dnsRaw = interfaceMap["dns"]?.joinToString(", ") { it.trim() }.orEmpty()
        val mtu = interfaceMap["mtu"]?.firstOrNull()?.toIntOrNull()?.coerceIn(1280, 1500) ?: 1420
        val listenPort = interfaceMap["listenport"]?.firstOrNull()?.toIntOrNull()?.takeIf { it in 1..65535 }

        val peerPublicKey = peerMap["publickey"]?.firstOrNull()?.trim().orEmpty()
        if (!WgKeyValidator.isValidWireGuardKey(peerPublicKey)) {
            return ConfigParseResult.Error("Invalid or missing [Peer] PublicKey (must be 32-byte Base64).")
        }

        val presharedKey = peerMap["presharedkey"]?.firstOrNull()?.trim().orEmpty()
        if (presharedKey.isNotEmpty() && !WgKeyValidator.isValidWireGuardKey(presharedKey)) {
            return ConfigParseResult.Error("Invalid [Peer] PresharedKey (must be 32-byte Base64).")
        }

        val endpointRaw = peerMap["endpoint"]?.firstOrNull()?.trim().orEmpty()
        val parsedEndpoint = parseEndpoint(endpointRaw)
            ?: return ConfigParseResult.Error("Invalid or missing [Peer] Endpoint (expected host:port).")

        val allowedIpsRaw = peerMap["allowedips"]?.joinToString(", ") { it.trim() }.orEmpty()
        val allowedIps = allowedIpsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (allowedIps.isEmpty() || allowedIps.any { !isValidCidrOrIp(it) }) {
            return ConfigParseResult.Error("Invalid or missing [Peer] AllowedIPs (e.g., 0.0.0.0/0).")
        }

        val keepalive = peerMap["persistentkeepalive"]?.firstOrNull()?.toIntOrNull()?.coerceIn(0, 65535) ?: 25
        val derivedPub = WgKeyGenerator.derivePublicKey(privateKey)

        return ConfigParseResult.Success(
            ParsedWireGuardConfig(
                privateKey = privateKey,
                derivedPublicKey = derivedPub,
                interfaceAddresses = addresses.joinToString(", "),
                dnsServers = dnsRaw,
                mtu = mtu,
                listenPort = listenPort,
                peerPublicKey = peerPublicKey,
                presharedKey = presharedKey,
                endpointHost = parsedEndpoint.first,
                endpointPort = parsedEndpoint.second,
                allowedIps = allowedIps.joinToString(", "),
                persistentKeepalive = keepalive
            )
        )
    }

    fun toSafePreviewString(profile: TunnelProfile, includePrivateKey: Boolean = false): String {
        val sb = StringBuilder()
        sb.appendLine("[Interface]")
        sb.appendLine("PrivateKey = ${if (includePrivateKey) profile.privateKey else "[HIDDEN_IN_APP]"}")
        sb.appendLine("Address = ${profile.interfaceAddresses}")
        if (profile.dnsServers.isNotBlank()) {
            sb.appendLine("DNS = ${profile.dnsServers}")
        }
        sb.appendLine("MTU = ${profile.mtu}")
        profile.listenPort?.let { sb.appendLine("ListenPort = $it") }
        sb.appendLine()
        sb.appendLine("[Peer]")
        sb.appendLine("PublicKey = ${profile.peerPublicKey}")
        if (profile.presharedKey.isNotBlank()) {
            sb.appendLine("PresharedKey = ${if (includePrivateKey) profile.presharedKey else "[HIDDEN_IN_APP]"}")
        }
        sb.appendLine("Endpoint = ${profile.endpointHost}:${profile.endpointPort}")
        sb.appendLine("AllowedIPs = ${profile.allowedIps}")
        if (profile.persistentKeepalive > 0) {
            sb.appendLine("PersistentKeepalive = ${profile.persistentKeepalive}")
        }
        return sb.toString().trim()
    }

    fun parseEndpoint(endpoint: String): Pair<String, Int>? {
        val trimmed = endpoint.trim()
        if (trimmed.isEmpty()) return null
        // IPv6 bracket notation: [2001:db8::1]:51820
        if (trimmed.startsWith("[")) {
            val closeBracket = trimmed.indexOf(']')
            if (closeBracket <= 1) return null
            val host = trimmed.substring(1, closeBracket)
            val portPart = trimmed.substring(closeBracket + 1)
            if (!portPart.startsWith(":")) return null
            val port = portPart.drop(1).toIntOrNull() ?: return null
            return if (host.isNotBlank() && port in 1..65535) host to port else null
        }
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon <= 0) return null
        val host = trimmed.substring(0, lastColon).trim()
        val port = trimmed.substring(lastColon + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || host.contains(' ') || port !in 1..65535) return null
        return host to port
    }

    fun isValidCidrOrIp(cidr: String): Boolean {
        val parts = cidr.trim().split("/")
        if (parts.size > 2) return false
        val ip = parts[0].trim()
        val isIpv4 = IPV4_REGEX.matches(ip)
        val isIpv6 = ip.contains(":") && !ip.contains(" ")
        if (!isIpv4 && !isIpv6) return false
        if (parts.size == 2) {
            val prefix = parts[1].toIntOrNull() ?: return false
            if (isIpv4 && prefix !in 0..32) return false
            if (isIpv6 && prefix !in 0..128) return false
        }
        return true
    }

    private fun stripComment(line: String): String {
        val hashIdx = line.indexOf('#')
        val semiIdx = line.indexOf(';')
        val cut = listOf(hashIdx, semiIdx).filter { it >= 0 }.minOrNull() ?: return line
        return line.substring(0, cut)
    }

    private val IPV4_REGEX = Regex(
        """^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"""
    )
}
