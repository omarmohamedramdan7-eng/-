package com.example

import com.example.core.util.SafeLogger
import com.example.domain.model.TunnelProfile
import com.example.domain.wireguard.ConfigParseResult
import com.example.domain.wireguard.WgConfigParser
import com.example.domain.wireguard.WgKeyGenerator
import com.example.domain.wireguard.WgKeyValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WgConfigParserTest {

    @Test
    fun `parse valid wireguard conf succeeds and derives public key`() {
        val (priv, expectedPub) = WgKeyGenerator.generateKeyPair()
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()

        val raw = """
            # Client config
            [Interface]
            PrivateKey = $priv
            Address = 10.8.0.2/32, fd00::2/128
            DNS = 1.1.1.1, 9.9.9.9
            MTU = 1380

            [Peer]
            PublicKey = $peerPub
            Endpoint = vpn.omar.example:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val result = WgConfigParser.parse(raw)
        assertTrue(result is ConfigParseResult.Success)
        val cfg = (result as ConfigParseResult.Success).config
        assertEquals(expectedPub, cfg.derivedPublicKey)
        assertEquals("vpn.omar.example", cfg.endpointHost)
        assertEquals(51820, cfg.endpointPort)
        assertEquals(1380, cfg.mtu)
        assertEquals("1.1.1.1, 9.9.9.9", cfg.dnsServers)
    }

    @Test
    fun `parse rejects invalid private key and invalid endpoint`() {
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()
        val badKeyConf = """
            [Interface]
            PrivateKey = not_a_valid_key
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = $peerPub
            Endpoint = 203.0.113.10:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        assertTrue(WgConfigParser.parse(badKeyConf) is ConfigParseResult.Error)
        assertFalse(WgKeyValidator.isValidWireGuardKey("short"))

        val (priv, _) = WgKeyGenerator.generateKeyPair()
        val badEndpointConf = """
            [Interface]
            PrivateKey = $priv
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = $peerPub
            Endpoint = invalid_endpoint_without_port
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        assertTrue(WgConfigParser.parse(badEndpointConf) is ConfigParseResult.Error)
    }

    @Test
    fun `safe preview and SafeLogger redact private keys`() {
        val (priv, _) = WgKeyGenerator.generateKeyPair()
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()
        val profile = TunnelProfile(
            id = 1L,
            name = "TestNode",
            serverName = "Frankfurt-01",
            countryCode = "DE",
            countryName = "Germany",
            interfaceAddresses = "10.0.0.2/32",
            dnsServers = "1.1.1.1",
            privateKey = priv,
            peerPublicKey = peerPub,
            presharedKey = priv,
            endpointHost = "de.example.net",
            endpointPort = 51820
        )

        val preview = WgConfigParser.toSafePreviewString(profile, includePrivateKey = false)
        assertFalse(preview.contains(priv))
        assertTrue(preview.contains("[HIDDEN_IN_APP]"))

        val logLine = SafeLogger.sanitize("PrivateKey = $priv Endpoint=1.2.3.4:51820")
        assertFalse(logLine.contains(priv))
        assertTrue(logLine.contains("[REDACTED]"))
    }
}
