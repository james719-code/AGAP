package com.travelingsalesman.agap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_packets")
data class PendingPacketEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_hash")
    val packetHash: String,

    @ColumnInfo(name = "incident_uuid")
    val incidentUuid: String = packetHash,

    @ColumnInfo(name = "packet_uuid")
    val packetUuid: String = packetHash,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    @ColumnInfo(name = "emergency_type")
    val emergencyType: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "hop_count")
    val hopCount: Int,

    @ColumnInfo(name = "encrypted_payload")
    val encryptedPayload: String,

    @ColumnInfo(name = "priority")
    val priority: Int = 10,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mesh_delivery_events")
data class MeshDeliveryEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "packet_hash")
    val packetHash: String,

    @ColumnInfo(name = "packet_type")
    val packetType: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "device_identifier")
    val deviceIdentifier: String? = null,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "hop_count")
    val hopCount: Int? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null
)

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey
    @ColumnInfo(name = "incident_id")
    val incidentId: String,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    @ColumnInfo(name = "emergency_type")
    val emergencyType: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "dispatcher_notes")
    val dispatcherNotes: String? = null,

    @ColumnInfo(name = "dispatched_unit_id")
    val dispatchedUnitId: Long? = null,

    @ColumnInfo(name = "dispatched_at")
    val dispatchedAt: String? = null,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: String? = null,

    @ColumnInfo(name = "packet_hash")
    val packetHash: String = "",

    @ColumnInfo(name = "channel")
    val channel: String = "ONLINE",

    @ColumnInfo(name = "delivery_path")
    val deliveryPath: String = "DIRECT_UPLINK",

    @ColumnInfo(name = "sender_device_id")
    val senderDeviceId: String = "",

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false
)

@Entity(tableName = "agencies")
data class AgencyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "region_code")
    val regionCode: String = "",

    @ColumnInfo(name = "province_code")
    val provinceCode: String = "",

    @ColumnInfo(name = "muni_code")
    val muniCode: String = "",

    @ColumnInfo(name = "barangay_code")
    val barangayCode: String = "",

    @ColumnInfo(name = "contact_number")
    val contactNumber: String = "",

    @ColumnInfo(name = "latitude")
    val latitude: Double = 0.0,

    @ColumnInfo(name = "longitude")
    val longitude: Double = 0.0,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rescue_units")
data class ResponderUnitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "agency_id")
    val agencyId: Long,

    @ColumnInfo(name = "target_id")
    val targetId: String = "",

    @ColumnInfo(name = "unit_callsign")
    val unitCallsign: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "status")
    val status: String = "IDLE",

    @ColumnInfo(name = "current_lat")
    val currentLat: Double = 0.0,

    @ColumnInfo(name = "current_lon")
    val currentLon: Double = 0.0,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: String? = null,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "predefined_messages")
data class PredefinedMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "message_text")
    val messageText: String,

    @ColumnInfo(name = "message_fil")
    val messageFil: String? = null,

    @ColumnInfo(name = "message_bikol")
    val messageBikol: String? = null,

    @ColumnInfo(name = "message_type")
    val messageType: String = "ACK",

    @ColumnInfo(name = "emergency_type_id")
    val emergencyTypeId: Long? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: String? = null,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "emergency_types")
data class EmergencyTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "default_priority")
    val defaultPriority: Int = 3,

    @ColumnInfo(name = "target_agency_type")
    val targetAgencyType: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: String? = null,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)


