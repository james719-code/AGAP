package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPacketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PendingPacketEntity)

    @Query("SELECT * FROM pending_packets ORDER BY created_at ASC")
    suspend fun getAllPendingPackets(): List<PendingPacketEntity>

    @Query("SELECT * FROM pending_packets ORDER BY created_at DESC")
    fun getAllPendingPacketsFlow(): Flow<List<PendingPacketEntity>>

    @Delete
    suspend fun deletePacket(packet: PendingPacketEntity)

    @Query("DELETE FROM pending_packets WHERE packet_hash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("SELECT * FROM pending_packets WHERE packet_hash = :hash LIMIT 1")
    suspend fun getPacketByHash(hash: String): PendingPacketEntity?

    @Query("SELECT COUNT(*) FROM pending_packets")
    fun getPendingCountFlow(): Flow<Int>
}

@Dao
interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidents(incidents: List<IncidentEntity>)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidentsFlow(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE incident_id = :id")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getIncidentCount(): Int
}
