package com.example.domain.model

enum class NetworkTransportType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER
}

data class ActiveNetworkSnapshot(
    val isConnected: Boolean = false,
    val hasValidatedInternet: Boolean = false,
    val isVpnActive: Boolean = false,
    val transport: NetworkTransportType = NetworkTransportType.NONE,
    val carrierDisplayName: String = "",
    val mccMnc: String = "",
    val dnsServers: List<String> = emptyList(),
    val linkDownstreamKbps: Int = 0,
    val linkUpstreamKbps: Int = 0
)

data class PingProbeResult(
    val target: String,
    val reachable: Boolean,
    val avgLatencyMs: Double?,
    val minLatencyMs: Double?,
    val maxLatencyMs: Double?,
    val jitterMs: Double?,
    val packetLossPct: Double,
    val samplesSent: Int,
    val samplesReceived: Int
)

data class DnsDiagnosticResult(
    val resolverName: String,
    val queryHost: String,
    val resolvedIps: List<String>,
    val latencyMs: Double?,
    val dohSupported: Boolean,
    val success: Boolean,
    val error: String? = null
)

data class SpeedTestResult(
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val bytesDownloaded: Long,
    val bytesUploaded: Long,
    val endpointUsed: String,
    val success: Boolean,
    val error: String? = null
)

data class ServerProbeResult(
    val tunnel: TunnelProfile,
    val reachable: Boolean,
    val latencyMs: Double?,
    val jitterMs: Double?,
    val packetLossPct: Double,
    val score: Double,
    val failureReason: String? = null
)

data class BeforeAfterComparison(
    val beforePingMs: Double?,
    val afterPingMs: Double?,
    val beforeJitterMs: Double?,
    val afterJitterMs: Double?,
    val beforeDownloadMbps: Double?,
    val afterDownloadMbps: Double?,
    val beforeUploadMbps: Double?,
    val afterUploadMbps: Double?,
    val beforeLossPct: Double?,
    val afterLossPct: Double?
)

enum class OptimizationStepStatus {
    PENDING,
    RUNNING,
    PASSED,
    WARNING,
    FAILED
}

data class OptimizationStep(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val status: OptimizationStepStatus,
    val detail: String = ""
)

data class OptimizationReport(
    val timestampMs: Long,
    val networkSnapshot: ActiveNetworkSnapshot,
    val pingResult: PingProbeResult?,
    val dnsResults: List<DnsDiagnosticResult>,
    val speedTestResult: SpeedTestResult?,
    val serverProbes: List<ServerProbeResult>,
    val recommendedServer: ServerProbeResult?,
    val comparison: BeforeAfterComparison?,
    val qualityScore: Int,
    val recommendationsEn: List<String>,
    val recommendationsAr: List<String>
)

enum class EgyptCarrierId(
    val id: String,
    val displayNameEn: String,
    val displayNameAr: String,
    val mccMncPrefixes: List<String>,
    val matchKeywords: List<String>,
    val recommendedDns: String
) {
    VODAFONE(
        id = "vodafone_eg",
        displayNameEn = "Vodafone Egypt",
        displayNameAr = "فودافون مصر",
        mccMncPrefixes = listOf("60202"),
        matchKeywords = listOf("vodafone", "vf-eg", "vf eg"),
        recommendedDns = "1.1.1.1, 1.0.0.1"
    ),
    ORANGE(
        id = "orange_eg",
        displayNameEn = "Orange Egypt",
        displayNameAr = "أورنج مصر",
        mccMncPrefixes = listOf("60201"),
        matchKeywords = listOf("orange", "mobinil"),
        recommendedDns = "9.9.9.9, 1.1.1.1"
    ),
    ETISALAT(
        id = "etisalat_eg",
        displayNameEn = "Etisalat Egypt (e&)",
        displayNameAr = "اتصالات مصر (e&)",
        mccMncPrefixes = listOf("60203"),
        matchKeywords = listOf("etisalat", "e&"),
        recommendedDns = "1.1.1.1, 8.8.8.8"
    ),
    WE(
        id = "we_eg",
        displayNameEn = "WE Egypt",
        displayNameAr = "وي مصر (WE)",
        mccMncPrefixes = listOf("60204"),
        matchKeywords = listOf("we", "telecom egypt", "te "),
        recommendedDns = "1.1.1.1, 9.9.9.9"
    )
}

data class CarrierDiagnosticState(
    val carrier: EgyptCarrierId,
    val isDetectedOnSim: Boolean = false,
    val assignedTunnelId: Long? = null,
    val preferredDns: String = carrier.recommendedDns,
    val customMtu: Int = 1380,
    val lastTestLatencyMs: Double? = null,
    val lastTestReachable: Boolean? = null,
    val lastTestSummary: String = "",
    val isTesting: Boolean = false
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)
