package com.example.data.repository

import com.example.data.local.CarrierConfigDao
import com.example.data.local.CarrierConfigEntity
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.CarrierDiagnosticState
import com.example.domain.model.EgyptCarrierId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CarrierRepository(
    private val carrierConfigDao: CarrierConfigDao
) {

    fun observeCarrierStates(networkSnapshotFlow: Flow<ActiveNetworkSnapshot>): Flow<List<CarrierDiagnosticState>> {
        return carrierConfigDao.observeAll().map { savedList ->
            val savedMap = savedList.associateBy { it.carrierId }
            EgyptCarrierId.entries.map { carrier ->
                val saved = savedMap[carrier.id]
                CarrierDiagnosticState(
                    carrier = carrier,
                    isDetectedOnSim = false,
                    assignedTunnelId = saved?.assignedTunnelId,
                    preferredDns = saved?.preferredDns ?: carrier.recommendedDns,
                    customMtu = saved?.customMtu ?: 1380,
                    lastTestLatencyMs = saved?.lastTestLatencyMs,
                    lastTestReachable = saved?.lastTestReachable,
                    lastTestSummary = saved?.lastTestSummary.orEmpty()
                )
            }
        }
    }

    suspend fun saveCarrierSettings(
        carrier: EgyptCarrierId,
        assignedTunnelId: Long?,
        preferredDns: String,
        customMtu: Int
    ) {
        val existing = carrierConfigDao.getByCarrierId(carrier.id)
        carrierConfigDao.upsert(
            CarrierConfigEntity(
                carrierId = carrier.id,
                assignedTunnelId = assignedTunnelId,
                preferredDns = preferredDns.trim().ifEmpty { carrier.recommendedDns },
                customMtu = customMtu.coerceIn(1280, 1500),
                lastTestLatencyMs = existing?.lastTestLatencyMs,
                lastTestReachable = existing?.lastTestReachable,
                lastTestSummary = existing?.lastTestSummary.orEmpty(),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveCarrierProbeResult(
        carrier: EgyptCarrierId,
        latencyMs: Double?,
        reachable: Boolean,
        summary: String
    ) {
        val existing = carrierConfigDao.getByCarrierId(carrier.id)
        carrierConfigDao.upsert(
            CarrierConfigEntity(
                carrierId = carrier.id,
                assignedTunnelId = existing?.assignedTunnelId,
                preferredDns = existing?.preferredDns ?: carrier.recommendedDns,
                customMtu = existing?.customMtu ?: 1380,
                lastTestLatencyMs = latencyMs,
                lastTestReachable = reachable,
                lastTestSummary = summary,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    companion object {
        fun matchesCarrier(carrier: EgyptCarrierId, snapshot: ActiveNetworkSnapshot): Boolean {
            if (snapshot.mccMnc.isNotBlank() && carrier.mccMncPrefixes.any { snapshot.mccMnc.startsWith(it) }) {
                return true
            }
            val name = snapshot.carrierDisplayName.lowercase()
            if (name.isBlank()) return false
            return carrier.matchKeywords.any { name.contains(it) }
        }
    }
}
