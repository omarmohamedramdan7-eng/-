package com.example.network

import com.example.core.util.SafeLogger
import com.example.domain.model.DnsDiagnosticResult
import com.example.domain.model.PingProbeResult
import com.example.domain.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs

interface NetworkProber {
    suspend fun probeHostLatency(host: String, port: Int, samples: Int = 4, timeoutMs: Int = 2000): PingProbeResult
    suspend fun runDnsDiagnostics(queryHost: String = "cloudflare.com", includeDoh: Boolean = true): List<DnsDiagnosticResult>
    suspend fun runRealSpeedTest(): SpeedTestResult
}

/**
 * Real network measurement engine using TCP sockets, DNS resolution,
 * HTTPS DNS-over-HTTPS queries, and real HTTP throughput streams.
 * Never fabricates or mocks metrics.
 */
class NetworkProbeEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) : NetworkProber {

    override suspend fun probeHostLatency(
        host: String,
        port: Int,
        samples: Int,
        timeoutMs: Int
    ): PingProbeResult = withContext(Dispatchers.IO) {
        val targetLabel = "$host:$port"
        val resolvedAddress = runCatching { InetAddress.getByName(host) }.getOrNull()
        if (resolvedAddress == null) {
            return@withContext PingProbeResult(
                target = targetLabel,
                reachable = false,
                avgLatencyMs = null,
                minLatencyMs = null,
                maxLatencyMs = null,
                jitterMs = null,
                packetLossPct = 100.0,
                samplesSent = samples,
                samplesReceived = 0
            )
        }

        val rtts = mutableListOf<Double>()
        repeat(samples) {
            val rtt = measureSingleTcpRtt(resolvedAddress, port, timeoutMs)
            if (rtt != null) {
                rtts.add(rtt)
            }
        }

        if (rtts.isEmpty()) {
            return@withContext PingProbeResult(
                target = targetLabel,
                reachable = false,
                avgLatencyMs = null,
                minLatencyMs = null,
                maxLatencyMs = null,
                jitterMs = null,
                packetLossPct = 100.0,
                samplesSent = samples,
                samplesReceived = 0
            )
        }

        val avg = rtts.average()
        val jitter = if (rtts.size >= 2) {
            rtts.zipWithNext { a, b -> abs(b - a) }.average()
        } else {
            0.0
        }
        val lossPct = ((samples - rtts.size).toDouble() / samples.toDouble()) * 100.0

        PingProbeResult(
            target = targetLabel,
            reachable = true,
            avgLatencyMs = avg,
            minLatencyMs = rtts.minOrNull(),
            maxLatencyMs = rtts.maxOrNull(),
            jitterMs = jitter,
            packetLossPct = lossPct,
            samplesSent = samples,
            samplesReceived = rtts.size
        )
    }

    /**
     * Measures TCP handshake RTT. If the remote host replies with TCP RST (ConnectException),
     * the host IP was still reached across the network and the RTT to receive the RST is valid.
     */
    private fun measureSingleTcpRtt(address: InetAddress, port: Int, timeoutMs: Int): Double? {
        val startNs = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMs)
            }
            (System.nanoTime() - startNs) / 1_000_000.0
        } catch (_: ConnectException) {
            // Host reachable across the path, port refused quickly -> RTT is still genuine
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
            elapsedMs.takeIf { it < timeoutMs }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun runDnsDiagnostics(
        queryHost: String,
        includeDoh: Boolean
    ): List<DnsDiagnosticResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DnsDiagnosticResult>()

        // 1. System DNS check
        val sysStart = System.nanoTime()
        val sysResult = try {
            val ips = InetAddress.getAllByName(queryHost).mapNotNull { it.hostAddress }.distinct()
            val latency = (System.nanoTime() - sysStart) / 1_000_000.0
            DnsDiagnosticResult(
                resolverName = "System DNS",
                queryHost = queryHost,
                resolvedIps = ips,
                latencyMs = latency,
                dohSupported = false,
                success = ips.isNotEmpty()
            )
        } catch (e: Exception) {
            DnsDiagnosticResult(
                resolverName = "System DNS",
                queryHost = queryHost,
                resolvedIps = emptyList(),
                latencyMs = null,
                dohSupported = false,
                success = false,
                error = e.message ?: "DNS resolution failed"
            )
        }
        results.add(sysResult)

        if (includeDoh) {
            // 2. Cloudflare DoH
            results.add(
                probeDohEndpoint(
                    resolverName = "Cloudflare DoH (1.1.1.1)",
                    url = "https://cloudflare-dns.com/dns-query?name=$queryHost&type=A",
                    queryHost = queryHost
                )
            )
            // 3. Google DoH
            results.add(
                probeDohEndpoint(
                    resolverName = "Google DoH (8.8.8.8)",
                    url = "https://dns.google/resolve?name=$queryHost&type=A",
                    queryHost = queryHost
                )
            )
        }

        results
    }

    private fun probeDohEndpoint(
        resolverName: String,
        url: String,
        queryHost: String
    ): DnsDiagnosticResult {
        val startNs = System.nanoTime()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val latency = (System.nanoTime() - startNs) / 1_000_000.0
                val ips = Regex(""""data"\s*:\s*"([0-9.]+)"""")
                    .findAll(body)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                DnsDiagnosticResult(
                    resolverName = resolverName,
                    queryHost = queryHost,
                    resolvedIps = ips,
                    latencyMs = latency,
                    dohSupported = true,
                    success = response.isSuccessful && ips.isNotEmpty(),
                    error = if (response.isSuccessful) null else "HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            DnsDiagnosticResult(
                resolverName = resolverName,
                queryHost = queryHost,
                resolvedIps = emptyList(),
                latencyMs = null,
                dohSupported = true,
                success = false,
                error = e.message ?: "DoH request failed"
            )
        }
    }

    override suspend fun runRealSpeedTest(): SpeedTestResult = withContext(Dispatchers.IO) {
        val downEndpoint = "https://speed.cloudflare.com/__down?bytes=4000000"
        val upEndpoint = "https://speed.cloudflare.com/__up"
        var downloadedBytes = 0L
        var downloadMbps: Double? = null
        var uploadedBytes = 0L
        var uploadMbps: Double? = null
        var errorMessage: String? = null

        // Real Download Measurement (up to 4 MB or 5 seconds max)
        try {
            val req = Request.Builder().url(downEndpoint).get().build()
            val startNs = System.nanoTime()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    errorMessage = "Download probe HTTP ${response.code}"
                } else {
                    val stream = response.body?.byteStream()
                    if (stream != null) {
                        val buffer = ByteArray(16 * 1024)
                        val maxDurationNs = 5_000_000_000L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            downloadedBytes += read
                            if (System.nanoTime() - startNs > maxDurationNs) break
                        }
                        val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                        if (downloadedBytes > 0) {
                            downloadMbps = (downloadedBytes * 8.0) / (elapsedSec * 1_000_000.0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SafeLogger.w("ProbeEngine", "Real download speed probe error: ${e.message}")
            errorMessage = e.message ?: "Download probe failed"
        }

        // Real Upload Measurement (1 MB stream)
        try {
            val totalUploadTarget = 1_000_000L
            val chunk = ByteArray(16 * 1024) { 0x4F }
            val startNs = System.nanoTime()
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = totalUploadTarget
                override fun writeTo(sink: BufferedSink) {
                    var sent = 0L
                    while (sent < totalUploadTarget) {
                        val toWrite = minOf(chunk.size.toLong(), totalUploadTarget - sent).toInt()
                        sink.write(chunk, 0, toWrite)
                        sent += toWrite
                        uploadedBytes = sent
                    }
                    sink.flush()
                }
            }
            val req = Request.Builder().url(upEndpoint).post(body).build()
            httpClient.newCall(req).execute().use { response ->
                val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                if (response.isSuccessful && uploadedBytes > 0) {
                    uploadMbps = (uploadedBytes * 8.0) / (elapsedSec * 1_000_000.0)
                } else if (errorMessage == null) {
                    errorMessage = "Upload probe HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            SafeLogger.w("ProbeEngine", "Real upload speed probe error: ${e.message}")
            if (errorMessage == null) {
                errorMessage = e.message ?: "Upload probe failed"
            }
        }

        SpeedTestResult(
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            bytesDownloaded = downloadedBytes,
            bytesUploaded = uploadedBytes,
            endpointUsed = "speed.cloudflare.com",
            success = downloadMbps != null || uploadMbps != null,
            error = if (downloadMbps == null && uploadMbps == null) errorMessage else null
        )
    }
}
