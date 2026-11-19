package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.datastore.AppSettings
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.VpnConnectionState
import com.example.domain.model.VpnErrorCode
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.OmarVpnTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OmarVpnUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `HomeScreen shows DISCONNECTED when idle and scrolls to quick cards`() {
        var clicked = false
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                OmarVpnTheme(isArabic = false) {
                    HomeScreen(
                        vpnState = VpnConnectionState.Disconnected,
                        settings = AppSettings(),
                        tunnels = emptyList(),
                        networkSnapshot = ActiveNetworkSnapshot(
                            isConnected = true,
                            transport = NetworkTransportType.WIFI
                        ),
                        isAutoProbing = false,
                        onConnectClick = { clicked = true },
                        onOpenServers = {},
                        onOpenEgyptNetworks = {},
                        onOpenHomeBoost = {},
                        onOpenSettings = {},
                        onToggleLanguage = {},
                        onDismissError = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("OMAR VPN").assertIsDisplayed()
        composeRule.onNodeWithText("DISCONNECTED").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_button").performClick()
        assertTrue(clicked)
        composeRule.onNodeWithTag("home_egypt_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_boost_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `HomeScreen displays CONNECTED and real metrics only when Connected state is active`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                OmarVpnTheme(isArabic = false) {
                    HomeScreen(
                        vpnState = VpnConnectionState.Connected(
                            tunnelId = 1L,
                            tunnelName = "Berlin-WG",
                            serverName = "Frankfurt-01",
                            countryCode = "DE",
                            countryName = "Germany",
                            endpoint = "203.0.113.10:51820",
                            connectedAtElapsedMs = 0L,
                            rxBytes = 2_097_152L,
                            txBytes = 524_288L,
                            livePingMs = 38.0,
                            latestHandshakeEpochMs = 1_700_000_000_000L,
                            vpnInterfaceVerified = true
                        ),
                        settings = AppSettings(autoServerMode = false),
                        tunnels = emptyList(),
                        networkSnapshot = ActiveNetworkSnapshot(
                            isConnected = true,
                            isVpnActive = true,
                            transport = NetworkTransportType.WIFI
                        ),
                        isAutoProbing = false,
                        onConnectClick = {},
                        onOpenServers = {},
                        onOpenEgyptNetworks = {},
                        onOpenHomeBoost = {},
                        onOpenSettings = {},
                        onToggleLanguage = {},
                        onDismissError = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("CONNECTED").assertIsDisplayed()
        composeRule.onNodeWithText("DE • Frankfurt-01").assertIsDisplayed()
        composeRule.onNodeWithText("2.00 MB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("512.0 KB").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `HomeScreen shows error state in Arabic when VPN permission fails`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides ArabicStrings) {
                OmarVpnTheme(isArabic = true) {
                    HomeScreen(
                        vpnState = VpnConnectionState.Error(
                            errorCode = VpnErrorCode.PERMISSION_DENIED,
                            detail = "VPN consent denied"
                        ),
                        settings = AppSettings(),
                        tunnels = emptyList(),
                        networkSnapshot = ActiveNetworkSnapshot(),
                        isAutoProbing = false,
                        onConnectClick = {},
                        onOpenServers = {},
                        onOpenEgyptNetworks = {},
                        onOpenHomeBoost = {},
                        onOpenSettings = {},
                        onToggleLanguage = {},
                        onDismissError = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("خطأ في الاتصال").assertIsDisplayed()
        composeRule.onNodeWithText("VPN consent denied").assertIsDisplayed()
    }
}
