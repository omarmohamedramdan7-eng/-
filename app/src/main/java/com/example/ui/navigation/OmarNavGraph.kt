package com.example.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.datastore.AppLanguage
import com.example.ui.components.CyberBackground
import com.example.ui.i18n.LocalStrings
import com.example.ui.screens.EgyptNetworksScreen
import com.example.ui.screens.HomeBoostScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplitTunnelingScreen
import com.example.ui.screens.TunnelsServersScreen
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.OmarVpnViewModel
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object ServersRoute
@Serializable object EgyptNetworksRoute
@Serializable object HomeBoostRoute
@Serializable object SettingsRoute
@Serializable object SplitTunnelingRoute

private data class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun OmarNavRoot(
    viewModel: OmarVpnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tunnels by viewModel.tunnels.collectAsStateWithLifecycle()
    val tunnelsLoaded by viewModel.tunnelsLoaded.collectAsStateWithLifecycle()
    val networkSnapshot by viewModel.networkSnapshot.collectAsStateWithLifecycle()
    val carrierStates by viewModel.carrierStates.collectAsStateWithLifecycle()
    val autoServerState by viewModel.autoServerState.collectAsStateWithLifecycle()
    val boostState by viewModel.boostState.collectAsStateWithLifecycle()
    val diagnosticHistory by viewModel.diagnosticHistory.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val probingIds by viewModel.probingTunnelIds.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Polite notification permission request on Android 13+ when starting VPN
    val requestNotificationIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiMessage) {
        val code = uiMessage ?: return@LaunchedEffect
        val needsServerAction = code == "NO_CONFIGURED_TUNNEL" || code == "NO_CONFIGURED_SERVERS"
        val text = when (code) {
            "NO_CONFIGURED_TUNNEL", "NO_CONFIGURED_SERVERS" -> strings.noServersConfiguredTitle
            "INCOMPLETE_TUNNEL_CONFIG" -> "Tunnel configuration is incomplete."
            "VPN_PERMISSION_DENIED" -> "Android VPN permission is required."
            "NO_REACHABLE_SERVER", "ALL_SERVERS_UNREACHABLE" -> "No configured WireGuard servers responded."
            "TUNNEL_SAVED" -> if (strings.isArabic) "تم حفظ النفق بنجاح" else "WireGuard tunnel saved."
            "CARRIER_SAVED" -> if (strings.isArabic) "تم حفظ إعدادات الشبكة" else "Carrier profile saved."
            else -> code
        }
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = if (needsServerAction) strings.addFirstServer else null
        )
        viewModel.consumeUiMessage()
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && needsServerAction) {
            navController.navigate(ServersRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val topTabs = listOf(
        TopLevelTab(HomeRoute, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        TopLevelTab(ServersRoute, strings.navServers, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_servers"),
        TopLevelTab(EgyptNetworksRoute, strings.navEgypt, Icons.Filled.CellTower, Icons.Outlined.CellTower, "nav_egypt"),
        TopLevelTab(HomeBoostRoute, strings.navBoost, Icons.Filled.Speed, Icons.Outlined.Speed, "nav_boost")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val showTopChrome = currentDest?.hasRoute(SettingsRoute::class) != true &&
        currentDest?.hasRoute(SplitTunnelingRoute::class) != true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CyberBackground(
        showSilhouette = settings.showCyberSilhouette,
        vpnState = vpnState,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showTopChrome && !useNavRail) {
                        NavigationBar(
                            containerColor = ObsidianSurface.copy(alpha = 0.96f),
                            contentColor = NeonEmerald
                        ) {
                            topTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab.route) },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianBlack,
                                        selectedTextColor = NeonEmerald,
                                        indicatorColor = NeonEmerald,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopChrome && useNavRail) {
                        NavigationRail(
                            containerColor = ObsidianSurface.copy(alpha = 0.96f),
                            contentColor = NeonEmerald
                        ) {
                            topTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab.route) },
                                    modifier = Modifier.testTag(tab.tag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ObsidianBlack,
                                        selectedTextColor = NeonEmerald,
                                        indicatorColor = NeonEmerald,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                vpnState = vpnState,
                                settings = settings,
                                tunnels = tunnels,
                                networkSnapshot = networkSnapshot,
                                isAutoProbing = autoServerState.isRunning,
                                onConnectClick = {
                                    requestNotificationIfNeeded()
                                    viewModel.onPrimaryConnectClick { intent ->
                                        vpnPermissionLauncher.launch(intent)
                                    }
                                },
                                onOpenServers = { navigateTopLevel(ServersRoute) },
                                onOpenEgyptNetworks = { navigateTopLevel(EgyptNetworksRoute) },
                                onOpenHomeBoost = { navigateTopLevel(HomeBoostRoute) },
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onToggleLanguage = {
                                    val next = if (strings.isArabic) AppLanguage.ENGLISH else AppLanguage.ARABIC
                                    viewModel.setLanguage(next)
                                },
                                onDismissError = { viewModel.clearVpnError() }
                            )
                        }

                        composable<ServersRoute> {
                            TunnelsServersScreen(
                                tunnels = tunnels,
                                tunnelsLoaded = tunnelsLoaded,
                                settings = settings,
                                vpnState = vpnState,
                                autoServerState = autoServerState,
                                probingTunnelIds = probingIds,
                                onSelectAutoMode = { viewModel.setAutoServerMode(it) },
                                onSelectTunnel = { viewModel.setSelectedTunnel(it) },
                                onRunAutoServer = {
                                    requestNotificationIfNeeded()
                                    viewModel.runAutoServerAndConnect(
                                        onNeedVpnPermission = { vpnPermissionLauncher.launch(it) },
                                        connectBestAfterProbe = true
                                    )
                                },
                                onConnectTunnel = { profile ->
                                    requestNotificationIfNeeded()
                                    viewModel.connectToTunnel(profile) { intent ->
                                        vpnPermissionLauncher.launch(intent)
                                    }
                                },
                                onDisconnect = {
                                    viewModel.onPrimaryConnectClick { }
                                },
                                onProbeTunnel = { viewModel.probeSingleTunnel(it) },
                                onSaveTunnel = { viewModel.saveTunnelProfile(it) },
                                onDeleteTunnel = { viewModel.deleteTunnel(it) },
                                onImportRawConfig = { raw, name, srv, cc, cn ->
                                    viewModel.importWireGuardConfigText(raw, name, srv, cc, cn)
                                }
                            )
                        }

                        composable<EgyptNetworksRoute> {
                            EgyptNetworksScreen(
                                carrierStates = carrierStates,
                                tunnels = tunnels,
                                networkSnapshot = networkSnapshot,
                                onRunCarrierDiagnostics = { viewModel.runCarrierDiagnostics(it) },
                                onSaveCarrierProfile = { c, tid, dns, mtu ->
                                    viewModel.saveCarrierProfile(c, tid, dns, mtu)
                                },
                                onConnectAuthorizedTunnel = { tunnel, dns, mtu ->
                                    requestNotificationIfNeeded()
                                    viewModel.connectToTunnel(
                                        tunnel = tunnel,
                                        overrideDns = dns,
                                        overrideMtu = mtu
                                    ) { intent ->
                                        vpnPermissionLauncher.launch(intent)
                                    }
                                }
                            )
                        }

                        composable<HomeBoostRoute> {
                            HomeBoostScreen(
                                boostState = boostState,
                                history = diagnosticHistory,
                                onOptimizeClick = { viewModel.runHomeInternetOptimize() },
                                onConnectRecommended = { tunnel ->
                                    requestNotificationIfNeeded()
                                    viewModel.connectToTunnel(tunnel) { intent ->
                                        vpnPermissionLauncher.launch(intent)
                                    }
                                }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                onSetLanguage = { viewModel.setLanguage(it) },
                                onSetCyberSilhouette = { viewModel.setShowCyberSilhouette(it) },
                                onSetDnsConfig = { preset, custom -> viewModel.setDnsConfig(preset, custom) },
                                onSetDohDiagnostics = { viewModel.setEnableDohDiagnostics(it) },
                                onSetAutoReconnect = { viewModel.setAutoReconnect(it) },
                                onSetAutoConnectRules = { w, c -> viewModel.setAutoConnectRules(w, c) },
                                onOpenSplitTunneling = { navController.navigate(SplitTunnelingRoute) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<SplitTunnelingRoute> {
                            SplitTunnelingScreen(
                                settings = settings,
                                installedApps = installedApps,
                                isLoadingApps = appsLoading,
                                onLoadApps = { viewModel.loadInstalledApps() },
                                onUpdateSplitTunneling = { mode, pkgs ->
                                    viewModel.updateSplitTunneling(mode, pkgs)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
