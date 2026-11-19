package com.example

import android.net.VpnService
import com.example.core.datastore.AppSettings
import com.example.core.datastore.DnsPreset
import com.example.domain.model.TunnelProfile
import com.example.domain.wireguard.WgKeyGenerator
import com.example.vpn.BaseWireGuardVpnService
import com.example.vpn.OmarVpnService
import com.example.vpn.VpnCidrParser
import com.example.vpn.WireGuardConfigMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BaseVpnServiceTest {

    @Test
    fun `OmarVpnService inherits from BaseWireGuardVpnService and android net VpnService`() {
        val controller = Robolectric.buildService(OmarVpnService::class.java).create()
        val service = controller.get()
        assertTrue(service is BaseWireGuardVpnService)
        assertTrue(service is VpnService)
        controller.destroy()
    }

    @Test
    fun `VpnCidrParser parses IPv4 and IPv6 CIDRs accurately and rejects invalid inputs`() {
        val ipv4 = VpnCidrParser.parseCidr("10.8.0.2/24")
        assertNotNull(ipv4)
        assertEquals("10.8.0.2", ipv4!!.first.hostAddress)
        assertEquals(24, ipv4.second)

        val ipv6 = VpnCidrParser.parseCidr("::/0")
        assertNotNull(ipv6)
        assertEquals(0, ipv6!!.second)

        assertNull(VpnCidrParser.parseCidr("10.0.0.1/99"))
        assertNull(VpnCidrParser.parseCidr("not_an_ip/24"))
    }

    @Test
    fun `WireGuardConfigMapper builds valid Config with DNS override and MTU`() {
        val (priv, _) = WgKeyGenerator.generateKeyPair()
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()
        val profile = TunnelProfile(
            id = 5L,
            name = "BaseVpnTest",
            serverName = "Alexandria-WG",
            countryCode = "EG",
            countryName = "Egypt",
            interfaceAddresses = "10.0.0.4/32",
            dnsServers = "1.1.1.1",
            mtu = 1420,
            privateKey = priv,
            peerPublicKey = peerPub,
            endpointHost = "203.0.113.55",
            endpointPort = 51820
        )

        val config = WireGuardConfigMapper.toWireGuardConfig(
            profile = profile,
            settings = AppSettings(dnsPreset = DnsPreset.QUAD9),
            selfPackageName = "com.omarvpn",
            overrideMtu = 1380
        )
        assertEquals(1380, config.getInterface().mtu.get())
        assertTrue(config.getInterface().dnsServers.any { it.hostAddress == "9.9.9.9" })
        assertEquals(1, config.peers.size)
    }
}
