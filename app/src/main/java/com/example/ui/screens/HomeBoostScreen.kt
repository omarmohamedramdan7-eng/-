package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.util.Formatters
import com.example.data.local.DiagnosticHistoryEntity
import com.example.domain.model.OptimizationStepStatus
import com.example.domain.model.TunnelProfile
import com.example.ui.components.OmarCard
import com.example.ui.components.SkeletonCardList
import com.example.ui.components.StatusBadge
import com.example.ui.components.TelemetryStatTile
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.BoostUiState

@Composable
fun HomeBoostScreen(
    boostState: BoostUiState,
    history: List<DiagnosticHistoryEntity>,
    onOptimizeClick: () -> Unit,
    onConnectRecommended: (TunnelProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 650.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.homeBoostTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.homeBoostSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // OPTIMIZE CONNECTION Primary CTA
            item {
                Button(
                    onClick = onOptimizeClick,
                    enabled = !boostState.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = ObsidianBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("optimize_connection_button")
                ) {
                    if (boostState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = ObsidianBlack
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.runningDiagnostics, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.optimizeConnectionBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Live 6-step Progress Checklist
            if (boostState.steps.isNotEmpty()) {
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            boostState.steps.forEach { step ->
                                val (icon, color) = when (step.status) {
                                    OptimizationStepStatus.PASSED -> Icons.Outlined.CheckCircle to NeonEmerald
                                    OptimizationStepStatus.WARNING -> Icons.Outlined.WarningAmber to WarningAmber
                                    OptimizationStepStatus.FAILED -> Icons.Outlined.ErrorOutline to ErrorCoral
                                    OptimizationStepStatus.RUNNING -> Icons.Outlined.HourglassTop to CyberCyan
                                    OptimizationStepStatus.PENDING -> Icons.Outlined.HourglassTop to TextMuted
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (strings.isArabic) step.titleAr else step.titleEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (step.detail.isNotBlank()) {
                                            Text(
                                                text = step.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (boostState.isRunning && boostState.report == null) {
                item { SkeletonCardList(count = 2) }
            }

            // Real Measured Report
            boostState.report?.let { report ->
                // Quality Score + Real Speed & Latency Metrics
                item {
                    OmarCard(borderColor = NeonEmerald.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.qualityScoreLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(
                                text = "${report.qualityScore} / 100",
                                color = when {
                                    report.qualityScore >= 75 -> NeonEmerald
                                    report.qualityScore >= 45 -> WarningAmber
                                    else -> ErrorCoral
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                label = strings.downloadLabel,
                                value = Formatters.formatMbps(report.speedTestResult?.downloadMbps),
                                icon = Icons.Rounded.ArrowDownward,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                label = strings.uploadLabel,
                                value = Formatters.formatMbps(report.speedTestResult?.uploadMbps),
                                icon = Icons.Rounded.ArrowUpward,
                                accent = CyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatTile(
                                label = strings.pingLabel,
                                value = Formatters.formatLatency(report.pingResult?.avgLatencyMs),
                                icon = Icons.Rounded.NetworkPing,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatTile(
                                label = "${strings.jitterLabel} / ${strings.packetLossLabel}",
                                value = "${Formatters.formatLatency(report.pingResult?.jitterMs)} • ${Formatters.formatPercent(report.pingResult?.packetLossPct)}",
                                icon = Icons.Rounded.SignalCellularAlt,
                                accent = WarningAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Recommended Best Available WireGuard Server
                report.recommendedServer?.let { best ->
                    item {
                        OmarCard(borderColor = NeonEmerald) {
                            Text(
                                text = if (strings.isArabic) "أفضل سيرفر متاح وفق القياس" else "Best Reachable WireGuard Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${best.tunnel.countryCode} • ${best.tunnel.serverName} (${Formatters.formatLatency(best.latencyMs)})",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onConnectRecommended(best.tunnel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonEmerald,
                                    contentColor = ObsidianBlack
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.connect, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Before VPN vs Current Comparison
                report.comparison?.let { cmp ->
                    item {
                        OmarCard(borderColor = ObsidianBorder) {
                            Text(
                                text = strings.beforeAfterTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ComparisonRow(
                                label = strings.pingLabel,
                                before = Formatters.formatLatency(cmp.beforePingMs),
                                current = Formatters.formatLatency(cmp.afterPingMs)
                            )
                            ComparisonRow(
                                label = strings.downloadLabel,
                                before = Formatters.formatMbps(cmp.beforeDownloadMbps),
                                current = Formatters.formatMbps(cmp.afterDownloadMbps)
                            )
                            ComparisonRow(
                                label = strings.uploadLabel,
                                before = Formatters.formatMbps(cmp.beforeUploadMbps),
                                current = Formatters.formatMbps(cmp.afterUploadMbps)
                            )
                        }
                    }
                }

                // DNS & DoH Diagnostics
                if (report.dnsResults.isNotEmpty()) {
                    item {
                        OmarCard(borderColor = ObsidianBorder) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Dns, contentDescription = null, tint = CyberCyan)
                                Text(
                                    text = strings.dnsDiagnosticsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            report.dnsResults.forEach { dns ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dns.resolverName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (dns.success) {
                                            Formatters.formatLatency(dns.latencyMs)
                                        } else {
                                            dns.error ?: "Failed"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (dns.success) NeonEmerald else ErrorCoral
                                    )
                                }
                            }
                        }
                    }
                }

                // Honest Measured Recommendations
                item {
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = WarningAmber)
                            Text(
                                text = strings.recommendationsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val recs = if (strings.isArabic) report.recommendationsAr else report.recommendationsEn
                        recs.forEach { rec ->
                            Text(
                                text = "• $rec",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Recent Diagnostic Runs History
            if (history.isNotEmpty() && boostState.report == null) {
                item {
                    Text(
                        text = if (strings.isArabic) "سجل الفحوصات السابقة" else "Recent Measurements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(history.take(5), key = { it.id }) { item ->
                    OmarCard(borderColor = ObsidianBorder) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.transportType} • ${if (item.vpnActiveDuringTest) "VPN ON" else "Direct"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                            Text(
                                text = "Score ${item.qualityScore}/100",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ping ${Formatters.formatLatency(item.pingAvgMs)} • ↓ ${Formatters.formatMbps(item.downloadMbps)} • ↑ ${Formatters.formatMbps(item.uploadMbps)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    before: String,
    current: String
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = "${strings.beforeVpn}: $before  →  ${strings.currentMeasure}: $current",
            style = MaterialTheme.typography.labelMedium,
            color = NeonEmerald
        )
    }
}
