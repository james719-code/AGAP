package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPacketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PendingPacketEntity)

    @Query("SELECT * FROM pending_packets ORDER BY priority DESC, created_at ASC")
    suspend fun getAllPendingPackets(): List<PendingPacketEntity>

    @Query("SELECT * FROM pending_packets ORDER BY priority DESC, created_at DESC")
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

@Dao
interface AgencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgency(agency: AgencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgencies(agencies: List<AgencyEntity>)

    @Query("SELECT * FROM agencies ORDER BY name ASC")
    fun getAllAgenciesFlow(): Flow<List<AgencyEntity>>

    @Query("SELECT * FROM agencies ORDER BY name ASC")
    suspend fun getAllAgencies(): List<AgencyEntity>

    @Query("SELECT * FROM agencies WHERE id = :id LIMIT 1")
    suspend fun getAgencyById(id: Long): AgencyEntity?

    @Query("DELETE FROM agencies")
    suspend fun deleteAll()
}

@Dao
interface ResponderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponder(responder: ResponderUnitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponders(responders: List<ResponderUnitEntity>)

    @Query("SELECT * FROM responder_units ORDER BY unit_callsign ASC")
    fun getAllRespondersFlow(): Flow<List<ResponderUnitEntity>>

    @Query("SELECT * FROM responder_units ORDER BY unit_callsign ASC")
    suspend fun getAllResponders(): List<ResponderUnitEntity>

    @Query("SELECT * FROM responder_units WHERE agency_id = :agencyId ORDER BY unit_callsign ASC")
    fun getRespondersByAgencyFlow(agencyId: Long): Flow<List<ResponderUnitEntity>>

    @Query("SELECT * FROM responder_units WHERE agency_id = :agencyId ORDER BY unit_callsign ASC")
    suspend fun getRespondersByAgency(agencyId: Long): List<ResponderUnitEntity>

    @Query("SELECT * FROM responder_units WHERE id = :id LIMIT 1")
    suspend fun getResponderById(id: Long): ResponderUnitEntity?

    @Query("DELETE FROM responder_units")
    suspend fun deleteAll()
}

@Dao
interface PredefinedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<PredefinedMessageEntity>)

    @Query("SELECT * FROM predefined_messages ORDER BY id ASC")
    fun getAllMessagesFlow(): Flow<List<PredefinedMessageEntity>>

    @Query("SELECT * FROM predefined_messages WHERE code = :code LIMIT 1")
    suspend fun getMessageByCode(code: String): PredefinedMessageEntity?

    @Query("DELETE FROM predefined_messages")
    suspend fun deleteAll()
}

