package com.example.data.repository

import com.example.core.crypto.KeyCipher
import com.example.data.local.TunnelDao
import com.example.data.local.TunnelEntity
import com.example.domain.model.TunnelProfile
import com.example.domain.wireguard.WgKeyGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TunnelRepository(
    private val tunnelDao: TunnelDao,
    private val keyCipher: KeyCipher
) {

    val tunnelsFlow: Flow<List<TunnelProfile>> = tunnelDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getAllTunnels(): List<TunnelProfile> {
        return tunnelDao.getAllOnce().map { it.toDomain() }
    }

    suspend fun getTunnelById(id: Long): TunnelProfile? {
        return tunnelDao.getById(id)?.toDomain()
    }

    suspend fun saveTunnel(profile: TunnelProfile): Long {
        val derivedPub = if (profile.derivedPublicKey.isNotBlank()) {
            profile.derivedPublicKey
        } else if (profile.privateKey.isNotBlank()) {
            WgKeyGenerator.derivePublicKey(profile.privateKey)
        } else {
            ""
        }
        val entity = profile.copy(derivedPublicKey = derivedPub).toEntity()
        return if (profile.id == 0L) {
            tunnelDao.insert(entity)
        } else {
            tunnelDao.update(entity)
            profile.id
        }
    }

    suspend fun deleteTunnel(id: Long) {
        tunnelDao.deleteById(id)
    }

    suspend fun updateProbeMetrics(
        id: Long,
        latencyMs: Double?,
        jitterMs: Double?,
        packetLossPct: Double?,
        reachable: Boolean
    ) {
        tunnelDao.updateProbeMetrics(
            id = id,
            latencyMs = latencyMs,
            jitterMs = jitterMs,
            packetLossPct = packetLossPct,
            reachable = reachable,
            testedAtMs = System.currentTimeMillis()
        )
    }

    private fun TunnelEntity.toDomain(): TunnelProfile {
        val decryptedPriv = runCatching { keyCipher.decrypt(privateKeyEncrypted) }.getOrDefault("")
        val decryptedPsk = runCatching { keyCipher.decrypt(presharedKeyEncrypted) }.getOrDefault("")
        return TunnelProfile(
            id = id,
            name = name,
            serverName = serverName,
            countryCode = countryCode,
            countryName = countryName,
            protocol = protocol,
            interfaceAddresses = interfaceAddresses,
            dnsServers = dnsServers,
            mtu = mtu,
            listenPort = listenPort,
            privateKey = decryptedPriv,
            derivedPublicKey = derivedPublicKey,
            peerPublicKey = peerPublicKey,
            presharedKey = decryptedPsk,
            endpointHost = endpointHost,
            endpointPort = endpointPort,
            allowedIps = allowedIps,
            persistentKeepalive = persistentKeepalive,
            probePort = probePort,
            carrierId = carrierId,
            isAutoEligible = isAutoEligible,
            lastLatencyMs = lastLatencyMs,
            lastJitterMs = lastJitterMs,
            lastPacketLossPct = lastPacketLossPct,
            lastReachable = lastReachable,
            lastTestedAtMs = lastTestedAtMs,
            createdAtMs = createdAtMs
        )
    }

    private fun TunnelProfile.toEntity(): TunnelEntity {
        val encryptedPriv = keyCipher.encrypt(privateKey.trim())
        val encryptedPsk = if (presharedKey.isNotBlank()) keyCipher.encrypt(presharedKey.trim()) else ""
        return TunnelEntity(
            id = id,
            name = name.trim(),
            serverName = serverName.trim().ifEmpty { name.trim() },
            countryCode = countryCode.trim().uppercase().ifEmpty { "UN" },
            countryName = countryName.trim().ifEmpty { "Custom" },
            protocol = protocol,
            interfaceAddresses = interfaceAddresses.trim(),
            dnsServers = dnsServers.trim(),
            mtu = mtu.coerceIn(1280, 1500),
            listenPort = listenPort,
            privateKeyEncrypted = encryptedPriv,
            derivedPublicKey = derivedPublicKey,
            peerPublicKey = peerPublicKey.trim(),
            presharedKeyEncrypted = encryptedPsk,
            endpointHost = endpointHost.trim(),
            endpointPort = endpointPort,
            allowedIps = allowedIps.trim(),
            persistentKeepalive = persistentKeepalive.coerceIn(0, 65535),
            probePort = probePort,
            carrierId = carrierId,
            isAutoEligible = isAutoEligible,
            lastLatencyMs = lastLatencyMs,
            lastJitterMs = lastJitterMs,
            lastPacketLossPct = lastPacketLossPct,
            lastReachable = lastReachable,
            lastTestedAtMs = lastTestedAtMs,
            createdAtMs = createdAtMs
        )
    }
}
