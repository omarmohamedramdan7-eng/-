package com.example.domain.usecase

import com.example.domain.model.ServerProbeResult
import com.example.domain.model.TunnelProfile
import com.example.network.NetworkProber
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class AutoServerEvaluation(
    val candidatesTested: Int,
    val reachableCount: Int,
    val rankedResults: List<ServerProbeResult>,
    val bestCandidate: ServerProbeResult?,
    val failureReason: String? = null
)

/**
 * Real AUTO SERVER engine:
 * 1. Filters only genuinely configured WireGuard tunnels/servers.
 * 2. Probes each candidate's endpoint reachability, latency, jitter, and packet loss.
 * 3. Excludes unreachable or 100% loss servers.
 * 4. Ranks candidates using composite quality score (latency + jitter + packet loss),
 *    never relying on static ordering or ping alone.
 */
class AutoServerSelector(
    private val prober: NetworkProber
) {
    suspend fun evaluateAndSelect(tunnels: List<TunnelProfile>): AutoServerEvaluation = coroutineScope {
        val eligible = tunnels.filter { it.isConfigured && it.isAutoEligible }
        if (eligible.isEmpty()) {
            return@coroutineScope AutoServerEvaluation(
                candidatesTested = 0,
                reachableCount = 0,
                rankedResults = emptyList(),
                bestCandidate = null,
                failureReason = "NO_CONFIGURED_SERVERS"
            )
        }

        val probed = eligible.map { tunnel ->
            async {
                val probePort = tunnel.probePort ?: 443
                val ping = prober.probeHostLatency(
                    host = tunnel.endpointHost,
                    port = probePort,
                    samples = 3,
                    timeoutMs = 1800
                )
                if (!ping.reachable || ping.avgLatencyMs == null || ping.packetLossPct >= 100.0) {
                    ServerProbeResult(
                        tunnel = tunnel,
                        reachable = false,
                        latencyMs = null,
                        jitterMs = null,
                        packetLossPct = 100.0,
                        score = Double.MAX_VALUE,
                        failureReason = "Unreachable (${tunnel.endpointHost}:$probePort)"
                    )
                } else {
                    val latency = ping.avgLatencyMs
                    val jitter = ping.jitterMs ?: 0.0
                    val loss = ping.packetLossPct
                    // Composite penalty score: lower is better.
                    // Penalizes packet loss heavily and jitter moderately alongside latency.
                    val compositeScore = latency + (jitter * 2.5) + (loss * 12.0)
                    ServerProbeResult(
                        tunnel = tunnel,
                        reachable = true,
                        latencyMs = latency,
                        jitterMs = jitter,
                        packetLossPct = loss,
                        score = compositeScore
                    )
                }
            }
        }.awaitAll()

        val reachable = probed.filter { it.reachable }.sortedBy { it.score }
        val unreachable = probed.filterNot { it.reachable }
        val combined = reachable + unreachable

        AutoServerEvaluation(
            candidatesTested = eligible.size,
            reachableCount = reachable.size,
            rankedResults = combined,
            bestCandidate = reachable.firstOrNull(),
            failureReason = if (reachable.isEmpty()) "ALL_SERVERS_UNREACHABLE" else null
        )
    }
}
