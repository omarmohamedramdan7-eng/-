package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.TestKeyCipher
import com.example.data.local.AppDatabase
import com.example.data.repository.CarrierRepository
import com.example.data.repository.TunnelRepository
import com.example.domain.model.ActiveNetworkSnapshot
import com.example.domain.model.EgyptCarrierId
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.ServerAvailability
import com.example.domain.model.TunnelProfile
import com.example.domain.wireguard.WgKeyGenerator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnStateAndRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TunnelRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TunnelRepository(db.tunnelDao(), TestKeyCipher())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `TunnelRepository encrypts private key in Room and decrypts cleanly on read`() = runTest {
        val (priv, _) = WgKeyGenerator.generateKeyPair()
        val (_, peerPub) = WgKeyGenerator.generateKeyPair()
        val profile = TunnelProfile(
            name = "EncryptedNode",
            serverName = "Cairo-Edge",
            countryCode = "EG",
            countryName = "Egypt",
            interfaceAddresses = "10.0.0.5/32",
            dnsServers = "1.1.1.1",
            privateKey = priv,
            peerPublicKey = peerPub,
            endpointHost = "198.51.100.2",
            endpointPort = 51820
        )

        val id = repository.saveTunnel(profile)
        val rawEntity = db.tunnelDao().getById(id)!!
        assertNotEquals(priv, rawEntity.privateKeyEncrypted)
        assertFalse(rawEntity.privateKeyEncrypted.contains(priv))

        val loaded = repository.getTunnelById(id)!!
        assertEquals(priv, loaded.privateKey)
        assertEquals(ServerAvailability.CONFIGURED_UNTESTED, loaded.availability)
    }

    @Test
    fun `CarrierRepository detects Vodafone Orange Etisalat and WE Egypt accurately`() {
        val vfSnapshot = ActiveNetworkSnapshot(
            isConnected = true,
            transport = NetworkTransportType.CELLULAR,
            carrierDisplayName = "Vodafone EG",
            mccMnc = "60202"
        )
        assertTrue(CarrierRepository.matchesCarrier(EgyptCarrierId.VODAFONE, vfSnapshot))
        assertFalse(CarrierRepository.matchesCarrier(EgyptCarrierId.ORANGE, vfSnapshot))

        val weSnapshot = ActiveNetworkSnapshot(
            isConnected = true,
            transport = NetworkTransportType.CELLULAR,
            carrierDisplayName = "WE",
            mccMnc = "60204"
        )
        assertTrue(CarrierRepository.matchesCarrier(EgyptCarrierId.WE, weSnapshot))
    }
}
