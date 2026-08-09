package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.local.entity.EmergencyTypeEntity
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
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

    @Query("SELECT * FROM pending_packets ORDER BY priority DESC, created_at ASC LIMIT 1")
    suspend fun getNextPendingPacket(): PendingPacketEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM pending_packets LIMIT 1)")
    suspend fun hasPendingPackets(): Boolean

    @Query("SELECT * FROM pending_packets ORDER BY priority DESC, created_at DESC")
    fun getAllPendingPacketsFlow(): Flow<List<PendingPacketEntity>>

    /**
     * Bounded observable list for mobile UI.
     * The full table remains available through getAllPendingPackets().
     */
    @Query("""
        SELECT * FROM pending_packets
        ORDER BY priority DESC, created_at DESC
        LIMIT :limit
    """)
    fun getRecentPendingPacketsFlow(
        limit: Int
    ): Flow<List<PendingPacketEntity>>

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
interface MeshDeliveryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MeshDeliveryEventEntity)

    @Query("SELECT * FROM mesh_delivery_events ORDER BY recorded_at DESC")
    fun getAllEventsFlow(): Flow<List<MeshDeliveryEventEntity>>

    @Query("""
        SELECT packet_hash
        FROM mesh_delivery_events
        WHERE event_type = 'RELAYED'
          AND device_identifier IS NOT NULL
          AND device_identifier != packet_hash
        GROUP BY packet_hash
        ORDER BY MAX(recorded_at) DESC
        LIMIT :limit
    """)
    fun observeRecentSuccessfullyRelayedPacketHashes(limit: Int): Flow<List<String>>

    /**
     * Counts only confirmed outbound GATT deliveries.
     *
     * Older builds incorrectly wrote RELAYED as soon as a packet was queued and
     * stored the packet hash in device_identifier. A real v3 GATT relay stores
     * the destination peer id instead, so the inequality deliberately excludes
     * those historical false-positive rows.
     */
    @Query("""
        SELECT COUNT(*)
        FROM mesh_delivery_events
        WHERE event_type = 'RELAYED'
          AND device_identifier IS NOT NULL
          AND device_identifier != packet_hash
    """)
    fun observeSuccessfulRelayCount(): Flow<Int>

    /** Latest stable peer that handed this logical packet to this node. */
    @Query("""
        SELECT device_identifier
        FROM mesh_delivery_events
        WHERE packet_hash = :packetHash
          AND event_type = 'RECEIVED'
          AND device_identifier IS NOT NULL
          AND device_identifier != packet_hash
        ORDER BY recorded_at DESC
        LIMIT 1
    """)
    suspend fun getLatestReceivedPeer(packetHash: String): String?

    @Query("SELECT * FROM mesh_delivery_events WHERE synced_at IS NULL ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnsyncedEvents(limit: Int = 200): List<MeshDeliveryEventEntity>

    @Query("UPDATE mesh_delivery_events SET synced_at = :syncedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM mesh_delivery_events WHERE event_type = 'DUPLICATE_DROPPED' AND recorded_at < :beforeMs")
    suspend fun deleteOldDuplicateEvents(beforeMs: Long): Int
}

@Dao
interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidents(incidents: List<IncidentEntity>)

    suspend fun addIncident(incident: IncidentEntity) = insertIncident(incident)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidentsFlow(): Flow<List<IncidentEntity>>

    /**
     * Bounded mobile working set.
     * Keeps long-running installations from pushing an unbounded table into Compose.
     */
    @Query("""
        SELECT * FROM incidents
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getRecentIncidentsFlow(
        limit: Int
    ): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE status NOT IN ('RESOLVED', 'CANCELLED') ORDER BY timestamp DESC")
    fun getActiveIncidentsFlow(): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents
        WHERE status NOT IN ('RESOLVED', 'CANCELLED')
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getRecentActiveIncidentsFlow(
        limit: Int
    ): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE incident_id IN (:ids)")
    suspend fun getIncidentsByIds(
        ids: List<String>
    ): List<IncidentEntity>

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getIncidentHistoryFlow(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE incident_id = :id")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE packet_hash = :hash LIMIT 1")
    suspend fun getIncidentByPacketHash(hash: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE packet_hash = :hash AND incident_id NOT GLOB '*[^0-9]*' ORDER BY last_synced_at DESC LIMIT 1")
    suspend fun getServerIncidentByPacketHash(hash: String): IncidentEntity?

    @Query("UPDATE incidents SET status = :status WHERE incident_id = :id")
    suspend fun updateIncidentStatus(id: String, status: String)

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

    @Query("SELECT * FROM rescue_units ORDER BY unit_callsign ASC")
    fun getAllRespondersFlow(): Flow<List<ResponderUnitEntity>>

    @Query("SELECT * FROM rescue_units ORDER BY unit_callsign ASC")
    suspend fun getAllResponders(): List<ResponderUnitEntity>

    @Query("SELECT * FROM rescue_units WHERE agency_id = :agencyId ORDER BY unit_callsign ASC")
    fun getRespondersByAgencyFlow(agencyId: Long): Flow<List<ResponderUnitEntity>>

    @Query("SELECT * FROM rescue_units WHERE agency_id = :agencyId ORDER BY unit_callsign ASC")
    suspend fun getRespondersByAgency(agencyId: Long): List<ResponderUnitEntity>

    @Query("SELECT * FROM rescue_units WHERE id = :id LIMIT 1")
    suspend fun getResponderById(id: Long): ResponderUnitEntity?

    @Query("DELETE FROM rescue_units")
    suspend fun deleteAll()
}

@Dao
interface PredefinedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<PredefinedMessageEntity>)

    @Query("SELECT * FROM predefined_messages ORDER BY id ASC")
    fun getAllMessagesFlow(): Flow<List<PredefinedMessageEntity>>

    @Query("SELECT * FROM predefined_messages ORDER BY id ASC")
    suspend fun getAllMessages(): List<PredefinedMessageEntity>

    @Query("SELECT * FROM predefined_messages WHERE code = :code LIMIT 1")
    suspend fun getMessageByCode(code: String): PredefinedMessageEntity?

    @Query("SELECT * FROM predefined_messages WHERE protocol_code = :protocolCode AND is_active = 1 LIMIT 1")
    suspend fun getMessageByProtocolCode(protocolCode: Int): PredefinedMessageEntity?

    @Query("SELECT * FROM predefined_messages WHERE is_active = 1 AND message_type IN ('ACK', 'STATUS') AND protocol_code BETWEEN 1 AND 255 ORDER BY id ASC")
    fun getActiveReplyMessagesFlow(): Flow<List<PredefinedMessageEntity>>

    @Query("DELETE FROM predefined_messages")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceMessages(messages: List<PredefinedMessageEntity>) {
        deleteAll()
        if (messages.isNotEmpty()) insertMessages(messages)
    }
}

@Dao
interface EmergencyTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyTypes(emergencyTypes: List<EmergencyTypeEntity>)

    @Query("SELECT * FROM emergency_types WHERE is_active = 1 ORDER BY default_priority ASC, name ASC")
    fun getActiveEmergencyTypesFlow(): Flow<List<EmergencyTypeEntity>>

    @Query("SELECT * FROM emergency_types ORDER BY default_priority ASC, name ASC")
    suspend fun getAllEmergencyTypes(): List<EmergencyTypeEntity>

    @Query("DELETE FROM emergency_types")
    suspend fun deleteAll()
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: com.travelingsalesman.agap.data.local.entity.UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    fun getUserProfileFlow(userId: Long): Flow<com.travelingsalesman.agap.data.local.entity.UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalInfo(info: com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity)

    @Query("SELECT * FROM medical_info WHERE user_id = :userId")
    fun getMedicalInfoFlow(userId: Long): Flow<com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyContact(contact: com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts WHERE user_id = :userId")
    fun getEmergencyContactsFlow(userId: Long): Flow<List<com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity>>
}