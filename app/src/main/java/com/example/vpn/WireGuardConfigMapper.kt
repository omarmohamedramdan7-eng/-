package com.example.vpn

import com.example.core.datastore.AppSettings
import com.example.core.datastore.DnsPreset
import com.example.core.datastore.SplitTunnelMode
import com.example.domain.model.TunnelProfile
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress

object WireGuardConfigMapper {

    fun toWireGuardConfig(
        profile: TunnelProfile,
        settings: AppSettings,
        selfPackageName: String,
        overrideDns: String? = null,
        overrideMtu: Int? = null
    ): Config {
        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(profile.privateKey.trim())

        profile.interfaceAddresses.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { cidr ->
                interfaceBuilder.addAddress(InetNetwork.parse(cidr))
            }

        val effectiveDnsString = when {
            !overrideDns.isNullOrBlank() -> overrideDns
            settings.dnsPreset == DnsPreset.TUNNEL_DEFAULT -> profile.dnsServers
            settings.dnsPreset == DnsPreset.CUSTOM -> settings.customDnsServers.ifBlank { profile.dnsServers }
            else -> settings.dnsPreset.servers.joinToString(", ")
        }

        effectiveDnsString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { dnsIp ->
                runCatching { InetAddress.getByName(dnsIp) }
                    .getOrNull()
                    ?.let { interfaceBuilder.addDnsServer(it) }
            }

        val mtu = (overrideMtu ?: profile.mtu).coerceIn(1280, 1500)
        interfaceBuilder.setMtu(mtu)

        profile.listenPort?.takeIf { it in 1..65535 }?.let {
            interfaceBuilder.setListenPort(it)
        }

        // Split Tunneling rules
        when (settings.splitTunnelMode) {
            SplitTunnelMode.ALL_APPS -> {
                // Exclude self so diagnostic probes or UI controls do not loop unexpectedly if needed,
                // or keep all apps routed through VPN.
            }
            SplitTunnelMode.EXCLUDE_SELECTED -> {
                settings.splitTunnelPackages.forEach { pkg ->
                    if (pkg.isNotBlank() && pkg != selfPackageName) {
                        interfaceBuilder.excludeApplication(pkg)
                    }
                }
            }
            SplitTunnelMode.INCLUDE_SELECTED -> {
                if (settings.splitTunnelPackages.isNotEmpty()) {
                    settings.splitTunnelPackages.forEach { pkg ->
                        if (pkg.isNotBlank()) {
                            interfaceBuilder.includeApplication(pkg)
                        }
                    }
                }
            }
        }

        val peerBuilder = Peer.Builder()
            .parsePublicKey(profile.peerPublicKey.trim())
            .setEndpoint(InetEndpoint.parse("${profile.endpointHost.trim()}:${profile.endpointPort}"))

        if (profile.presharedKey.isNotBlank()) {
            peerBuilder.parsePreSharedKey(profile.presharedKey.trim())
        }

        profile.allowedIps.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { cidr ->
                peerBuilder.addAllowedIp(InetNetwork.parse(cidr))
            }

        if (profile.persistentKeepalive > 0) {
            peerBuilder.setPersistentKeepalive(profile.persistentKeepalive)
        }

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }
}
