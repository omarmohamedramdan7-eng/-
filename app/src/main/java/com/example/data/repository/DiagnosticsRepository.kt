package com.example.data.repository

import com.example.data.local.DiagnosticDao
import com.example.data.local.DiagnosticHistoryEntity
import com.example.domain.model.OptimizationReport
import kotlinx.coroutines.flow.Flow

class DiagnosticsRepository(
    private val diagnosticDao: DiagnosticDao
) {
    val recentHistory: Flow<List<DiagnosticHistoryEntity>> = diagnosticDao.observeRecent()

    suspend fun recordReport(report: OptimizationReport) {
        diagnosticDao.insert(
            DiagnosticHistoryEntity(
                timestampMs = report.timestampMs,
                vpnActiveDuringTest = report.networkSnapshot.isVpnActive,
                transportType = report.networkSnapshot.transport.name,
                pingAvgMs = report.pingResult?.avgLatencyMs,
                jitterMs = report.pingResult?.jitterMs,
                packetLossPct = report.pingResult?.packetLossPct,
                downloadMbps = report.speedTestResult?.downloadMbps,
                uploadMbps = report.speedTestResult?.uploadMbps,
                qualityScore = report.qualityScore,
                recommendedServerName = report.recommendedServer?.tunnel?.serverName
            )
        )
    }

    suspend fun clearHistory() {
        diagnosticDao.clearAll()
    }
}
