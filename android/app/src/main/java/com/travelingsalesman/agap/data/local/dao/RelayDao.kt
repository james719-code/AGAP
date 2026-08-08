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

    @Query("SELECT * FROM seen_mesh_packets WHERE transportToken = :token AND packetType = :packetType LIMIT 1")
    suspend fun getSeenPacket(token: String, packetType: String): SeenMeshPacketEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM seen_mesh_packets WHERE transportToken = :token AND isTerminal = 1)")
    suspend fun isTokenTerminal(token: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueRelay(packet: RelayQueueEntity): Long

    @Query("SELECT * FROM relay_queue WHERE transportToken = :token AND packetType = :packetType AND relayState = 'QUEUED' AND expiryMs > :now LIMIT 1")
    suspend fun getQueuedRelay(
        token: String,
        packetType: String,
        now: Long = System.currentTimeMillis()
    ): RelayQueueEntity?

    @Query("SELECT * FROM relay_queue WHERE relayState = 'QUEUED' AND expiryMs > :now ORDER BY priority DESC, lastAdvertiseMs ASC, createdAtMs ASC")
    suspend fun getNextRelayPackets(now: Long = System.currentTimeMillis()): List<RelayQueueEntity>

    @Query("SELECT COUNT(*) FROM relay_queue WHERE relayState = 'QUEUED'")
    fun observeQueuedRelayCount(): Flow<Int>

    /**
     * User-facing pending count. A logical message is pending only until this
     * device either uploads it to the server or successfully hands it to another
     * AGAP peer. The UNION de-duplicates packets that exist in both the gateway
     * outbox and the relay queue.
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT packet_hash AS token
            FROM pending_packets
            WHERE packet_hash NOT IN (
                SELECT packet_hash FROM mesh_delivery_events
                WHERE event_type = 'UPLINKED' OR (event_type = 'RELAYED' AND device_identifier IS NOT NULL AND device_identifier != packet_hash)
            )
            UNION
            SELECT transportToken AS token
            FROM relay_queue
            WHERE relayState = 'QUEUED'
              AND expiryMs > :now
              AND transportToken NOT IN (
                  SELECT packet_hash FROM mesh_delivery_events
                  WHERE event_type = 'UPLINKED' OR (event_type = 'RELAYED' AND device_identifier IS NOT NULL AND device_identifier != packet_hash)
              )
        )
    """)
    fun observeUndeliveredMessageCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM relay_queue WHERE relayState = 'QUEUED' ORDER BY priority DESC, createdAtMs DESC LIMIT :limit")
    fun observeQueuedRelays(limit: Int): Flow<List<RelayQueueEntity>>

    /**
     * Cleans rows produced by older builds that inserted the same logical packet
     * more than once. Keep the newest row for each token + logical packet type.
     */
    @Query("""
        DELETE FROM relay_queue
        WHERE relayState = 'QUEUED'
          AND queueId NOT IN (
              SELECT MAX(queueId)
              FROM relay_queue
              WHERE relayState = 'QUEUED'
              GROUP BY transportToken, packetType
          )
    """)
    suspend fun deleteDuplicateQueuedRelays(): Int

    @Query("UPDATE relay_queue SET nextAdvertiseMs = :now WHERE queueId = :queueId AND relayState = 'QUEUED'")
    suspend fun makeRelayEligibleNow(
        queueId: Long,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE relay_queue SET lastAdvertiseMs = :now, advertiseCount = advertiseCount + 1, nextAdvertiseMs = :nextAdvertiseMs WHERE queueId = :queueId")
    suspend fun markAdvertised(
        queueId: Long,
        now: Long,
        nextAdvertiseMs: Long
    )

    @Query("DELETE FROM relay_queue WHERE queueId = :queueId")
    suspend fun removeQueueEntry(queueId: Long)

    @Query("DELETE FROM relay_queue WHERE transportToken = :token")
    suspend fun removeTokenFromQueue(token: String)

    @Query("DELETE FROM relay_queue WHERE expiryMs <= :now")
    suspend fun deleteExpiredRelayRows(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM seen_mesh_packets WHERE (isTerminal = 0 AND expiryMs <= :now) OR (isTerminal = 1 AND tombstoneExpiryMs IS NOT NULL AND tombstoneExpiryMs <= :now)")
    suspend fun deleteExpiredSeenRows(now: Long = System.currentTimeMillis())

    @Query("UPDATE seen_mesh_packets SET isTerminal = 1, terminalStatus = :status, terminalTimeMs = :timeMs, tombstoneExpiryMs = :tombstoneExpiryMs WHERE transportToken = :token")
    suspend fun markTokenTerminal(
        token: String,
        status: String,
        timeMs: Long = System.currentTimeMillis(),
        tombstoneExpiryMs: Long = System.currentTimeMillis() + 10_800_000L
    )
}
