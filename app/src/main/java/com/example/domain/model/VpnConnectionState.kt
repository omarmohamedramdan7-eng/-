package com.example.domain.model

sealed class VpnConnectionState {
    data object Disconnected : VpnConnectionState()

    data class Connecting(
        val tunnelId: Long,
        val tunnelName: String,
        val serverName: String,
        val stage: String = "INITIALIZING_TUNNEL"
    ) : VpnConnectionState()

    data class Connected(
        val tunnelId: Long,
        val tunnelName: String,
        val serverName: String,
        val countryCode: String,
        val countryName: String,
        val endpoint: String,
        val connectedAtElapsedMs: Long,
        val rxBytes: Long = 0L,
        val txBytes: Long = 0L,
        val livePingMs: Double? = null,
        val latestHandshakeEpochMs: Long = 0L,
        val vpnInterfaceVerified: Boolean = false
    ) : VpnConnectionState() {
        val handshakeVerified: Boolean
            get() = latestHandshakeEpochMs > 0L
    }

    data class Reconnecting(
        val tunnelId: Long,
        val tunnelName: String,
        val attempt: Int,
        val reason: String
    ) : VpnConnectionState()

    data class Error(
        val errorCode: VpnErrorCode,
        val detail: String,
        val tunnelId: Long? = null
    ) : VpnConnectionState()
}

enum class VpnErrorCode {
    PERMISSION_DENIED,
    NO_CONFIGURED_TUNNEL,
    INVALID_CONFIG,
    NO_REACHABLE_SERVER,
    BACKEND_START_FAILED,
    HANDSHAKE_TIMEOUT,
    NETWORK_LOST
}
