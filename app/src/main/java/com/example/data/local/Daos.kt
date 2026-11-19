package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelDao {
    @Query("SELECT * FROM tunnels ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<TunnelEntity>>

    @Query("SELECT * FROM tunnels ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<TunnelEntity>

    @Query("SELECT * FROM tunnels WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TunnelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TunnelEntity): Long

    @Update
    suspend fun update(entity: TunnelEntity)

    @Query("DELETE FROM tunnels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE tunnels SET lastLatencyMs = :latencyMs, lastJitterMs = :jitterMs, " +
            "lastPacketLossPct = :packetLossPct, lastReachable = :reachable, " +
            "lastTestedAtMs = :testedAtMs WHERE id = :id"
    )
    suspend fun updateProbeMetrics(
        id: Long,
        latencyMs: Double?,
        jitterMs: Double?,
        packetLossPct: Double?,
        reachable: Boolean,
        testedAtMs: Long
    )
}

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostic_history ORDER BY timestampMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<DiagnosticHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosticHistoryEntity): Long

    @Query("DELETE FROM diagnostic_history")
    suspend fun clearAll()
}

@Dao
interface CarrierConfigDao {
    @Query("SELECT * FROM carrier_configs")
    fun observeAll(): Flow<List<CarrierConfigEntity>>

    @Query("SELECT * FROM carrier_configs WHERE carrierId = :carrierId LIMIT 1")
    suspend fun getByCarrierId(carrierId: String): CarrierConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CarrierConfigEntity)
}
