package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.omarSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "omar_vpn_settings"
)

enum class AppLanguage {
    SYSTEM,
    ARABIC,
    ENGLISH
}

enum class DnsPreset(val label: String, val servers: List<String>, val dohUrl: String?) {
    TUNNEL_DEFAULT("Tunnel Config DNS", emptyList(), null),
    CLOUDFLARE("Cloudflare (1.1.1.1)", listOf("1.1.1.1", "1.0.0.1"), "https://cloudflare-dns.com/dns-query"),
    QUAD9("Quad9 Security (9.9.9.9)", listOf("9.9.9.9", "149.112.112.112"), "https://dns.quad9.net:5053/dns-query"),
    GOOGLE("Google Public DNS (8.8.8.8)", listOf("8.8.8.8", "8.8.4.4"), "https://dns.google/resolve"),
    CUSTOM("Custom DNS", emptyList(), null)
}

enum class SplitTunnelMode {
    ALL_APPS,
    EXCLUDE_SELECTED,
    INCLUDE_SELECTED
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val showCyberSilhouette: Boolean = true,
    val autoServerMode: Boolean = true,
    val selectedTunnelId: Long? = null,
    val dnsPreset: DnsPreset = DnsPreset.TUNNEL_DEFAULT,
    val customDnsServers: String = "",
    val enableDohDiagnostics: Boolean = true,
    val autoReconnect: Boolean = true,
    val autoConnectOnUnsecuredWifi: Boolean = false,
    val autoConnectOnMobileData: Boolean = false,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val splitTunnelPackages: Set<String> = emptySet(),
    val baselinePingMs: Double? = null,
    val baselineJitterMs: Double? = null,
    val baselineDownloadMbps: Double? = null,
    val baselineUploadMbps: Double? = null,
    val baselinePacketLossPct: Double? = null,
    val baselineCapturedAtMs: Long = 0L
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val SHOW_CYBER_SILHOUETTE = booleanPreferencesKey("show_cyber_silhouette")
        val AUTO_SERVER_MODE = booleanPreferencesKey("auto_server_mode")
        val SELECTED_TUNNEL_ID = longPreferencesKey("selected_tunnel_id")
        val DNS_PRESET = stringPreferencesKey("dns_preset")
        val CUSTOM_DNS_SERVERS = stringPreferencesKey("custom_dns_servers")
        val ENABLE_DOH_DIAGNOSTICS = booleanPreferencesKey("enable_doh_diagnostics")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTO_CONNECT_WIFI = booleanPreferencesKey("auto_connect_wifi")
        val AUTO_CONNECT_CELLULAR = booleanPreferencesKey("auto_connect_cellular")
        val SPLIT_TUNNEL_MODE = stringPreferencesKey("split_tunnel_mode")
        val SPLIT_TUNNEL_PACKAGES = stringSetPreferencesKey("split_tunnel_packages")
        val BASELINE_PING_MS = doublePreferencesKey("baseline_ping_ms")
        val BASELINE_JITTER_MS = doublePreferencesKey("baseline_jitter_ms")
        val BASELINE_DOWN_MBPS = doublePreferencesKey("baseline_down_mbps")
        val BASELINE_UP_MBPS = doublePreferencesKey("baseline_up_mbps")
        val BASELINE_LOSS_PCT = doublePreferencesKey("baseline_loss_pct")
        val BASELINE_AT_MS = longPreferencesKey("baseline_at_ms")
    }

    val settingsFlow: Flow<AppSettings> = context.omarSettingsDataStore.data.map { prefs ->
        AppSettings(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            showCyberSilhouette = prefs[Keys.SHOW_CYBER_SILHOUETTE] ?: true,
            autoServerMode = prefs[Keys.AUTO_SERVER_MODE] ?: true,
            selectedTunnelId = prefs[Keys.SELECTED_TUNNEL_ID],
            dnsPreset = prefs[Keys.DNS_PRESET]?.let { runCatching { DnsPreset.valueOf(it) }.getOrNull() }
                ?: DnsPreset.TUNNEL_DEFAULT,
            customDnsServers = prefs[Keys.CUSTOM_DNS_SERVERS] ?: "",
            enableDohDiagnostics = prefs[Keys.ENABLE_DOH_DIAGNOSTICS] ?: true,
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
            autoConnectOnUnsecuredWifi = prefs[Keys.AUTO_CONNECT_WIFI] ?: false,
            autoConnectOnMobileData = prefs[Keys.AUTO_CONNECT_CELLULAR] ?: false,
            splitTunnelMode = prefs[Keys.SPLIT_TUNNEL_MODE]?.let { runCatching { SplitTunnelMode.valueOf(it) }.getOrNull() }
                ?: SplitTunnelMode.ALL_APPS,
            splitTunnelPackages = prefs[Keys.SPLIT_TUNNEL_PACKAGES] ?: emptySet(),
            baselinePingMs = prefs[Keys.BASELINE_PING_MS],
            baselineJitterMs = prefs[Keys.BASELINE_JITTER_MS],
            baselineDownloadMbps = prefs[Keys.BASELINE_DOWN_MBPS],
            baselineUploadMbps = prefs[Keys.BASELINE_UP_MBPS],
            baselinePacketLossPct = prefs[Keys.BASELINE_LOSS_PCT],
            baselineCapturedAtMs = prefs[Keys.BASELINE_AT_MS] ?: 0L
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.omarSettingsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setShowCyberSilhouette(enabled: Boolean) {
        context.omarSettingsDataStore.edit { it[Keys.SHOW_CYBER_SILHOUETTE] = enabled }
    }

    suspend fun setAutoServerMode(enabled: Boolean) {
        context.omarSettingsDataStore.edit { it[Keys.AUTO_SERVER_MODE] = enabled }
    }

    suspend fun setSelectedTunnelId(tunnelId: Long?) {
        context.omarSettingsDataStore.edit {
            if (tunnelId == null) {
                it.remove(Keys.SELECTED_TUNNEL_ID)
            } else {
                it[Keys.SELECTED_TUNNEL_ID] = tunnelId
            }
        }
    }

    suspend fun setDnsConfig(preset: DnsPreset, customServers: String) {
        context.omarSettingsDataStore.edit {
            it[Keys.DNS_PRESET] = preset.name
            it[Keys.CUSTOM_DNS_SERVERS] = customServers
        }
    }

    suspend fun setEnableDohDiagnostics(enabled: Boolean) {
        context.omarSettingsDataStore.edit { it[Keys.ENABLE_DOH_DIAGNOSTICS] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.omarSettingsDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setAutoConnectRules(onWifi: Boolean, onCellular: Boolean) {
        context.omarSettingsDataStore.edit {
            it[Keys.AUTO_CONNECT_WIFI] = onWifi
            it[Keys.AUTO_CONNECT_CELLULAR] = onCellular
        }
    }

    suspend fun setSplitTunneling(mode: SplitTunnelMode, packages: Set<String>) {
        context.omarSettingsDataStore.edit {
            it[Keys.SPLIT_TUNNEL_MODE] = mode.name
            it[Keys.SPLIT_TUNNEL_PACKAGES] = packages
        }
    }

    suspend fun saveBaselineMetrics(
        pingMs: Double?,
        jitterMs: Double?,
        downloadMbps: Double?,
        uploadMbps: Double?,
        packetLossPct: Double?
    ) {
        context.omarSettingsDataStore.edit {
            if (pingMs != null) it[Keys.BASELINE_PING_MS] = pingMs else it.remove(Keys.BASELINE_PING_MS)
            if (jitterMs != null) it[Keys.BASELINE_JITTER_MS] = jitterMs else it.remove(Keys.BASELINE_JITTER_MS)
            if (downloadMbps != null) it[Keys.BASELINE_DOWN_MBPS] = downloadMbps else it.remove(Keys.BASELINE_DOWN_MBPS)
            if (uploadMbps != null) it[Keys.BASELINE_UP_MBPS] = uploadMbps else it.remove(Keys.BASELINE_UP_MBPS)
            if (packetLossPct != null) it[Keys.BASELINE_LOSS_PCT] = packetLossPct else it.remove(Keys.BASELINE_LOSS_PCT)
            it[Keys.BASELINE_AT_MS] = System.currentTimeMillis()
        }
    }
}
