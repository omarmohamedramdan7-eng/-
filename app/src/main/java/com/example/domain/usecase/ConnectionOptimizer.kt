package com.example.domain.usecase

import com.example.core.datastore.AppSettings
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.BeforeAfterComparison
import com.example.domain.model.DnsDiagnosticResult
import com.example.domain.model.OptimizationReport
import com.example.domain.model.OptimizationStep
import com.example.domain.model.OptimizationStepStatus
import com.example.domain.model.TunnelProfile
import com.example.network.NetworkProber
import kotlin.math.roundToInt

/**
 * Executes the real 9-step HOME INTERNET BOOST / OPTIMIZE CONNECTION diagnostics pipeline.
 * Never fabricates speed improvements or pretends to modify router hardware.
 */
class ConnectionOptimizer(
    private val prober: NetworkProber,
    private val autoServerSelector: AutoServerSelector
) {

    suspend fun runOptimization(
        snapshot: ActiveNetworkSnapshot,
        configuredTunnels: List<TunnelProfile>,
        settings: AppSettings,
        onStepProgress: (List<OptimizationStep>) -> Unit
    ): OptimizationReport {
        val steps = mutableListOf(
            OptimizationStep("net", "Inspect active network interface", "فحص واجهة الشبكة النشطة", OptimizationStepStatus.RUNNING),
            OptimizationStep("ping", "Measure Ping, Jitter & Packet Loss", "قياس Ping و Jitter وفقدان الحزم", OptimizationStepStatus.PENDING),
            OptimizationStep("dns", "Run System DNS & DoH diagnostics", "تشخيص DNS والاتصال المشفر DoH", OptimizationStepStatus.PENDING),
            OptimizationStep("speed", "Measure real Download & Upload throughput", "قياس سرعة التحميل والرفع الحقيقية", OptimizationStepStatus.PENDING),
            OptimizationStep("servers", "Probe configured WireGuard servers", "فحص خوادم WireGuard المجهزة", OptimizationStepStatus.PENDING),
            OptimizationStep("compare", "Compare baseline vs current metrics", "مقارنة النتائج قبل وبعد VPN", OptimizationStepStatus.PENDING)
        )
        onStepProgress(steps.toList())

        // Step 1: Network snapshot
        steps[0] = steps[0].copy(
            status = if (snapshot.isConnected) OptimizationStepStatus.PASSED else OptimizationStepStatus.FAILED,
            detail = if (snapshot.isConnected) {
                "${snapshot.transport.name} • Validated=${snapshot.hasValidatedInternet}"
            } else {
                "No active internet transport"
            }
        )
        steps[1] = steps[1].copy(status = OptimizationStepStatus.RUNNING)
        onStepProgress(steps.toList())

        // Step 2: Ping / Jitter / Packet Loss
        val pingResult = if (snapshot.isConnected) {
            prober.probeHostLatency("1.1.1.1", 443, samples = 5, timeoutMs = 2000)
        } else null

        steps[1] = steps[1].copy(
            status = when {
                pingResult == null || !pingResult.reachable -> OptimizationStepStatus.FAILED
                pingResult.packetLossPct > 5.0 -> OptimizationStepStatus.WARNING
                else -> OptimizationStepStatus.PASSED
            },
            detail = pingResult?.let {
                if (it.reachable) {
                    "${it.avgLatencyMs?.roundToInt()} ms • Jitter ${it.jitterMs?.roundToInt()} ms • Loss ${it.packetLossPct.roundToInt()}%"
                } else {
                    "Probe unreachable"
                }
            } ?: "Offline"
        )
        steps[2] = steps[2].copy(status = OptimizationStepStatus.RUNNING)
        onStepProgress(steps.toList())

        // Step 3: DNS & DoH diagnostics
        val dnsResults = if (snapshot.isConnected) {
            prober.runDnsDiagnostics("cloudflare.com", includeDoh = settings.enableDohDiagnostics)
        } else emptyList()
        val anyDnsOk = dnsResults.any { it.success }
        steps[2] = steps[2].copy(
            status = if (anyDnsOk) OptimizationStepStatus.PASSED else OptimizationStepStatus.WARNING,
            detail = "${dnsResults.count { it.success }}/${dnsResults.size} resolvers succeeded"
        )
        steps[3] = steps[3].copy(status = OptimizationStepStatus.RUNNING)
        onStepProgress(steps.toList())

        // Step 4: Real Speed Test
        val speedResult = if (snapshot.isConnected) {
            prober.runRealSpeedTest()
        } else null
        steps[3] = steps[3].copy(
            status = when {
                speedResult == null -> OptimizationStepStatus.FAILED
                speedResult.success -> OptimizationStepStatus.PASSED
                else -> OptimizationStepStatus.WARNING
            },
            detail = speedResult?.let {
                if (it.success) {
                    "↓ ${String.format("%.1f", it.downloadMbps ?: 0.0)} Mbps • ↑ ${String.format("%.1f", it.uploadMbps ?: 0.0)} Mbps"
                } else {
                    it.error ?: "Measurement failed"
                }
            } ?: "Offline"
        )
        steps[4] = steps[4].copy(status = OptimizationStepStatus.RUNNING)
        onStepProgress(steps.toList())

        // Step 5: Probe configured WireGuard servers
        val serverEval = if (snapshot.isConnected) {
            autoServerSelector.evaluateAndSelect(configuredTunnels)
        } else {
            AutoServerEvaluation(0, 0, emptyList(), null, "OFFLINE")
        }
        steps[4] = steps[4].copy(
            status = when {
                serverEval.candidatesTested == 0 -> OptimizationStepStatus.WARNING
                serverEval.bestCandidate != null -> OptimizationStepStatus.PASSED
                else -> OptimizationStepStatus.FAILED
            },
            detail = when {
                serverEval.candidatesTested == 0 -> "No configured WireGuard servers"
                serverEval.bestCandidate != null -> "Best: ${serverEval.bestCandidate.tunnel.serverName} (${serverEval.bestCandidate.latencyMs?.roundToInt()} ms)"
                else -> "0/${serverEval.candidatesTested} reachable"
            }
        )
        steps[5] = steps[5].copy(status = OptimizationStepStatus.RUNNING)
        onStepProgress(steps.toList())

        // Step 6: Comparison & Quality Score
        val comparison = if (settings.baselineCapturedAtMs > 0L) {
            BeforeAfterComparison(
                beforePingMs = settings.baselinePingMs,
                afterPingMs = pingResult?.avgLatencyMs,
                beforeJitterMs = settings.baselineJitterMs,
                afterJitterMs = pingResult?.jitterMs,
                beforeDownloadMbps = settings.baselineDownloadMbps,
                afterDownloadMbps = speedResult?.downloadMbps,
                beforeUploadMbps = settings.baselineUploadMbps,
                afterUploadMbps = speedResult?.uploadMbps,
                beforeLossPct = settings.baselinePacketLossPct,
                afterLossPct = pingResult?.packetLossPct
            )
        } else null

        val qualityScore = calculateQualityScore(
            connected = snapshot.isConnected,
            pingMs = pingResult?.avgLatencyMs,
            jitterMs = pingResult?.jitterMs,
            lossPct = pingResult?.packetLossPct,
            anyDnsOk = anyDnsOk,
            downloadMbps = speedResult?.downloadMbps
        )

        val (recsEn, recsAr) = buildHonestRecommendations(
            snapshot = snapshot,
            pingMs = pingResult?.avgLatencyMs,
            jitterMs = pingResult?.jitterMs,
            lossPct = pingResult?.packetLossPct,
            dnsResults = dnsResults,
            serverEval = serverEval
        )

        steps[5] = steps[5].copy(
            status = OptimizationStepStatus.PASSED,
            detail = "Score $qualityScore/100"
        )
        onStepProgress(steps.toList())

        return OptimizationReport(
            timestampMs = System.currentTimeMillis(),
            networkSnapshot = snapshot,
            pingResult = pingResult,
            dnsResults = dnsResults,
            speedTestResult = speedResult,
            serverProbes = serverEval.rankedResults,
            recommendedServer = serverEval.bestCandidate,
            comparison = comparison,
            qualityScore = qualityScore,
            recommendationsEn = recsEn,
            recommendationsAr = recsAr
        )
    }

    private fun calculateQualityScore(
        connected: Boolean,
        pingMs: Double?,
        jitterMs: Double?,
        lossPct: Double?,
        anyDnsOk: Boolean,
        downloadMbps: Double?
    ): Int {
        if (!connected || pingMs == null) return 0
        var score = 100
        score -= when {
            pingMs > 250 -> 35
            pingMs > 150 -> 20
            pingMs > 80 -> 10
            else -> 0
        }
        if (jitterMs != null) {
            score -= when {
                jitterMs > 40 -> 20
                jitterMs > 15 -> 10
                else -> 0
            }
        }
        if (lossPct != null) {
            score -= (lossPct * 4.0).roundToInt().coerceAtMost(35)
        }
        if (!anyDnsOk) score -= 15
        if (downloadMbps != null && downloadMbps < 2.0) score -= 10
        return score.coerceIn(0, 100)
    }

    private fun buildHonestRecommendations(
        snapshot: ActiveNetworkSnapshot,
        pingMs: Double?,
        jitterMs: Double?,
        lossPct: Double?,
        dnsResults: List<DnsDiagnosticResult>,
        serverEval: AutoServerEvaluation
    ): Pair<List<String>, List<String>> {
        val en = mutableListOf<String>()
        val ar = mutableListOf<String>()

        if (!snapshot.isConnected) {
            en.add("No active network connection detected. Connect to Wi-Fi or mobile data and re-run diagnostics.")
            ar.add("لا يوجد اتصال نشط بالشبكة. اتصل بالواي فاي أو بيانات الهاتف وأعد الفحص.")
            return en to ar
        }

        if (lossPct != null && lossPct > 2.0) {
            en.add("Detected ${lossPct.roundToInt()}% packet loss. Consider moving closer to your Wi-Fi access point or testing Ethernet/5GHz.")
            ar.add("تم رصد فقدان حزم بنسبة ${lossPct.roundToInt()}%. يُنصح بالاقتراب من نقطة الواي فاي أو تجربة نطاق 5GHz.")
        }

        if (jitterMs != null && jitterMs > 20.0) {
            en.add("High jitter (${jitterMs.roundToInt()} ms) indicates unstable routing or local congestion.")
            ar.add("ارتفاع التذبذب (${jitterMs.roundToInt()} ms) يشير إلى عدم استقرار المسار أو ازدحام محلي.")
        }

        val sysDns = dnsResults.firstOrNull { !it.dohSupported }
        val bestDoh = dnsResults.filter { it.dohSupported && it.success }.minByOrNull { it.latencyMs ?: Double.MAX_VALUE }
        if (sysDns != null && bestDoh != null && (sysDns.latencyMs ?: 999.0) > (bestDoh.latencyMs ?: 0.0) + 15.0) {
            en.add("${bestDoh.resolverName} responded faster than your current System DNS. You can select it in DNS Settings.")
            ar.add("استجاب ${bestDoh.resolverName} بشكل أسرع من DNS النظام الحالي. يمكنك تفعيله في إعدادات DNS.")
        }

        if (serverEval.candidatesTested == 0) {
            en.add("No WireGuard servers are configured yet. Import a .conf file or add a server in Tunnels & Servers.")
            ar.add("لا توجد خوادم WireGuard مجهزة بعد. استورد ملف .conf أو أضف خادمًا في قسم الأنفاق والسيرفرات.")
        } else if (serverEval.bestCandidate != null) {
            val best = serverEval.bestCandidate
            en.add("Best reachable server: ${best.tunnel.serverName} (${best.latencyMs?.roundToInt()} ms). Tap Connect below to route encrypted traffic through it.")
            ar.add("أفضل خادم متاح: ${best.tunnel.serverName} (${best.latencyMs?.roundToInt()} ms). اضغط اتصال أدناه لتوجيه اتصالك المشفر عبره.")
        } else {
            en.add("None of your configured WireGuard servers responded to reachability probes. Check server firewall or endpoint.")
            ar.add("لم يستجب أي من خوادم WireGuard المجهزة لفحص الوصول. تحقق من جدار الحماية أو عنوان السيرفر.")
        }

        if (pingMs != null && pingMs <= 60.0 && (lossPct ?: 0.0) == 0.0) {
            en.add("Baseline latency is healthy (${pingMs.roundToInt()} ms) with zero packet loss.")
            ar.add("زمن الاستجابة الأساسي جيد (${pingMs.roundToInt()} ms) بدون فقدان حزم.")
        }

        return en to ar
    }
}
