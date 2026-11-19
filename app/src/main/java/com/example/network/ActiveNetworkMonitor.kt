package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import com.example.core.util.SafeLogger
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.NetworkTransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real Android network capabilities, active transport, carrier metadata
 * (without requiring privileged permissions), DNS servers, and OS-level TRANSPORT_VPN verification.
 */
class ActiveNetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _snapshot = MutableStateFlow(captureCurrentSnapshot())
    val snapshot: StateFlow<ActiveNetworkSnapshot> = _snapshot.asStateFlow()

    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            refresh()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            refresh()
        }
    }

    fun startMonitoring() {
        if (callbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            callbackRegistered = true
            refresh()
        } catch (e: Exception) {
            SafeLogger.w("NetMonitor", "Unable to register NetworkCallback", e)
        }
    }

    fun stopMonitoring() {
        if (!callbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        } finally {
            callbackRegistered = false
        }
    }

    fun refresh(): ActiveNetworkSnapshot {
        val current = captureCurrentSnapshot()
        _snapshot.value = current
        return current
    }

    fun isOsVpnTransportActive(): Boolean {
        return try {
            val allNetworks = connectivityManager.allNetworks
            allNetworks.any { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun captureCurrentSnapshot(): ActiveNetworkSnapshot {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val linkProps = activeNetwork?.let { connectivityManager.getLinkProperties(it) }
            val vpnActive = isOsVpnTransportActive()

            val transport = when {
                caps == null -> NetworkTransportType.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
                else -> NetworkTransportType.OTHER
            }

            val carrierName = runCatching {
                telephonyManager?.networkOperatorName.orEmpty()
                    .ifBlank { telephonyManager?.simOperatorName.orEmpty() }
            }.getOrDefault("")

            val mccMnc = runCatching {
                telephonyManager?.networkOperator.orEmpty()
                    .ifBlank { telephonyManager?.simOperator.orEmpty() }
            }.getOrDefault("")

            val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()

            ActiveNetworkSnapshot(
                isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                hasValidatedInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                isVpnActive = vpnActive,
                transport = transport,
                carrierDisplayName = carrierName,
                mccMnc = mccMnc,
                dnsServers = dnsList,
                linkDownstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0,
                linkUpstreamKbps = caps?.linkUpstreamBandwidthKbps ?: 0
            )
        } catch (e: Exception) {
            SafeLogger.w("NetMonitor", "Snapshot capture failed", e)
            ActiveNetworkSnapshot()
        }
    }
}
