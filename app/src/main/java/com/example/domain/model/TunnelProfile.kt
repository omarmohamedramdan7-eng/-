package com.example.domain.model

enum class ServerAvailability {
    CONFIGURED_UNTESTED,
    REACHABLE,
    UNREACHABLE,
    UNCONFIGURED
}

data class TunnelProfile(
    val id: Long = 0L,
    val name: String,
    val serverName: String,
    val countryCode: String,
    val countryName: String,
    val protocol: String = "WireGuard",
    val interfaceAddresses: String,
    val dnsServers: String,
    val mtu: Int = 1420,
    val listenPort: Int? = null,
    val privateKey: String,
    val derivedPublicKey: String = "",
    val peerPublicKey: String,
    val presharedKey: String = "",
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val persistentKeepalive: Int = 25,
    val probePort: Int? = null,
    val carrierId: String? = null,
    val isAutoEligible: Boolean = true,
    val lastLatencyMs: Double? = null,
    val lastJitterMs: Double? = null,
    val lastPacketLossPct: Double? = null,
    val lastReachable: Boolean? = null,
    val lastTestedAtMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    /**
     * A server or tunnel is only considered configured and connectable when
     * real WireGuard keys, interface address, and peer endpoint are present.
     */
    val isConfigured: Boolean
        get() = privateKey.isNotBlank() &&
            peerPublicKey.isNotBlank() &&
            endpointHost.isNotBlank() &&
            endpointPort in 1..65535 &&
            interfaceAddresses.isNotBlank() &&
            allowedIps.isNotBlank()

    val availability: ServerAvailability
        get() = when {
            !isConfigured -> ServerAvailability.UNCONFIGURED
            lastReachable == true -> ServerAvailability.REACHABLE
            lastReachable == false -> ServerAvailability.UNREACHABLE
            else -> ServerAvailability.CONFIGURED_UNTESTED
        }

    val endpointDisplay: String
        get() = if (endpointHost.isNotBlank() && endpointPort > 0) {
            "$endpointHost:$endpointPort"
        } else {
            "--"
        }
}
