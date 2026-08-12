package com.travelingsalesman.agap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_packets",
    indices = [
        Index(value = ["priority", "created_at"]),
        Index(value = ["created_at"])
    ]
)
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

    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0,

    @ColumnInfo(name = "gps_accuracy")
    val gpsAccuracy: Double = 0.0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "hop_count")
    val hopCount: Int,

    @ColumnInfo(name = "encrypted_payload")
    val encryptedPayload: String,

    /** Registered opaque origin device ID; no profile or contact data. */
    @ColumnInfo(name = "origin_device_id")
    val originDeviceId: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Int = 10,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mesh_delivery_events",
    indices = [
        Index(value = ["packet_hash", "event_type", "recorded_at"]),
        Index(value = ["event_type", "recorded_at"]),
        Index(value = ["synced_at", "recorded_at"])
    ]
)
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

@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["status", "timestamp"]),
        Index(value = ["packet_hash"]),
        Index(value = ["logical_id"])
    ]
)
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

    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0,

    @ColumnInfo(name = "gps_accuracy")
    val gpsAccuracy: Double = 0.0,

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

    /** Stable local identity shared by BLE placeholders and server rows. */
    @ColumnInfo(name = "logical_id")
    val logicalId: String = packetHash,

    @ColumnInfo(name = "server_incident_id")
    val serverIncidentId: Long? = null,

    @ColumnInfo(name = "delivery_state")
    val deliveryState: String = "RECEIVED_BY_COMMAND",

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

@Entity(
    tableName = "rescue_units",
    indices = [
        Index(value = ["agency_id", "unit_callsign"])
    ]
)
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

/**
 * Per-account snapshot of dispatch assignments used to rebuild operational chat
 * channels after an offline restart.  The scope prevents an administrator's
 * broad dispatch list from being mistaken for a responder's own assignments.
 */
@Entity(
    tableName = "cached_dispatches",
    primaryKeys = ["owner_user_id", "owner_scope", "dispatch_id"],
    indices = [
        Index(value = ["owner_user_id", "owner_scope", "incident_id"])
    ]
)
data class CachedDispatchEntity(
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: Long,

    @ColumnInfo(name = "owner_scope")
    val ownerScope: String,

    @ColumnInfo(name = "dispatch_id")
    val dispatchId: Long,

    @ColumnInfo(name = "incident_id")
    val incidentId: Long,

    @ColumnInfo(name = "unit_id")
    val unitId: Long,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "dispatched_at")
    val dispatchedAt: String? = null,

    @ColumnInfo(name = "accepted_at")
    val acceptedAt: String? = null,

    @ColumnInfo(name = "departed_at")
    val departedAt: String? = null,

    @ColumnInfo(name = "arrived_at")
    val arrivedAt: String? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: String? = null,

    @ColumnInfo(name = "unit_callsign")
    val unitCallsign: String? = null,

    @ColumnInfo(name = "agency_name")
    val agencyName: String? = null,

    @ColumnInfo(name = "responder_name")
    val responderName: String? = null,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "predefined_messages")
data class PredefinedMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "protocol_code")
    val protocolCode: Int = 0,

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

    @ColumnInfo(name = "protocol_code")
    val protocolCode: Int = 0,

    @ColumnInfo(name = "target_agency_type")
    val targetAgencyType: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: String? = null,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)
