package com.travelingsalesman.agap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_packets")
data class PendingPacketEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_hash")
    val packetHash: String,

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

@Entity(tableName = "responder_units")
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

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)


