package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeenPacket(seen: SeenMeshPacketEntity)

    @Query("SELECT * FROM seen_mesh_packets WHERE transportToken = :token LIMIT 1")
    suspend fun getSeenPacket(token: String): SeenMeshPacketEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM seen_mesh_packets WHERE transportToken = :token AND isTerminal = 1)")
    suspend fun isTokenTerminal(token: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueRelay(packet: RelayQueueEntity): Long

    @Query("SELECT * FROM relay_queue WHERE relayState = 'QUEUED' AND expiryMs > :now ORDER BY priority DESC, createdAtMs ASC")
    suspend fun getNextRelayPackets(now: Long = System.currentTimeMillis()): List<RelayQueueEntity>

    @Query("DELETE FROM relay_queue WHERE transportToken = :token")
    suspend fun removeTokenFromQueue(token: String)

    @Query("UPDATE seen_mesh_packets SET isTerminal = 1, terminalStatus = :status, terminalTimeMs = :timeMs, tombstoneExpiryMs = :tombstoneExpiryMs WHERE transportToken = :token")
    suspend fun markTokenTerminal(token: String, status: String, timeMs: Long = System.currentTimeMillis(), tombstoneExpiryMs: Long = System.currentTimeMillis() + 10800_000L)
}
