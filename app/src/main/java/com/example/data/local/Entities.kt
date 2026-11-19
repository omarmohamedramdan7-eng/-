package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tunnels")
data class TunnelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val serverName: String,
    val countryCode: String,
    val countryName: String,
    val protocol: String,
    val interfaceAddresses: String,
    val dnsServers: String,
    val mtu: Int,
    val listenPort: Int?,
    val privateKeyEncrypted: String,
    val derivedPublicKey: String,
    val peerPublicKey: String,
    val presharedKeyEncrypted: String,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: String,
    val persistentKeepalive: Int,
    val probePort: Int?,
    val carrierId: String?,
    val isAutoEligible: Boolean,
    val lastLatencyMs: Double?,
    val lastJitterMs: Double?,
    val lastPacketLossPct: Double?,
    val lastReachable: Boolean?,
    val lastTestedAtMs: Long,
    val createdAtMs: Long
)

@Entity(tableName = "diagnostic_history")
data class DiagnosticHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val vpnActiveDuringTest: Boolean,
    val transportType: String,
    val pingAvgMs: Double?,
    val jitterMs: Double?,
    val packetLossPct: Double?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val qualityScore: Int,
    val recommendedServerName: String?
)

@Entity(tableName = "carrier_configs")
data class CarrierConfigEntity(
    @PrimaryKey val carrierId: String,
    val assignedTunnelId: Long?,
    val preferredDns: String,
    val customMtu: Int,
    val lastTestLatencyMs: Double?,
    val lastTestReachable: Boolean?,
    val lastTestSummary: String,
    val updatedAtMs: Long
)
