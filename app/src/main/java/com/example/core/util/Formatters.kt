package com.example.core.util

import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDuration(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatLatency(ms: Double?): String {
        if (ms == null || ms.isNaN() || ms < 0) return "--"
        return "${ms.roundToInt()} ms"
    }

    fun formatMbps(mbps: Double?): String {
        if (mbps == null || mbps.isNaN() || mbps < 0) return "--"
        return String.format(Locale.US, "%.2f Mbps", mbps)
    }

    fun formatPercent(value: Double?): String {
        if (value == null || value.isNaN() || value < 0) return "--"
        return String.format(Locale.US, "%.1f%%", value)
    }
}
