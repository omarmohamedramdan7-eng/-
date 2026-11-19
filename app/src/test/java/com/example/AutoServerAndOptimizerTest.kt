package com.example

import com.example.core.datastore.AppSettings
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.DnsDiagnosticResult
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.PingProbeResult
import com.example.domain.model.SpeedTestResult
import com.example.domain.model.TunnelProfile
import com.example.domain.usecase.AutoServerSelector
import com.example.domain.usecase.ConnectionOptimizer
import com.example.domain.wireguard.WgKeyGenerator
import com.example.network.NetworkProber
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoServerAndOptimizerTest {

    private fun buildTunnel(
        id: Long,
        name: String,
        host: String,
        configured: Boolean = true
    ): TunnelProfile {
        val (priv, _) = WgKeyGenerator.generateKeyPair()
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()
        return TunnelProfile(
            id = id,
            name = name,
            serverName = name,
            countryCode = "DE",
            countryName = "Germany",
            interfaceAddresses = if (configured) "10.0.0.2/32" else "",
            dnsServers = "1.1.1.1",
            privateKey = if (configured) priv else "",
            peerPublicKey = if (configured) peerPub else "",
            endpointHost = host,
            endpointPort = 51820
        )
    }

    @Test
    fun `AutoServerSelector excludes unconfigured and unreachable servers and prefers stable low-loss server over noisy low-ping server`() = runTest {
        val unconfigured = buildTunnel(1, "Unconfigured", "0.0.0.0", configured = false)
        val unreachable = buildTunnel(2, "DeadServer", "dead.example.net", configured = true)
        val unstableLowPing = buildTunnel(3, "UnstableLowPing", "unstable.example.net", configured = true)
        val stableGoodServer = buildTunnel(4, "StableOptimal", "stable.example.net", configured = true)

        val fakeProber = object : NetworkProber {
            override suspend fun probeHostLatency(host: String, port: Int, samples: Int, timeoutMs: Int): PingProbeResult {
                return when (host) {
                    "dead.example.net" -> PingProbeResult(host, false, null, null, null, null, 100.0, samples, 0)
                    // Lower average ping (30ms) but terrible jitter (35ms) and 25% packet loss
                    "unstable.example.net" -> PingProbeResult(host, true, 30.0, 10.0, 80.0, 35.0, 25.0, samples, 3)
                    // Slightly higher ping (45ms) but 2ms jitter and 0% loss -> clearly better VPN experience
                    "stable.example.net" -> PingProbeResult(host, true, 45.0, 43.0, 47.0, 2.0, 0.0, samples, samples)
                    else -> PingProbeResult(host, false, null, null, null, null, 100.0, samples, 0)
                }
            }

            override suspend fun runDnsDiagnostics(queryHost: String, includeDoh: Boolean) = emptyList<DnsDiagnosticResult>()
            override suspend fun runRealSpeedTest() = SpeedTestResult(null, null, 0, 0, "", false)
        }

        val selector = AutoServerSelector(fakeProber)
        val eval = selector.evaluateAndSelect(listOf(unconfigured, unreachable, unstableLowPing, stableGoodServer))

        // Unconfigured was excluded before probing
        assertEquals(3, eval.candidatesTested)
        assertEquals(2, eval.reachableCount)
        assertNotNull(eval.bestCandidate)
        assertEquals("StableOptimal", eval.bestCandidate!!.tunnel.serverName)
    }

    @Test
    fun `AutoServerSelector returns NO_CONFIGURED_SERVERS when empty`() = runTest {
        val dummyProber = object : NetworkProber {
            override suspend fun probeHostLatency(host: String, port: Int, samples: Int, timeoutMs: Int) =
                PingProbeResult(host, false, null, null, null, null, 100.0, samples, 0)
            override suspend fun runDnsDiagnostics(queryHost: String, includeDoh: Boolean) = emptyList<DnsDiagnosticResult>()
            override suspend fun runRealSpeedTest() = SpeedTestResult(null, null, 0, 0, "", false)
        }
        val eval = AutoServerSelector(dummyProber).evaluateAndSelect(emptyList())
        assertNull(eval.bestCandidate)
        assertEquals("NO_CONFIGURED_SERVERS", eval.failureReason)
    }

    @Test
    fun `ConnectionOptimizer executes all 6 steps and compares baseline`() = runTest {
        val server = buildTunnel(10, "Cairo-WG", "vpn.egypt.example", configured = true)
        val prober = object : NetworkProber {
            override suspend fun probeHostLatency(host: String, port: Int, samples: Int, timeoutMs: Int) =
                PingProbeResult(host, true, 42.0, 40.0, 44.0, 2.0, 0.0, samples, samples)

            override suspend fun runDnsDiagnostics(queryHost: String, includeDoh: Boolean) = listOf(
                DnsDiagnosticResult("System DNS", queryHost, listOf("1.1.1.1"), 55.0, false, true),
                DnsDiagnosticResult("Cloudflare DoH", queryHost, listOf("1.1.1.1"), 22.0, true, true)
            )

            override suspend fun runRealSpeedTest() = SpeedTestResult(
                downloadMbps = 48.5,
                uploadMbps = 12.3,
                bytesDownloaded = 4_000_000L,
                bytesUploaded = 1_000_000L,
                endpointUsed = "speed.cloudflare.com",
                success = true
            )
        }

        val optimizer = ConnectionOptimizer(prober, AutoServerSelector(prober))
        val snapshot = ActiveNetworkSnapshot(
            isConnected = true,
            hasValidatedInternet = true,
            isVpnActive = false,
            transport = NetworkTransportType.WIFI
        )
        val settings = AppSettings(
            baselinePingMs = 60.0,
            baselineDownloadMbps = 30.0,
            baselineCapturedAtMs = 1000L
        )

        var lastStepsSize = 0
        val report = optimizer.runOptimization(snapshot, listOf(server), settings) { steps ->
            lastStepsSize = steps.size
        }

        assertEquals(6, lastStepsSize)
        assertTrue(report.qualityScore >= 80)
        assertNotNull(report.recommendedServer)
        assertEquals("Cairo-WG", report.recommendedServer!!.tunnel.serverName)
        val comparison = report.comparison
        assertNotNull(comparison)
        assertEquals(60.0, comparison!!.beforePingMs!!, 0.01)
        assertEquals(42.0, comparison.afterPingMs!!, 0.01)
    }
}
